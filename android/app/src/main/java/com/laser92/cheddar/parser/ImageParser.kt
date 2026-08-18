package com.laser92.cheddar.parser

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.laser92.cheddar.model.Transaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionParser: TransactionParser,
    private val ocrErrorFixer: OcrErrorFixer
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts transactions from an image file using ML Kit Text Recognition
     */
    suspend fun extractTransactions(uri: Uri): List<Transaction> {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        
        // Reconstruct lines from text blocks, sorted by Y then X position
        val elements = mutableListOf<Triple<Float, Float, String>>() // y, x, text
        
        for (block in result.textBlocks) {
            for (line in block.getLines()) {
                val box = line.boundingBox ?: continue
                elements.add(Triple(box.top.toFloat(), box.left.toFloat(), line.text))
            }
        }
        
        // Group by Y proximity (threshold ~15px), then sort by X within each line
        val yThreshold = 15f
        val sortedElements = elements.sortedWith(compareBy({ it.first }, { it.second }))
        
        val reconstructedLines = mutableListOf<String>()
        var currentLineY = -999f
        var currentLineTexts = mutableListOf<Pair<Float, String>>()
        
        for ((y, x, text) in sortedElements) {
            if (y - currentLineY > yThreshold && currentLineTexts.isNotEmpty()) {
                // Flush current line
                val lineStr = currentLineTexts.sortedBy { it.first }.joinToString("  ") { it.second }
                reconstructedLines.add(lineStr)
                currentLineTexts = mutableListOf()
            }
            currentLineTexts.add(Pair(x, text))
            currentLineY = y
        }
        if (currentLineTexts.isNotEmpty()) {
            val lineStr = currentLineTexts.sortedBy { it.first }.joinToString("  ") { it.second }
            reconstructedLines.add(lineStr)
        }
        
        // Apply OCR error fixes and parse
        val fixedLines = reconstructedLines.map { ocrErrorFixer.fixOcrErrors(it) }
        return transactionParser.parseLinesToTransactions(fixedLines, useOcrRegex = true)
    }
}
