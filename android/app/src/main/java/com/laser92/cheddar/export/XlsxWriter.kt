package com.laser92.cheddar.export

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// Assuming Transaction is in a model package. Adjust import as needed.
import com.laser92.cheddar.model.Transaction
import com.laser92.cheddar.sheets.CategoryMapper

@Singleton
class XlsxWriter @Inject constructor(
    private val categoryMapper: CategoryMapper
) {

    /**
     * Writes a list of transactions to an XLSX file.
     * 
     * @param transactions List of transactions to write
     * @param outputFile The destination file
     * @param cardName The name of the credit card
     * @param style 1 for Blue header with green highlights, 2 for Dark blue header with alternating row fill
     * @return Number of transactions written
     */
    fun writeToXlsx(
        transactions: List<Transaction>,
        outputFile: File,
        cardName: String = "SBI",
        style: Int = 1,
        columns: List<com.laser92.cheddar.model.ColumnConfig> = com.laser92.cheddar.model.DEFAULT_COLUMNS
    ): Int {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Credit_$cardName")

        // Styles
        val headerStyle = workbook.createCellStyle()
        val font = workbook.createFont()
        font.color = IndexedColors.WHITE.index
        font.bold = true
        headerStyle.setFont(font)
        headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
        
        val dataStyle = workbook.createCellStyle()
        dataStyle.borderBottom = BorderStyle.THIN
        dataStyle.borderTop = BorderStyle.THIN
        dataStyle.borderLeft = BorderStyle.THIN
        dataStyle.borderRight = BorderStyle.THIN

        val highlightStyle = workbook.createCellStyle()
        highlightStyle.cloneStyleFrom(dataStyle)

        val altRowStyle = workbook.createCellStyle()
        altRowStyle.cloneStyleFrom(dataStyle)

        // Set colors based on style
        if (style == 1) {
            val headerColor = XSSFColor(byteArrayOf(68.toByte(), 114.toByte(), 196.toByte()), null)
            (headerStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(headerColor)
            
            val highlightColor = XSSFColor(byteArrayOf(198.toByte(), 239.toByte(), 206.toByte()), null)
            (highlightStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(highlightColor)
            highlightStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
        } else {
            val headerColor = XSSFColor(byteArrayOf(92.toByte(), 115.toByte(), 156.toByte()), null)
            (headerStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(headerColor)

            val altColor = XSSFColor(byteArrayOf(233.toByte(), 238.toByte(), 244.toByte()), null)
            (altRowStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(altColor)
            altRowStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        // Row 1: empty (index 0)
        sheet.createRow(0)

        // Row 2: Headers (index 1)
        val headerRow = sheet.createRow(1)
        for ((index, config) in columns.withIndex()) {
            val cell = headerRow.createCell(index)
            cell.setCellValue(config.label)
            cell.cellStyle = headerStyle
        }

        // Formatter for date (DD Mon YY)
        val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yy")
        // Data starts at row 3 (index 2)
        var rowIndex = 2
        for (txn in transactions) {
            val row = sheet.createRow(rowIndex)
            val isEven = (rowIndex - 2) % 2 != 0 // For alternating rows
            val currentDataStyle = if (style == 2 && isEven) altRowStyle else dataStyle
            val dateFormatterStr = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val dateTimeFormatterStr = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            
            for ((colIndex, config) in columns.withIndex()) {
                val cell = row.createCell(colIndex)
                cell.cellStyle = if (config.type == "my_share" && style == 1) highlightStyle else currentDataStyle

                when (config.type) {
                    "date" -> {
                        val dateStr = if (txn.date.hour == 0 && txn.date.minute == 0) {
                            txn.date.format(dateFormatterStr)
                        } else {
                            txn.date.format(dateTimeFormatterStr)
                        }
                        cell.setCellValue(dateStr)
                    }
                    "amount" -> cell.setCellValue(txn.amount)
                    "my_share" -> {} // Empty
                    "nitt_share" -> {
                        val excelRow = rowIndex + 1
                        val amountColLetter = (65 + columns.indexOfFirst { it.type == "amount" }).toChar()
                        val myShareColLetter = (65 + columns.indexOfFirst { it.type == "my_share" }).toChar()
                        if (columns.any { it.type == "amount" } && columns.any { it.type == "my_share" }) {
                            cell.cellFormula = "IF($amountColLetter$excelRow<>0,$amountColLetter$excelRow-$myShareColLetter$excelRow,\"\")"
                        }
                    }
                    "remark" -> cell.setCellValue(txn.remark)
                    "category" -> {
                        val category = categoryMapper.applyCategories(txn.remark).first
                        cell.setCellValue(category)
                    }
                    "card" -> cell.setCellValue(cardName)
                    "custom" -> {
                        val excelRow = rowIndex + 1
                        val formula = config.formula.replace("{row}", excelRow.toString())
                        if (formula.isNotBlank()) {
                            if (formula.startsWith("=")) {
                                cell.cellFormula = formula.substring(1)
                            } else {
                                cell.cellFormula = formula
                            }
                        }
                    }
                }
            }

            rowIndex++
        }

        // Auto-filter on all columns
        val lastRow = if (rowIndex > 2) rowIndex - 1 else 2
        sheet.setAutoFilter(CellRangeAddress(1, lastRow, 0, if (columns.isNotEmpty()) columns.size - 1 else 0))

        // Freeze pane at row 3 (index 2)
        sheet.createFreezePane(0, 2)

        // Column widths
        for (i in columns.indices) {
            sheet.setColumnWidth(i, 15 * 256)
        }

        // Write to file
        FileOutputStream(outputFile).use { out ->
            workbook.write(out)
        }
        workbook.close()

        return transactions.size
    }
}
