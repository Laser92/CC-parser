import os
import tempfile
import uuid
from flask import Flask, request, render_template, send_file, jsonify
from pdf_parser import (
    extract_transactions_from_pdf,
    extract_transactions_from_image,
    write_to_xlsx,
    PDF_EXTENSIONS,
    IMAGE_EXTENSIONS,
)

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max upload


@app.route('/')
def index():
    return render_template('index.html', version="1.0.3")


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
        count = write_to_xlsx(transactions, output_path, card_name, style=style)
        
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
        result = sheets_reconcile(transactions, card_name)
        
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


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True)
