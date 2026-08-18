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

    private val TRANSACTION_RE = Regex("^(\\d{2}\\s+\\w{3}\\s+\\d{2})\\s+(.+?)\\s+([+-]?)\\s*(?:₹|Rs\\.?|INR|€|E|\\$)?\\s*([\\d,]+\\.\\d{2})\\s*([CDcd][rR]?\\.?)?\\s*$")
    private val TRANSACTION_OCR_RE = Regex("^(\\d{2}\\s+\\w{3}\\s+\\d{2})\\s+(.+?)\\s+([+-]?)\\s*(?:₹|Rs\\.?|INR|€|E|\\$)?\\s*([\\d,]+\\.\\d{2})\\s*([CDcd][rR]?\\.?)?\\s*$")
    private val FORMAT2_RE = Regex("^(\\d{2}/\\d{2}/\\d{4}\\s*(?:\\|)?\\s*\\d{2}:\\d{2})\\s+(.+?)\\s+(?:(?:[+-]\\s*)?\\d+\\s+)?([+-]?)\\s*(?:₹|C|c|€|E)?\\s*([\\d,]+\\.\\d{2})\\b.*$")

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

    fun parseDate(dateStr: String): LocalDate {
        val formatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd MMM yy")
            .toFormatter(Locale.ENGLISH)
        return LocalDate.parse(dateStr, formatter)
    }

    fun parseDateFormat2(dateStr: String): Pair<LocalDate, LocalTime?> {
        val cleanStr = dateStr.replace("|", "").replace(Regex("\\s+"), " ").trim()
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val dateTime = LocalDateTime.parse(cleanStr, formatter)
        return Pair(dateTime.toLocalDate(), dateTime.toLocalTime())
    }

    fun cleanAmount(amountStr: String): Double {
        return amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    fun parseLinesToTransactions(lines: List<String>, useOcrRegex: Boolean = false): List<Transaction> {
        val transactions = mutableListOf<Transaction>()

        for (line in lines) {
            val cleanLine = line.trim()
            if (shouldSkipLine(cleanLine)) continue

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
                    
                    var isCredit = cdInd.startsWith("C") || sign == "+"
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

                    transactions.add(Transaction(date, null, desc, amount, type, remark))
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

                    val (date, time) = parseDateFormat2(dateStr)
                    var amount = cleanAmount(amountStr)
                    val isCredit = sign == "+"

                    if (isCredit) {
                        amount = -amount
                    }

                    val type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT
                    val remark = merchantNormalizer.simplifyDescription(desc)

                    transactions.add(Transaction(date, time, desc, amount, type, remark))
                } catch (e: Exception) {
                    // Ignore parse errors for line
                }
            }
        }
        return transactions
    }
}
