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
                val text = stripper.getText(doc)
                val lines = text.lines()
                transactionParser.parseLinesToTransactions(lines)
            }
        }
    }
}
