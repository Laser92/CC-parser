import os
import json
import tempfile
import uuid
from flask import Flask, request, render_template, send_file, jsonify
from pdf_parser import (
    extract_transactions_from_pdf,
    extract_transactions_from_image,
    write_to_xlsx,
    detect_card_name,
    _auto_category,
    PDF_EXTENSIONS,
    IMAGE_EXTENSIONS,
)

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max upload


@app.route('/')
def index():
    return render_template('index.html', version="2.1.0")


@app.route('/upload', methods=['POST'])
def upload():
    # Validate file
    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded'}), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No file selected'}), 400
    
    # Get form fields
    password = request.form.get('password', '').strip() or None
    card_name = request.form.get('card_name', 'SBI').strip() or 'SBI'
    style_str = request.form.get('style', '1').strip()
    style = int(style_str) if style_str.isdigit() else 1
    ocr_engine = request.form.get('ocr_engine', 'easyocr').strip()
    
    # Parse column config
    columns = None
    columns_json = request.form.get('columns', '').strip()
    if columns_json:
        try:
            import json
            columns = json.loads(columns_json)
        except (json.JSONDecodeError, TypeError):
            columns = None
    
    # Check file extension
    ext = os.path.splitext(file.filename)[1].lower()
    if ext not in PDF_EXTENSIONS and ext not in IMAGE_EXTENSIONS:
        return jsonify({
            'error': f'Unsupported file type: {ext}. Supported: PDF, PNG, JPG, JPEG, BMP, TIFF, WEBP'
        }), 400
    
    # Save uploaded file to temp location
    temp_dir = tempfile.mkdtemp()
    input_path = os.path.join(temp_dir, f'input{ext}')
    output_path = os.path.join(temp_dir, f'{os.path.splitext(file.filename)[0]}_parsed.xlsx')
    
    try:
        file.save(input_path)
        
        # Extract transactions
        if ext in PDF_EXTENSIONS:
            transactions = extract_transactions_from_pdf(input_path, password=password)
        else:
            transactions = extract_transactions_from_image(input_path, ocr_engine=ocr_engine)
        
        if not transactions:
            debug_text = ""
            if ext in PDF_EXTENSIONS:
                try:
                    import pdfplumber
                    with pdfplumber.open(input_path, password=password) as pdf:
                        full_text = []
                        for i, page in enumerate(pdf.pages):
                            page_text = page.extract_text(x_tolerance=1, y_tolerance=3)
                            if page_text:
                                full_text.append(f"--- PAGE {i} ---")
                                full_text.extend(page_text.split("\n"))
                        
                        if full_text:
                            debug_text = "\n".join(full_text[:40])
                        else:
                            debug_text = "(PDF is empty or image-only with no text layer)"
                except Exception as ex:
                    debug_text = f"(Failed to read PDF: {ex})"
            else:
                try:
                    debug_text = "\n".join(str(text).split("\n")[:20])
                except:
                    debug_text = "(Failed to get OCR text)"
            return jsonify({
                'error': f'No transactions found. Debug text:\n{debug_text}'
            }), 400
        
        # Sort by date
        transactions.sort(key=lambda t: t['date'])
        
        # Write XLSX
        count = write_to_xlsx(transactions, output_path, card_name, style=style, columns=columns)
        
        if count == 0:
            return jsonify({
                'error': 'No valid transactions found in the file.'
            }), 400
        
        # Send file
        return send_file(
            output_path,
            as_attachment=True,
            download_name=f'{os.path.splitext(file.filename)[0]}_parsed.xlsx',
            mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        )
    
    except Exception as e:
        error_msg = str(e)
        if 'password' in error_msg.lower() or 'encrypt' in error_msg.lower():
            error_msg = 'This PDF is password-protected. Please enter the correct password.'
        return jsonify({'error': error_msg}), 500
    
    finally:
        # Cleanup temp files
        try:
            if os.path.exists(input_path):
                os.remove(input_path)
            if os.path.exists(output_path):
                os.remove(output_path)
            os.rmdir(temp_dir)
        except:
            pass

