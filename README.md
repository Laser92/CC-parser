# CC Statement Parser

A web app that parses credit card PDF statements (including password-protected ones) and screenshot images into structured XLSX files.

## Features
- 📄 **PDF parsing** with password support for encrypted statements
- 🖼️ **Image OCR** via Tesseract for screenshot parsing
- 📊 **XLSX output** with formulas, formatting, and auto-filters
- 🏷️ Auto-simplified merchant names (Amazon, Zepto, Minimalist, etc.)
- 🎨 Modern dark-themed web UI

## Local Development

```bash
pip install -r requirements.txt
python app.py
```

Then open http://localhost:5000

## Deploy to Render

1. Push this repo to GitHub
2. Go to [render.com](https://render.com) → New → Web Service
3. Connect your GitHub repo
4. Render will auto-detect the `render.yaml` and deploy

## Output XLSX Columns

| Col | Header     | Content                              |
|-----|------------|--------------------------------------|
| A   | Date       | DD/MM/YYYY                           |
| B   | Amount     | Transaction amount                   |
| C   | My Share   | Blank (green) — for user input       |
| D   | Nitt Share | Formula: `=IF(B3<>0,B3-C3,"")`      |
| E   | Remarks    | Auto-simplified merchant name        |
| F   | Category   | Blank — for user input               |
| G   | Card       | User-specified card name             |
