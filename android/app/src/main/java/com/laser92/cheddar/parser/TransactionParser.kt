package com.laser92.cheddar.parser

import com.laser92.cheddar.model.Transaction
import com.laser92.cheddar.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionParser @Inject constructor(
    private val merchantNormalizer: MerchantNormalizer
) {

    private val TRANSACTION_RE = Regex("^(\\d{2}\\s+\\w{3}\\s+\\d{2})\\s+(.+?)\\s+([+\\-\\uFF0B\\u2212]?)\\s*(?:₹|Rs\\.?|INR|€|E|\\$)?\\s*([\\d,]+\\.\\d{2})\\s*([CDcd][rR]?\\.?)?\\s*$")
    private val TRANSACTION_OCR_RE = Regex("^(\\d{2}\\s+\\w{3}\\s+\\d{2})\\s+(.+?)\\s+([+\\-\\uFF0B\\u2212]?)\\s*(?:₹|Rs\\.?|INR|€|E|\\$)?\\s*([\\d,]+\\.\\d{2})\\s*([CDcd][rR]?\\.?)?\\s*$")
    private val FORMAT2_RE = Regex("^(\\d{2}/\\d{2}/\\d{4}\\s*(?:\\|)?\\s*\\d{2}:\\d{2})\\s+(.+?)\\s+(?:(?:[+\\-\\uFF0B\\u2212]\\s*)?\\d+\\s+)?([+\\-\\uFF0B\\u2212]?)\\s*(?:₹|Rs\\.?|INR|C|c|€|E)?\\s*([\\d,]+\\.\\d{2})\\b.*$")
    private val FORMAT3_RE = Regex("^(?:VISA\\s*|RuPay\\s*|MasterCard\\s*)?(\\d{2}[-./]\\d{2}[-./]\\d{4}\\s*[^\\d\\w\\s]\\s*\\d{2}:\\d{2})\\s*(.+?)\\s*(?:Refund\\s*)?([+\\-\\uFF0B\\u2212]?)\\s*(?:[^\\w\\s\\d]+\\s*)?(\\d[\\d,]*\\.\\d{2})\\b.*$")

    private val SKIP_PATTERNS = listOf(
        "Date Transaction Details", "For Statement Period", "Statement Period",
        "Domestic Transactions", "International Transactions", "Page \\d+",
        "Opening Balance", "Closing Balance", "Total Amount Due", "Minimum Amount Due",
        "^Total\\s", "Credit Limit", "Available Credit", "^\\s*$", "Finance Charge",
        "Late Payment", "Previous Balance", "Payment Due Date", "Reward Points",
        "Annual Percentage", "^\\d+\\s+of\\s+\\d+$", "Transactions for", "Card Number",
        "Statement Date"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val ocrCreditKeywords = listOf("payment", "cashback", "credit", "refund", "reversal")

    fun shouldSkipLine(line: String): Boolean {
        return SKIP_PATTERNS.any { it.containsMatchIn(line) }
    }

    fun parseDate(dateStr: String): LocalDateTime {
        val formatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd MMM yy")
            .toFormatter(Locale.ENGLISH)
        return LocalDate.parse(dateStr, formatter).atStartOfDay()
    }

    fun parseDateFormat2(dateStr: String): LocalDateTime {
        val cleanStr = dateStr.replace("|", "").replace(Regex("\\s+"), " ").trim()
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        return LocalDateTime.parse(cleanStr, formatter)
    }

    private fun parseDateFormat3(dateStr: String): LocalDateTime {
        val regex = Regex("""(\d{2})[-./](\d{2})[-./](\d{4}).*?(\d{2}):(\d{2})""")
        val matchResult = regex.find(dateStr)
        if (matchResult != null) {
            val (d, m, y, h, min) = matchResult.destructured
            return LocalDateTime.of(y.toInt(), m.toInt(), d.toInt(), h.toInt(), min.toInt())
        }
        throw IllegalArgumentException("Invalid date format: $dateStr")
    }

    fun cleanAmount(amountStr: String): Double {
        return amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    fun parseLinesToTransactions(lines: List<String>, useOcrRegex: Boolean = false): List<Transaction> {
        val transactions = mutableListOf<Transaction>()

        // Pre-process lines to merge multi-line transactions (e.g. amount on next line)
        val mergedLines = mutableListOf<String>()
        var currentMerged = ""
        
        for (line in lines) {
            val cleanLine = line.replace('\u00A0', ' ').trim()
            if (shouldSkipLine(cleanLine)) continue
            
            val startsWithDate1 = Regex("^\\d{2}\\s+\\w{3}\\s+\\d{2}").find(cleanLine) != null
            val startsWithDate2 = Regex("^\\d{2}/\\d{2}/\\d{4}").find(cleanLine) != null
            val startsWithDate3 = Regex("^(?:VISA\\s+|RuPay\\s+|MasterCard\\s+)?\\d{2}-\\d{2}-\\d{4}").find(cleanLine) != null
            
            if (startsWithDate1 || startsWithDate2 || startsWithDate3) {
                if (currentMerged.isNotEmpty()) {
                    mergedLines.add(currentMerged)
                }
                currentMerged = cleanLine
            } else {
                if (currentMerged.isNotEmpty()) {
                    currentMerged += " " + cleanLine
                }
            }
        }
        if (currentMerged.isNotEmpty()) {
            mergedLines.add(currentMerged)
        }

        for (cleanLine in mergedLines) {
            var matched = false

            // Try Format 1
            val regex1 = if (useOcrRegex) TRANSACTION_OCR_RE else TRANSACTION_RE
            val match1 = regex1.find(cleanLine)
            
            if (match1 != null) {
                try {
                    val dateStr = match1.groupValues[1]
                    val desc = match1.groupValues[2].trim()
                    val sign = match1.groupValues[3]
                    val amountStr = match1.groupValues[4]
                    val cdInd = match1.groupValues.getOrNull(5)?.uppercase() ?: ""

                    val date = parseDate(dateStr)
                    var amount = cleanAmount(amountStr)
                    
                    var isCredit = cdInd.startsWith("C") || sign == "+" || sign == "\uFF0B"
                    if (useOcrRegex && cdInd.isEmpty() && sign.isEmpty()) {
                        val lowerDesc = desc.lowercase()
                        if (ocrCreditKeywords.any { lowerDesc.contains(it) }) {
                            isCredit = true
                        }
                    }

                    if (isCredit) {
                        amount = -amount
                    }

                    val type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT
                    val remark = merchantNormalizer.simplifyDescription(desc)

                    transactions.add(Transaction(date, desc, amount, type, remark))
                    matched = true
                } catch (e: Exception) {
                    // Ignore parse errors for line
                }
            }

            if (matched) continue

            // Try Format 2
            val match2 = FORMAT2_RE.find(cleanLine)
            if (match2 != null) {
                try {
                    val dateStr = match2.groupValues[1]
                    val desc = match2.groupValues[2].trim()
                    val sign = match2.groupValues[3]
                    val amountStr = match2.groupValues[4]

                    val date = parseDateFormat2(dateStr)
                    var amount = cleanAmount(amountStr)
                    val isCredit = sign == "+" || sign == "\uFF0B"

                    if (isCredit) {
                        amount = -amount
                    }

                    val type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT
                    val remark = merchantNormalizer.simplifyDescription(desc)

                    transactions.add(Transaction(date, desc, amount, type, remark))
                    matched = true
                } catch (e: Exception) {
                    // Ignore parse errors for line
                }
            }

            if (matched) continue

            // Try Format 3 (Scapia / Federal)
            val match3 = FORMAT3_RE.find(cleanLine)
            if (match3 != null) {
                try {
                    val dateStr = match3.groupValues[1]
                    val desc = match3.groupValues[2].trim()
                    val sign = match3.groupValues[3]
                    val amountStr = match3.groupValues[4]

                    val date = parseDateFormat3(dateStr)
                    var amount = cleanAmount(amountStr)
                    
                    // In format 3, if it contains "Refund" the regex doesn't explicitly group it as a sign, 
                    // but usually it comes with a '+' or '-' or it says 'Refund'. 
                    // Let's also check if the description has Refund or sign is +
                    val lowerDesc = desc.lowercase()
                    var isCredit = sign == "+" || sign == "\uFF0B" || cleanLine.lowercase().contains("refund")
                    
                    if (isCredit) {
                        amount = -amount
                    }

                    val type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT
                    val remark = merchantNormalizer.simplifyDescription(desc)

                    transactions.add(Transaction(date, desc, amount, type, remark))
                } catch (e: Exception) {
                    // Ignore parse errors for line
                }
            }
        }
        return transactions
    }
}