@app.route('/parse', methods=['POST'])
def parse():
    """
    Parse endpoint — returns JSON array of transactions for preview.
    Supports multiple files. Auto-detects bank name.
    
    POST multipart/form-data:
      - file:      one or more PDF/image files (required)
      - password:  PDF password (optional)
      - card_name: card identifier (optional, auto-detected if empty)
      - ocr_engine: tesseract or easyocr (default: easyocr)
    
    Returns JSON:
      { transactions: [...], detected_card: "SBI", count: 23 }
    """
    files = request.files.getlist('file')
    if not files or all(f.filename == '' for f in files):
        return jsonify({'error': 'No files uploaded'}), 400
    
    password = request.form.get('password', '').strip() or None
    card_name = request.form.get('card_name', '').strip()
    ocr_engine = request.form.get('ocr_engine', 'easyocr').strip()
    
    all_transactions = []
    detected_card = ''
    
    for file in files:
        if file.filename == '':
            continue
        ext = os.path.splitext(file.filename)[1].lower()
        if ext not in PDF_EXTENSIONS and ext not in IMAGE_EXTENSIONS:
            continue
        
        temp_dir = tempfile.mkdtemp()
        input_path = os.path.join(temp_dir, f'input{ext}')
        try:
            file.save(input_path)
            
            if ext in PDF_EXTENSIONS:
                transactions = extract_transactions_from_pdf(input_path, password=password)
                # Auto-detect bank name from first PDF
                if not detected_card and not card_name:
                    try:
                        import pdfplumber
                        with pdfplumber.open(input_path, password=password) as pdf:
                            if pdf.pages:
                                text = pdf.pages[0].extract_text(x_tolerance=1, y_tolerance=3) or ''
                                detected_card = detect_card_name(text)
                    except:
                        pass
            else:
                transactions = extract_transactions_from_image(input_path, ocr_engine=ocr_engine)
            
            all_transactions.extend(transactions or [])
        finally:
            try:
                if os.path.exists(input_path):
                    os.remove(input_path)
                os.rmdir(temp_dir)
            except:
                pass
    
    if not all_transactions:
        return jsonify({'error': 'No transactions found in the uploaded file(s).'}), 400
    
    # Sort by date
    all_transactions.sort(key=lambda t: t['date'])
    
    final_card = card_name or detected_card or 'SBI'
    
    # Serialize for JSON
    result = []
    for i, txn in enumerate(all_transactions):
        remark = txn.get('remark', txn.get('description', ''))
        result.append({
            'id': i,
            'date': txn['date'].strftime('%d/%m/%Y %H:%M') if txn['date'].hour or txn['date'].minute else txn['date'].strftime('%d/%m/%Y'),
            'amount': txn['amount'],
            'description': txn.get('description', ''),
            'remark': remark,
            'type': txn['type'],
            'category': _auto_category(remark),
            'card': final_card,
        })
    
    return jsonify({
        'transactions': result,
        'detected_card': detected_card,
        'count': len(result),
    })


@app.route('/reconcile', methods=['POST'])
def reconcile():
    """
    Reconcile endpoint — parse PDF and add missing transactions to Google Sheets.
    
    POST multipart/form-data:
      - file:      PDF file (required)
      - password:  PDF password (optional)
      - card_name: card identifier (default: SBI)
    
    Returns JSON summary of what was added/skipped.
    """
    from sheets_integration import reconcile as sheets_reconcile

    # Validate file
    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded'}), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No file selected'}), 400
    
    # Get form fields
    password = request.form.get('password', '').strip() or None
    card_name = request.form.get('card_name', 'SBI').strip() or 'SBI'
    
    # Parse column config
    columns = None
    columns_json = request.form.get('columns', '').strip()
    if columns_json:
        try:
            import json
            columns = json.loads(columns_json)
        except (json.JSONDecodeError, TypeError):
            columns = None
    
    # Only PDFs supported for reconciliation
    ext = os.path.splitext(file.filename)[1].lower()
    if ext != '.pdf':
        return jsonify({'error': 'Only PDF files are supported for Google Sheets reconciliation.'}), 400
    
    # Save uploaded file to temp location
    temp_dir = tempfile.mkdtemp()
    input_path = os.path.join(temp_dir, f'input{ext}')
    
    try:
        file.save(input_path)
        
        # Extract transactions from PDF
        transactions = extract_transactions_from_pdf(input_path, password=password)
        
        if not transactions:
            debug_text = ""
            try:
                import pdfplumber
                with pdfplumber.open(input_path, password=password) as pdf:
                    full_text = []
                    for i, page in enumerate(pdf.pages):
                        page_text = page.extract_text(x_tolerance=1, y_tolerance=3)
                        if page_text:
                            full_text.append(f"--- PAGE {i} ---")
                            full_text.extend(page_text.split("\n"))
                    
                    if full_text:
                        debug_text = "\n".join(full_text[:40])
                    else:
                        debug_text = "(PDF is empty or image-only with no text layer)"
            except Exception as ex:
                debug_text = f"(Failed to read PDF: {ex})"
            return jsonify({
                'error': f'No transactions found in the PDF.\n{debug_text}'
            }), 400
        
        # Sort by date
        transactions.sort(key=lambda t: t['date'])
        
        # Reconcile with Google Sheets
        result = sheets_reconcile(transactions, card_name, columns=columns)
        
        return jsonify(result)
    
    except FileNotFoundError:
        return jsonify({
            'error': 'Google Sheets credentials not found. '
                     'Place your service_account.json in the project folder. '
                     'See README for setup instructions.'
        }), 500
    
    except Exception as e:
        error_msg = str(e)
        if 'password' in error_msg.lower() or 'encrypt' in error_msg.lower():
            error_msg = 'This PDF is password-protected. Please enter the correct password.'
        return jsonify({'error': error_msg}), 500
    
    finally:
        # Cleanup temp files
        try:
            if os.path.exists(input_path):
                os.remove(input_path)
            os.rmdir(temp_dir)
        except:
            pass


