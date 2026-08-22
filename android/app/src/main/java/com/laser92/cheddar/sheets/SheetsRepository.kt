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
     * Format a LocalDateTime to "July '26" using custom month abbreviations
     * Jan, Feb, March, April, May, June, July, Aug, Sept, Oct, Nov, Dec
     */
    fun getTabName(date: LocalDateTime): String {
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
     * Scans from the bottom up to find the absolute last row with data in Column A or B.
     * This safely skips any empty gaps in pre-formatted tables.
     */
    private fun findNextRow(values: List<List<Any>>): Int {
        if (values.size <= 1) {
            return 2 // 1-indexed for Sheets API, assume row 1 is header
        }
        
        for (i in values.indices.reversed()) {
            val row = values[i]
            val colA = if (row.isNotEmpty()) row[0].toString().trim() else ""
            val colB = if (row.size > 1) row[1].toString().trim() else ""
            
            if (colA.isNotEmpty() || colB.isNotEmpty()) {
                return i + 2 // i is 0-indexed, API is 1-indexed, +1 for next row = +2
            }
        }
        return 2
    }

    /**
     * Basic duplicate check: day-level match and amount tolerance +/- 0.50
     */
    private fun parseRowDate(rawDate: String): LocalDateTime? {
        val formats = listOf(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:m:s"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:m"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
        )
        for (f in formats) {
            try {
                return LocalDateTime.parse(rawDate, f)
            } catch (e: Exception) {
                try {
                    return LocalDate.parse(rawDate, f).atStartOfDay()
                } catch (ex: Exception) {
                    // Try next
                }
            }
        }
        return null
    }

    private fun isDuplicate(
        existingRows: List<List<Any>>, 
        txnDate: LocalDateTime, 
        txnAmount: Double,
        txnCard: String
    ): Boolean {
        for (row in existingRows) {
            if (row.size < 2) continue

            val rawDate = row[0].toString().trim()
            val rawAmtStr = row[1].toString().trim()
            val rawCard = row.getOrNull(6)?.toString()?.trim() ?: ""

            if (rawDate.isEmpty() && rawAmtStr.isEmpty()) continue

            // Card compare (ignoring case)
            if (!rawCard.equals(txnCard, ignoreCase = true)) continue

            // Amount compare
            val rawAmt = rawAmtStr.replace(",", "").replace(Regex("[^\\d.-]"), "").toDoubleOrNull()
            if (rawAmt == null || abs(rawAmt - txnAmount) > 0.50) continue

            // Date compare
            val rowDate = parseRowDate(rawDate) ?: continue
            val minutesDiff = abs(java.time.Duration.between(txnDate, rowDate).toMinutes())
            if (minutesDiff <= 5) {
                return true
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

                // Escape single quotes for A1 notation (e.g. Aug '26 -> 'Aug ''26')
                val safeTabName = "'" + tabName.replace("'", "''") + "'"
                
                // Fetch the entire sheet's data range to absolutely guarantee we get all populated rows
                val range = safeTabName
                val rawResponse = service.spreadsheets().values().get(sheetId, range).executeUnparsed().parseAsString()
                
                val jsonObject = org.json.JSONObject(rawResponse)
                val valuesArray = jsonObject.optJSONArray("values")
                val parsedValues = mutableListOf<List<Any>>()
                
                if (valuesArray != null) {
                    for (i in 0 until valuesArray.length()) {
                        val rowArray = valuesArray.optJSONArray(i)
                        val row = mutableListOf<Any>()
                        if (rowArray != null) {
                            for (j in 0 until rowArray.length()) {
                                row.add(rowArray.getString(j))
                            }
                        }
                        parsedValues.add(row)
                    }
                }
                
                val values = parsedValues
                
                android.util.Log.d("SheetsRepository", "Fetched range: $range, values size: ${values.size}")
                
                val startRow = findNextRow(values)
                var currentRow = startRow
                
                android.util.Log.d("SheetsRepository", "Computed startRow: $startRow")

                // Prepare to gather batch data
                val rowsToAdd = mutableListOf<List<Any>>()
                val formatRequests = mutableListOf<Request>()

                for (txn in txnsInTab) {
                    val dateStr = if (txn.date.hour == 0 && txn.date.minute == 0) {
                        txn.date.format(dateFormatter)
                    } else {
                        txn.date.format(dateTimeFormatter)
                    }

                    val catAndCard = categoryMapper.applyCategories(txn.remark)
                    val category = catAndCard.first
                    val finalCard = if (catAndCard.second.isNotEmpty()) catAndCard.second else cardName

                    val isDup = isDuplicate(values, txn.date, txn.amount, finalCard)

                    if (isDup) {
                        skippedTxns.add(
                            TransactionSummary(
                                date = dateStr,
                                amount = txn.amount,
                                merchant = txn.remark,
                                card = finalCard
                            )
                        )
                    } else {
                        val rowData = listOf(
                            dateStr,
                            txn.amount,
                            txn.amount,
                            0,
                            txn.remark,
                            category,
                            finalCard
                        )
                        rowsToAdd.add(rowData)
                        addedTxns.add(
                            TransactionSummary(
                                date = dateStr,
                                amount = txn.amount,
                                merchant = txn.remark,
                                card = finalCard
                            )
                        )

                        // If you want dropdowns, we add DataValidation formatting here...
                        val validationReq = Request().setRepeatCell(
                            RepeatCellRequest()
                                .setRange(GridRange().setSheetId(targetSheetId).setStartRowIndex(currentRow - 1).setEndRowIndex(currentRow).setStartColumnIndex(6).setEndColumnIndex(7))
                                .setCell(CellData()
                                    .setDataValidation(
                                        DataValidationRule()
                                            .setCondition(BooleanCondition().setType("ONE_OF_LIST").setValues(
                                                listOf(
                                                    ConditionValue().setUserEnteredValue("HDFC Rupay"),
                                                    ConditionValue().setUserEnteredValue("SBI"),
                                                    ConditionValue().setUserEnteredValue("ICICI"),
                                                    ConditionValue().setUserEnteredValue("Axis"),
                                                    ConditionValue().setUserEnteredValue("Amex")
                                                )
                                            ))
                                            .setShowCustomUi(true)
                                            .setStrict(true)
                                    )
                                    .setUserEnteredFormat(com.google.api.services.sheets.v4.model.CellFormat()
                                        .setTextFormat(com.google.api.services.sheets.v4.model.TextFormat().setFontFamily("Lexend"))
                                    )
                                )
                                .setFields("dataValidation,userEnteredFormat.textFormat.fontFamily")
                        )
                        formatRequests.add(validationReq)
                        
                        currentRow++
                    }
                }

                // Batch write values
                if (rowsToAdd.isNotEmpty()) {
                    val body = ValueRange().setValues(rowsToAdd)
                    val updateRange = "$safeTabName!A$startRow:G${currentRow - 1}"
                    
                    android.util.Log.d("SheetsRepository", "Updating range: $updateRange with ${rowsToAdd.size} rows")
                    
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
