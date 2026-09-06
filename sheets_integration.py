"""
Google Sheets Integration for CC Statement Reconciliation
=========================================================
Connects to the user's expense-tracking Google Sheet, compares
parsed PDF transactions against existing data, and appends only
the ones that are missing (skips duplicates).

Authentication:
    Uses a Google Cloud Service Account.  One-time setup:
    1. Go to https://console.cloud.google.com
    2. Create a project (or pick an existing one)
    3. Enable the Google Sheets API
    4. Create a Service Account → download the JSON key
    5. Share your Google Sheet with the service account email
    6. Save the JSON key as  service_account.json  in this folder
"""

import os
from datetime import datetime

import gspread
from google.oauth2.service_account import Credentials


# ---------------------------------------------------------------------------
# Configuration  —  must match your Google Apps Script settings
# ---------------------------------------------------------------------------

SHEET_ID = os.environ.get(
    'SHEET_ID',
    '1mmetc8XmMGdY3jsq6OBpc8VwhfP0wdKf-IbmHovtva8',
)

SERVICE_ACCOUNT_FILE = os.environ.get(
    'GOOGLE_SA_KEY',
    os.path.join(os.path.dirname(os.path.abspath(__file__)), 'service_account.json'),
)

TIMEZONE = 'Asia/Kolkata'

MONTH_NAMES = [
    'Jan', 'Feb', 'March', 'April', 'May', 'June',
    'July', 'Aug', 'Sept', 'Oct', 'Nov', 'Dec',
]

TEMPLATE_TAB_NAME = 'Template'

# Column indices — 1-based, matching the GAS COL object
COL_DATE      = 1   # A
COL_AMOUNT    = 2   # B
COL_MYSHARE   = 3   # C
COL_NITTSHARE = 4   # D
COL_REMARK    = 5   # E
COL_CATEGORY  = 6   # F
COL_CARD      = 7   # G

# Remark -> Category mapping  (mirrors GAS REMARK_TO_CATEGORY)
REMARK_TO_CATEGORY = {
    'swiggy':              'Swiggy',
    'instamart':           'Instamart',
    'blinkit foods limit': 'Bistro',
    'blinkit':             'Blinkit',
    'zomato':              'Online Food',
    'bistro':              'Bistro',
    'rentomojo':           'Subscriptions >.<',
    'wifi':                'Subscriptions >.<',
    'coitonic':            'Clothes',
    'ratnadeep':           'Ratnadeep',
    'zepto':               'Blinkit',
    'amazon':              'Amazon',
    'devaraj enterpr':     'Petrol',
    'anand':               'Outside Food'
}

# Category -> Card mapping  (mirrors GAS CATEGORY_TO_CARD)
CATEGORY_TO_CARD = {
    'swiggy':              'Swiggy',
    'instamart':           'Swiggy',
    'blinkit':             'SBI',
    'online food':         'SBI',
    'online':              'SBI',
    'bistro':              'SBI',
    'subscriptions >.<':   'SBI'
}


# ---------------------------------------------------------------------------
# Google Sheets client
# ---------------------------------------------------------------------------

_client_cache = None


def _get_client():
    """Return an authorised gspread client (cached)."""
    global _client_cache
    if _client_cache is None:
        scopes = [
            'https://www.googleapis.com/auth/spreadsheets',
            'https://www.googleapis.com/auth/drive',
        ]
        creds = Credentials.from_service_account_file(
            SERVICE_ACCOUNT_FILE, scopes=scopes,
        )
        _client_cache = gspread.authorize(creds)
    return _client_cache


def _open_spreadsheet():
    """Open the expense-tracking spreadsheet by ID."""
    return _get_client().open_by_key(SHEET_ID)


# ---------------------------------------------------------------------------
# Tab helpers
# ---------------------------------------------------------------------------

def get_tab_name(dt: datetime) -> str:
    """Convert a datetime to a tab name, e.g.  July '26"""
    month = MONTH_NAMES[dt.month - 1]
    year  = str(dt.year)[-2:]
    return f"{month} '{year}"


