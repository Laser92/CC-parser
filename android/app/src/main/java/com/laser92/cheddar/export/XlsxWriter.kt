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

@Singleton
class XlsxWriter @Inject constructor() {

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
        style: Int = 1
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
        val headers = listOf("Date", "Amount", "My Share", "Nitt Share", "Remarks", "Category", "Card")
        for ((index, header) in headers.withIndex()) {
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
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
            val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            
            // Column A: Date
            val dateCell = row.createCell(0)
            val dateStr = if (txn.time != null) {
                java.time.LocalDateTime.of(txn.date, txn.time).format(dateTimeFormatter)
            } else {
                txn.date.format(dateFormatter)
            }
            dateCell.setCellValue(dateStr)
            dateCell.cellStyle = currentDataStyle

            // Column B: Amount
            val amountCell = row.createCell(1)
            amountCell.setCellValue(txn.amount) // assumes amount is Double
            amountCell.cellStyle = currentDataStyle

            // Column C: My Share (blank)
            val myShareCell = row.createCell(2)
            myShareCell.cellStyle = if (style == 1) highlightStyle else currentDataStyle

            // Column D: Nitt Share (formula)
            val nittShareCell = row.createCell(3)
            val excelRow = rowIndex + 1
            nittShareCell.cellFormula = "IF(B$excelRow<>0,B$excelRow-C$excelRow,\"\")"
            nittShareCell.cellStyle = currentDataStyle

            // Column E: Remarks
            val remarksCell = row.createCell(4)
            remarksCell.setCellValue(txn.remark) // Using simplified merchant name
            remarksCell.cellStyle = currentDataStyle

            // Column F: Category
            val categoryCell = row.createCell(5)
            categoryCell.setCellValue("") // Empty category as specified
            categoryCell.cellStyle = currentDataStyle

            // Column G: Card
            val cardCell = row.createCell(6)
            cardCell.setCellValue(cardName)
            cardCell.cellStyle = currentDataStyle

            rowIndex++
        }

        // Auto-filter on A2:G{last_row}
        val lastRow = if (rowIndex > 2) rowIndex - 1 else 2
        sheet.setAutoFilter(CellRangeAddress(1, lastRow, 0, 6))

        // Freeze pane at A3 (row index 2)
        sheet.createFreezePane(0, 2)

        // Column widths: A=14, B=12, C=12, D=12, E=30, F=15, G=10
        sheet.setColumnWidth(0, 14 * 256)
        sheet.setColumnWidth(1, 12 * 256)
        sheet.setColumnWidth(2, 12 * 256)
        sheet.setColumnWidth(3, 12 * 256)
        sheet.setColumnWidth(4, 30 * 256)
        sheet.setColumnWidth(5, 15 * 256)
        sheet.setColumnWidth(6, 10 * 256)

        // Write to file
        FileOutputStream(outputFile).use { out ->
            workbook.write(out)
        }
        workbook.close()

        return transactions.size
    }
}
