package com.laser92.cheddar.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrErrorFixer @Inject constructor() {
    fun fixOcrErrors(line: String): String {
        var fixedLine = line

        // Month typos: 3un -> Jun, Ju1 -> Jul, Jui -> Jul, 0ct -> Oct, Mov -> Nov, Oec -> Dec
        fixedLine = fixedLine.replace("3un", "Jun", ignoreCase = true)
        fixedLine = fixedLine.replace("Ju1", "Jul", ignoreCase = true)
        fixedLine = fixedLine.replace("Jui", "Jul", ignoreCase = true)
        fixedLine = fixedLine.replace("0ct", "Oct", ignoreCase = true)
        fixedLine = fixedLine.replace("Mov", "Nov", ignoreCase = true)
        fixedLine = fixedLine.replace("Oec", "Dec", ignoreCase = true)

        // Letter O in amounts -> 0 (in digit context)
        fixedLine = fixedLine.replace(Regex("(?<=\\d)O(?=\\d|\\.|,|\\s)"), "0")
        fixedLine = fixedLine.replace(Regex("(?<=\\d|\\.|,)O(?=\\d)"), "0")
        
        // Rupee symbol noise: € or stray characters near amounts -> clean
        fixedLine = fixedLine.replace("€", "")
        fixedLine = fixedLine.replace("₹", "")

        // Comma/period swaps in numbers (e.g., 1.199.00 -> 1,199.00)
        fixedLine = fixedLine.replace(Regex("(\\d)\\.(\\d{3})\\.(\\d{2})"), "$1,$2.$3")
        fixedLine = fixedLine.replace(Regex("(\\d)\\.(\\d{2})\\.(\\d{2})"), "$1,$2.$3")

        return fixedLine
    }
}
