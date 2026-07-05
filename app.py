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
    return render_template('index.html')


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
            return jsonify({
                'error': 'No transactions found. Please check the file and try again. For encrypted PDFs, make sure you entered the correct password.'
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


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=False)