def _get_or_create_tab(spreadsheet, dt: datetime):
    """
    Return the worksheet for the month of *dt*.
    If it doesn't exist, clone from the Template tab.
    """
    tab_name = get_tab_name(dt)

    try:
        return spreadsheet.worksheet(tab_name)
    except gspread.WorksheetNotFound:
        pass

    # Clone from Template
    try:
        template = spreadsheet.worksheet(TEMPLATE_TAB_NAME)
    except gspread.WorksheetNotFound:
        raise ValueError(
            f'Tab "{tab_name}" not found and no "{TEMPLATE_TAB_NAME}" tab '
            'exists to create one from.'
        )

    new_ws = spreadsheet.duplicate_sheet(
        template.id,
        new_sheet_name=tab_name,
        insert_sheet_index=0,
    )
    return new_ws


# ---------------------------------------------------------------------------
# Row-finding logic  (mirrors GAS findNextRow_)
# ---------------------------------------------------------------------------

def _find_next_row(worksheet) -> int:
    """
    Scan column B (Amount) from row 2.
    Tolerate gaps of up to 2 empty rows inside the data block.
    3+ consecutive empties = end of data.
    Return the 1-based row number to write the next entry.
    """
    col_b = worksheet.col_values(COL_AMOUNT)       # list of strings

    if len(col_b) <= 1:
        return 2                                   # only header or empty

    last_data_row = 1                               # 1 = header (fallback)
    empty_streak  = 0

    for i in range(1, len(col_b)):                  # index 1 → row 2
        val = (col_b[i] or '').strip()
        if val:
            last_data_row = i + 1                   # 1-based row number
            empty_streak  = 0
        else:
            empty_streak += 1
            if empty_streak > 2:
                break

    return last_data_row + 1


# ---------------------------------------------------------------------------
# Category / card mapping  (mirrors GAS applyCategories_)
# ---------------------------------------------------------------------------

def _apply_categories(merchant: str) -> tuple[str, str]:
    """Return (category, card) based on the merchant name."""
    text = merchant.lower()
    for key, category in REMARK_TO_CATEGORY.items():
        if key in text:
            card = CATEGORY_TO_CARD.get(category.lower(), '')
            return category, card
    return '', ''


# ---------------------------------------------------------------------------
# Duplicate detection  (mirrors GAS isDuplicate_)
# ---------------------------------------------------------------------------

import re as _re

def _parse_sheet_amount(raw):
    """Parse an amount from the sheet — handles ₹ symbol, commas, spaces."""
    if isinstance(raw, (int, float)):
        return float(raw)
    if isinstance(raw, str):
        cleaned = _re.sub(r'[₹$€£,\s]', '', raw.strip())
        if cleaned:
            return float(cleaned)
    raise ValueError(f'Cannot parse amount: {raw}')

def _is_duplicate(existing_rows: list[list], txn_date: datetime, txn_amount: float) -> bool:
    """
    Check whether a transaction already exists in *existing_rows*.
    Match on: same calendar day  +  amount within ±₹0.50.
    """
    txn_day = txn_date.strftime('%d/%m/%Y')

    for row in existing_rows:
        if len(row) < 2:
            continue

        raw_date   = row[COL_DATE - 1]
        raw_amount = row[COL_AMOUNT - 1]

        if not raw_date and not raw_amount:
            continue

        # --- date comparison (day-level) ---
        row_day = ''
        if isinstance(raw_date, str) and raw_date:
            row_day = raw_date.strip()[:10]          # first 10 chars: dd/MM/yyyy

        # --- amount comparison ---
        try:
            row_amount = _parse_sheet_amount(raw_amount)
        except (ValueError, TypeError):
            continue

        if row_day == txn_day and abs(row_amount - txn_amount) < 0.50:
            return True

    return False


# ---------------------------------------------------------------------------
# Main reconciliation entry-point
# ---------------------------------------------------------------------------