@app.route('/api/status')
def api_status():
    """Check if Google Sheets is connected."""
    try:
        from sheets_integration import _open_spreadsheet
        ss = _open_spreadsheet()
        return jsonify({
            'connected': True,
            'sheet_name': ss.title,
            'tabs': [ws.title for ws in ss.worksheets()],
        })
    except FileNotFoundError:
        return jsonify({
            'connected': False,
            'error': 'service_account.json not found',
        })
    except Exception as e:
        return jsonify({
            'connected': False,
            'error': str(e),
        })


@app.route('/compare')
def compare_page():
    return render_template('compare.html', version="2.1.0")


@app.route('/api/compare', methods=['POST'])
def compare_statements():
    """
    Compare two PDF statements side by side.
    Expects: fileA, fileB (PDF files), optional password, card_name
    """
    fileA = request.files.get('fileA')
    fileB = request.files.get('fileB')
    if not fileA or not fileB:
        return jsonify({'error': 'Two PDF files are required'}), 400
    
    password = request.form.get('password', '').strip() or None
    card_name = request.form.get('card_name', '').strip() or 'SBI'
    
    import tempfile
    results = {}
    for label, f in [('monthA', fileA), ('monthB', fileB)]:
        ext = os.path.splitext(f.filename)[1].lower()
        temp_dir = tempfile.mkdtemp()
        input_path = os.path.join(temp_dir, f'input{ext}')
        try:
            f.save(input_path)
            if ext in PDF_EXTENSIONS:
                txns = extract_transactions_from_pdf(input_path, password=password)
            elif ext in IMAGE_EXTENSIONS:
                txns = extract_transactions_from_image(input_path)
            else:
                txns = []
            
            debits = [t for t in (txns or []) if t['type'] != 'C' and t['amount'] > 0]
            merchants = set()
            categories = {}
            total = 0
            for t in debits:
                remark = t.get('remark', t.get('description', ''))
                merchants.add(remark)
                total += t['amount']
                cat = _auto_category(remark) or 'Uncategorized'
                categories[cat] = categories.get(cat, 0) + t['amount']
            
            results[label] = {
                'total': round(total, 2),
                'count': len(debits),
                'merchants': sorted(merchants),
                'categories': {k: round(v, 2) for k, v in sorted(categories.items(), key=lambda x: -x[1])}
            }
        finally:
            try:
                if os.path.exists(input_path):
                    os.remove(input_path)
                os.rmdir(temp_dir)
            except:
                pass
    
    setA = set(results['monthA']['merchants'])
    setB = set(results['monthB']['merchants'])
    
    totalA = results['monthA']['total']
    totalB = results['monthB']['total']
    delta = round(((totalB - totalA) / totalA * 100) if totalA else 0, 1)
    
    return jsonify({
        'monthA': results['monthA'],
        'monthB': results['monthB'],
        'delta': delta,
        'new_merchants': sorted(setB - setA),
        'stopped_merchants': sorted(setA - setB)
    })


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True)
