package com.laser92.cheddar.sheets

import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import com.laser92.cheddar.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.round

data class ReconcileResult(
    val totalParsed: Int,
    val added: Int,
    val skipped: Int,
    val errors: Int,
    val addedTransactions: List<TransactionSummary>,
    val skippedTransactions: List<TransactionSummary>,
    val errorMessages: List<String>
)

data class TransactionSummary(
    val date: String,
    val merchant: String,
    val amount: Double,
    val card: String
)

@Singleton
class SheetsRepository @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val categoryMapper: CategoryMapper
) {

    private val jsonFactory = GsonFactory.getDefaultInstance()

    private fun getSheetsService(): Sheets {
        val httpTransport = com.google.api.client.http.javanet.NetHttpTransport()
        return Sheets.Builder(httpTransport, jsonFactory, authManager.getCredential())
            .setApplicationName("Cheddar Android")
            .build()
    }

    /**
     * Format a LocalDate to "July '26" using custom month abbreviations
     * Jan, Feb, March, April, May, June, July, Aug, Sept, Oct, Nov, Dec
     */
    fun getTabName(date: LocalDate): String {
        val months = arrayOf(
            "", "Jan", "Feb", "March", "April", "May", "June", 
            "July", "Aug", "Sept", "Oct", "Nov", "Dec"
        )
        val monthStr = months[date.monthValue]
        val yearStr = date.year.toString().takeLast(2)
        return "$monthStr '$yearStr"
    }

    /**
     * Finds or creates a tab based on a "Template" tab.
     */
    private suspend fun getOrCreateTab(service: Sheets, spreadsheetId: String, tabName: String): Int = withContext(Dispatchers.IO) {
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        
        val existingSheet = spreadsheet.sheets.find { it.properties.title == tabName }
        if (existingSheet != null) {
            return@withContext existingSheet.properties.sheetId
        }

        val templateSheet = spreadsheet.sheets.find { it.properties.title == "Template" }
            ?: throw Exception("Template tab not found in the spreadsheet.")

        val duplicateRequest = DuplicateSheetRequest()
            .setSourceSheetId(templateSheet.properties.sheetId)
            .setInsertSheetIndex(spreadsheet.sheets.size)
            .setNewSheetName(tabName)

        val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
            .setRequests(listOf(Request().setDuplicateSheet(duplicateRequest)))

        val response = service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()
        
        return@withContext response.replies[0].duplicateSheet.properties.sheetId
    }

    /**
     * Scans column B for the next empty row. Allows 2 empty gaps, 3+ empty = end.
     */
    private fun findNextRow(values: List<List<Any>>): Int {
        var emptyCount = 0
        var nextRow = 1 // 1-indexed for Sheets API
        
        for (i in values.indices) {
            val row = values[i]
            val colB = if (row.size > 1) row[1].toString().trim() else ""
            
            if (colB.isEmpty()) {
                emptyCount++
                if (emptyCount >= 3) {
                    // Backtrack to the first of the 3 empty rows
                    return nextRow - 2 
                }
            } else {
                emptyCount = 0
            }
            nextRow++
        }
        
        return nextRow
    }

    /**
     * Basic duplicate check: day-level match and amount tolerance +/- 0.50
     */
    private fun isDuplicate(
        existingRows: List<List<Any>>, 
        txnDate: String, 
        txnAmount: Double
    ): Boolean {
        for (row in existingRows) {
            if (row.size >= 2) {
                val sheetDate = row[0].toString().trim()
                val sheetAmountStr = row[1].toString().trim().replace(",", "")
                
                if (sheetDate == txnDate) {
                    val sheetAmount = sheetAmountStr.toDoubleOrNull()
                    if (sheetAmount != null && abs(sheetAmount - txnAmount) <= 0.50) {
                        return true
                    }
                }
            }
        }
        return false
    }

    suspend fun reconcile(transactions: List<Transaction>, cardName: String, sheetId: String): ReconcileResult = withContext(Dispatchers.IO) {
        val addedTxns = mutableListOf<TransactionSummary>()
        val skippedTxns = mutableListOf<TransactionSummary>()
        val errorMsgs = mutableListOf<String>()
        var errors = 0
        
        if (transactions.isEmpty()) {
            return@withContext ReconcileResult(0, 0, 0, 0, addedTxns, skippedTxns, errorMsgs)
        }

        try {
            val service = getSheetsService()
            val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

            // Group transactions by target tab name
            val groupedTxns = transactions.groupBy { getTabName(it.date) }

            for ((tabName, txnsInTab) in groupedTxns) {
                // Get or create tab
                val targetSheetId = getOrCreateTab(service, sheetId, tabName)

                // Read existing data in Column A and B to find next row and check duplicates
                val range = "$tabName!A:B"
                val response = service.spreadsheets().values().get(sheetId, range).execute()
                @Suppress("UNCHECKED_CAST")
                val values = (response.values as? List<List<Any>>) ?: emptyList()
                
                val startRow = findNextRow(values)
                var currentRow = startRow

                // Prepare to gather batch data
                val rowsToAdd = mutableListOf<List<Any>>()
                val formatRequests = mutableListOf<Request>()

                // Count occurrences of (date_str, round(amt)) in the PDF batch
                val pdfCounts = txnsInTab.groupingBy { 
                    val dStr = if (it.time != null) {
                        java.time.LocalDateTime.of(it.date, it.time).format(dateTimeFormatter)
                    } else {
                        it.date.format(dateFormatter)
                    }
                    Pair(dStr, round(it.amount)) 
                }.eachCount()

                // Count occurrences in the sheet
                val sheetCounts = values.mapNotNull { row ->
                    if (row.size >= 2) {
                        val d = row[0].toString().trim()
                        val a = row[1].toString().trim().replace(",", "").toDoubleOrNull()
                        if (a != null) Pair(d, round(a)) else null
                    } else null
                }.groupingBy { it }.eachCount().toMutableMap()

                for (txn in txnsInTab) {
                    val dateStr = if (txn.time != null) {
                        java.time.LocalDateTime.of(txn.date, txn.time).format(dateTimeFormatter)
                    } else {
                        txn.date.format(dateFormatter)
                    }
                    val roundedAmt = round(txn.amount)
                    val matchKey = Pair(dateStr, roundedAmt)
                    
                    val pCount = pdfCounts[matchKey] ?: 0
                    val sCount = sheetCounts[matchKey] ?: 0

                    var isDup = false
                    if (pCount <= 2) {
                        // If 2 or fewer identical transactions in PDF, normal duplicate check
                        isDup = isDuplicate(values, dateStr, txn.amount)
                    } else {
                        // If more than 2, check if we've already satisfied the sheet count
                        if (sCount >= pCount) {
                            isDup = true
                        } else {
                            // Increment sheet count to allow adding the missing ones
                            sheetCounts[matchKey] = sCount + 1
                        }
                    }

                    val summary = TransactionSummary(
                        dateStr, txn.remark, txn.amount, cardName
                    )

                    if (isDup) {
                        skippedTxns.add(summary)
                        continue
                    }

                    // Apply categories based on remark
                    val (category, autoCard) = categoryMapper.applyCategories(txn.remark)
                    val finalCard = if (autoCard.isNotEmpty()) autoCard else cardName

                    // A: Date, B: Amount, C: blank, D: Formula, E: Merchant, F: Category, G: Card
                    val excelRow = currentRow
                    val formula = "=IF(B$excelRow<>\"\", B$excelRow-C$excelRow, \"\")"
                    
                    rowsToAdd.add(listOf(
                        dateStr, 
                        txn.amount, 
                        "", 
                        formula, 
                        txn.remark, 
                        category, 
                        finalCard
                    ))

                    // Apply Lexend formatting and borders (Request formulation)
                    val cellFormat = CellFormat().setTextFormat(TextFormat().setFontFamily("Lexend"))
                    val rowRange = GridRange()
                        .setSheetId(targetSheetId)
                        .setStartRowIndex(currentRow - 1)
                        .setEndRowIndex(currentRow)
                        .setStartColumnIndex(0)
                        .setEndColumnIndex(7)

                    formatRequests.add(Request().setRepeatCell(
                        RepeatCellRequest()
                            .setRange(rowRange)
                            .setCell(CellData().setUserEnteredFormat(cellFormat))
                            .setFields("userEnteredFormat.textFormat.fontFamily")
                    ))
                    
                    // Note: Copying Data Validation from row above can be complex in batch requests.
                    // Assuming basic formatting here, but you can expand `formatRequests` with 
                    // `CopyPasteRequest` to duplicate data validations from `currentRow - 2`.
                    if (currentRow > 2) {
                        val sourceRange = GridRange()
                            .setSheetId(targetSheetId)
                            .setStartRowIndex(currentRow - 2)
                            .setEndRowIndex(currentRow - 1)
                            .setStartColumnIndex(0)
                            .setEndColumnIndex(7)
                        
                        formatRequests.add(Request().setCopyPaste(
                            CopyPasteRequest()
                                .setSource(sourceRange)
                                .setDestination(rowRange)
                                .setPasteType("PASTE_DATA_VALIDATION")
                        ))
                    }

                    addedTxns.add(summary)
                    currentRow++
                }

                // Batch write values
                if (rowsToAdd.isNotEmpty()) {
                    val body = ValueRange().setValues(rowsToAdd)
                    val updateRange = "$tabName!A$startRow:G${currentRow - 1}"
                    
                    service.spreadsheets().values()
                        .update(sheetId, updateRange, body)
                        .setValueInputOption("USER_ENTERED")
                        .execute()

                    // Batch update formats
                    if (formatRequests.isNotEmpty()) {
                        val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(formatRequests)
                        service.spreadsheets().batchUpdate(sheetId, batchUpdate).execute()
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            errors++
            errorMsgs.add(e.message ?: "Unknown Error")
        }

        return@withContext ReconcileResult(
            totalParsed = transactions.size,
            added = addedTxns.size,
            skipped = skippedTxns.size,
            errors = errors,
            addedTransactions = addedTxns,
            skippedTransactions = skippedTxns,
            errorMessages = errorMsgs
        )
    }
}