def reconcile(transactions: list[dict], card_name: str, columns: list[dict] = None) -> dict:
    """
    Compare *transactions* (from pdf_parser) against the Google Sheet.
    Append only the ones that don't already exist.

    Each transaction dict must have at least:
        date        : datetime
        amount      : float  (negative for refunds)
        remark      : str    (merchant / description)

    Returns a summary dict:
        total_parsed, added, skipped, errors,
        added_transactions, skipped_transactions
    """
    spreadsheet = _open_spreadsheet()

    result = {
        'total_parsed': len(transactions),
        'added': 0,
        'skipped': 0,
        'errors': 0,
        'added_transactions': [],
        'skipped_transactions': [],
        'error_messages': [],
    }

    # ── Group by month tab ──────────────────────────────────────────
    by_tab: dict[str, list[dict]] = {}
    for txn in transactions:
        tab = get_tab_name(txn['date'])
        by_tab.setdefault(tab, []).append(txn)

    # ── Process each monthly tab ────────────────────────────────────
    for tab_name, month_txns in by_tab.items():
        try:
            worksheet = _get_or_create_tab(spreadsheet, month_txns[0]['date'])

            # Read all existing rows (skip header)
            all_values = worksheet.get_all_values()
            existing = all_values[1:] if len(all_values) > 1 else []

            rows_to_add: list[list] = []
            added_details: list[dict] = []

            # ── Count-based duplicate detection ──────────────────────
            # Count how many times each (day, amount) pair appears in
            # the PDF batch and in the existing sheet data.
            from collections import Counter

            def _make_key(dt, amt):
                """Normalise a (date, amount) pair into a hashable key."""
                return (dt.strftime('%d/%m/%Y'), round(amt, 0))

            # PDF counts
            pdf_counts: Counter = Counter()
            for txn in month_txns:
                pdf_counts[_make_key(txn['date'], txn['amount'])] += 1

            # Sheet counts
            sheet_counts: Counter = Counter()
            for row in existing:
                if len(row) < 2:
                    continue
                raw_d, raw_a = row[COL_DATE - 1], row[COL_AMOUNT - 1]
                if not raw_d and not raw_a:
                    continue
                try:
                    rd = (raw_d.strip()[:10]) if isinstance(raw_d, str) else ''
                    ra = _parse_sheet_amount(raw_a)
                    sheet_counts[(rd, round(ra, 0))] += 1
                except (ValueError, TypeError):
                    continue

            # Track how many we decide to add per key during this loop
            added_counts: Counter = Counter()

            for txn in month_txns:
                date    = txn['date']
                amount  = txn['amount']
                merchant = txn.get('remark', txn.get('description', 'Unknown'))

                # Simplify merchant name
                from pdf_parser import simplify_description
                merchant = simplify_description(merchant)

                key = _make_key(date, amount)
                pdf_n   = pdf_counts[key]       # how many in the PDF
                sheet_n = sheet_counts[key]      # how many already in sheet

                # Decision logic:
                #   ≤ 2 in PDF  → simple dup: skip if sheet already has ≥1
                #   > 2 in PDF  → count-based: add only (pdf_n − sheet_n) new
                is_dup = False
                if pdf_n <= 2:
                    # Simple: any match in sheet → skip
                    is_dup = sheet_n > 0
                else:
                    # Count-based: allow up to (pdf_n − sheet_n) additions
                    allowed = max(0, pdf_n - sheet_n)
                    is_dup = added_counts[key] >= allowed

                if is_dup:
                    result['skipped'] += 1
                    result['skipped_transactions'].append({
                        'date':     date.strftime('%d/%m/%Y'),
                        'amount':   amount,
                        'merchant': merchant,
                    })
                    continue

                added_counts[key] += 1

                # Category / card cascade
                category, mapped_card = _apply_categories(merchant)
                final_card = mapped_card or card_name

                if date.hour == 0 and date.minute == 0:
                    formatted_date = date.strftime('%d/%m/%Y')
                else:
                    formatted_date = date.strftime('%d/%m/%Y %H:%M')
                
                # Build row dynamically based on column config
                if columns:
                    row = []
                    for col_def in columns:
                        col_type = col_def.get('type', '')
                        if col_type == 'date':
                            row.append(formatted_date)
                        elif col_type == 'amount':
                            row.append(amount)
                        elif col_type == 'remark':
                            row.append(merchant)
                        elif col_type == 'card':
                            row.append(final_card)
                        elif col_type == 'category':
                            row.append(category)
                        elif col_type == 'my_share':
                            row.append(amount)
                        elif col_type == 'nitt_share':
                            amt_letter = None
                            share_letter = None
                            for i, c in enumerate(columns):
                                if c["type"] == "amount":
                                    amt_letter = chr(65 + i)
                                elif c["type"] == "my_share":
                                    share_letter = chr(65 + i)
                            if amt_letter and share_letter:
                                row.append(f'=IF({amt_letter}{{row}}<>0,{amt_letter}{{row}}-{share_letter}{{row}},"")')
                            else:
                                row.append(0)
                        elif col_type == 'custom':
                            formula = col_def.get('formula', '')
                            # {row} will be replaced after we know the actual row number
                            row.append(formula)
                        else:
                            row.append('')
                    last_col_letter = chr(64 + len(columns))
                else:
                    row = [
                        formatted_date,     # A  Date
                        amount,             # B  Amount
                        amount,             # C  My Share  (default = full amount)
                        '=IF(B{row}<>0,B{row}-C{row},"")', # D  Nitt Share
                        merchant,           # E  Remark
                        category,           # F  Category
                        final_card,         # G  Card
                    ]
                    last_col_letter = 'G'
                
                rows_to_add.append(row)
                added_details.append({
                    'date':     formatted_date,
                    'amount':   amount,
                    'merchant': merchant,
                    'category': category,
                    'card':     final_card,
                })

                # Add to existing so subsequent txns in this batch
                # are checked against what we're about to write.
                existing.append(row)

            # ── Batch-write all new rows ────────────────────────────
            if rows_to_add:
                start_row = _find_next_row(worksheet)
                end_row   = start_row + len(rows_to_add) - 1
                
                # Replace {row} placeholders in custom formulas
                for r_idx, row in enumerate(rows_to_add):
                    actual_row = start_row + r_idx
                    for c_idx, cell_val in enumerate(row):
                        if isinstance(cell_val, str) and '{row}' in cell_val:
                            rows_to_add[r_idx][c_idx] = cell_val.replace('{row}', str(actual_row))
                
                cell_range = f'A{start_row}:{last_col_letter}{end_row}'

                worksheet.update(
                    cell_range,
                    rows_to_add,
                    value_input_option='USER_ENTERED',
                )

                # Set font to Lexend  (matches GAS appendRow_)
                worksheet.format(cell_range, {
                    'textFormat': {'fontFamily': 'Lexend'},
                })

                # Copy data-validation from the row above  (dropdowns)
                if start_row > 2:
                    _copy_data_validation(
                        spreadsheet, worksheet,
                        source_row=start_row - 1,
                        dest_start_row=start_row,
                        dest_end_row=end_row,
                    )

                result['added'] += len(rows_to_add)
                result['added_transactions'].extend(added_details)

        except Exception as exc:
            result['errors'] += 1
            result['error_messages'].append(f'{tab_name}: {exc}')

    return result


