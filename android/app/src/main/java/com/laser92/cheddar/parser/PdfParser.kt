package com.laser92.cheddar.parser

import android.content.Context
import android.net.Uri
import com.laser92.cheddar.model.Transaction
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionParser: TransactionParser
) {
    init {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extracts transactions from a PDF file
     */
    fun extractTransactions(uri: Uri, password: String? = null): List<Transaction> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file")
        
        return inputStream.use { stream ->
            val document = if (password.isNullOrBlank()) {
                PDDocument.load(stream)
            } else {
                PDDocument.load(stream, password)
            }
            
            document.use { doc ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val text = stripper.getText(doc)
                val lines = text.lines()
                transactionParser.parseLinesToTransactions(lines)
            }
        }
    }

    fun detectCardName(text: String): String {
        val signatures = mapOf(
            "state bank" to "SBI", "sbi card" to "SBI",
            "hdfc bank" to "HDFC", "hdfc ltd" to "HDFC",
            "icici bank" to "ICICI", "icici card" to "ICICI",
            "axis bank" to "Axis", "kotak" to "Kotak",
            "scapia" to "Scapia", "federal bank" to "Scapia",
            "indusind" to "IndusInd", "yes bank" to "Yes Bank",
            "rbl bank" to "RBL", "au small" to "AU Bank",
            "idfc first" to "IDFC First",
            "american express" to "Amex", "amex" to "Amex",
            "standard chartered" to "SC", "citibank" to "Citi",
            "bob card" to "BOB", "canara bank" to "Canara",
            "union bank" to "Union", "pnb" to "PNB",
            "hsbc" to "HSBC", "dbs" to "DBS",
            "slice" to "Slice", "onecard" to "OneCard", "one card" to "OneCard",
        )
        val lower = text.take(500).lowercase()
        for ((key, name) in signatures) {
            if (key in lower) return name
        }
        return ""
    }
}