def _copy_data_validation(
    spreadsheet, worksheet,
    source_row: int, dest_start_row: int, dest_end_row: int,
):
    """
    Copy data-validation rules from *source_row* to
    *dest_start_row..dest_end_row* (columns A–G).
    Uses the Sheets API batchUpdate / CopyPaste request.
    """
    try:
        body = {
            'requests': [{
                'copyPaste': {
                    'source': {
                        'sheetId': worksheet.id,
                        'startRowIndex':    source_row - 1,   # 0-based
                        'endRowIndex':      source_row,
                        'startColumnIndex': 0,
                        'endColumnIndex':   COL_CARD,
                    },
                    'destination': {
                        'sheetId': worksheet.id,
                        'startRowIndex':    dest_start_row - 1,
                        'endRowIndex':      dest_end_row,
                        'startColumnIndex': 0,
                        'endColumnIndex':   COL_CARD,
                    },
                    'pasteType': 'PASTE_DATA_VALIDATION',
                },
            }],
        }
        spreadsheet.batch_update(body)
    except Exception:
        pass   # non-critical — dropdowns are nice-to-have


# ---------------------------------------------------------------------------
# Quick test  (run this file directly to verify Sheets connectivity)
# ---------------------------------------------------------------------------

if __name__ == '__main__':
    print('Testing Google Sheets connection...')
    try:
        ss = _open_spreadsheet()
        print(f'  ✅ Connected to: {ss.title}')
        tabs = [ws.title for ws in ss.worksheets()]
        print(f'  📋 Tabs: {", ".join(tabs)}')
    except FileNotFoundError:
        print('  ❌ service_account.json not found.')
        print('     Download it from Google Cloud Console → IAM → Service Accounts.')
    except Exception as e:
        print(f'  ❌ Error: {e}')
