package com.laser92.cheddar.parser

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantNormalizer @Inject constructor() {
    private val CITIES = listOf(
        "BANGALORE", "BENGALURU", "MUMBAI", "DELHI", "NEW DELHI", "GURGAON", "GURUGRAM", 
        "NOIDA", "CHENNAI", "HYDERABAD", "PUNE", "KOLKATA", "JAIPUR", "AHMEDABAD", 
        "LUCKNOW", "CHANDIGARH", "INDORE", "KOCHI", "THIRUVANANTHAPURAM", "COIMBATORE"
    )

    private val MERCHANT_MAPPINGS = mapOf(
        "AMAZON SELLER" to "Amazon",
        "ASSPL" to "Amazon",
        "RSP*BLINK COMMERCE" to "Blinkit",
        "RSP BLINK" to "Blinkit",
        "BEMINIMALIST" to "Minimalist",
        "ZEPTO MARKETPLACE" to "Zepto",
        "SWIGGY" to "Swiggy",
        "BUNDL TECHNOLOGIES" to "Swiggy",
        "ZOMATO" to "Zomato",
        "FLIPKART" to "Flipkart",
        "INSTAKART" to "Flipkart",
        "RAZ*BLUE TOKAI" to "Blue Tokai Coffee",
        "PTM*RELIANCE RETAIL" to "Reliance / JioMart",
        "MYNTRA DESIGNS" to "Myntra",
        "UNIQLO" to "Uniqlo",
        "UBER" to "Uber / Ola",
        "OLACABS" to "Uber / Ola",
        "NETFLIX" to "Netflix",
        "SPOTIFY" to "Spotify",
        "GOOGLE" to "Google",
        "GOOGLEPLAY" to "Google",
        "APPLE.COM" to "Apple",
        "RENTOMOJO" to "Rentomojo",
        "ORBGEN TECHNOLOGIES" to "Eternal (Swiggy parent)",
        "ETERNAL LIMITED" to "Eternal (Swiggy parent)",
        "CARBONTREE" to "Carbontree",
        "CMA EQUIPMENTS" to "CMA Equipments",
        "BLING QUEEN" to "Bling Queen",
        "RATNADEEP" to "Ratnadeep",
        "CRED" to "CRED"
    )

    private val KNOWN_CATEGORIES = listOf(
        "GROCERY &.*", "GROCERY(?: AND SUPERMARKET)?", "SUPERMARKET", "MISCELLANEOUS",
        "COMPUTERS", "RESTAURANTS?", "DEPARTMENT STORES?", "HEALTHCARE",
        "TRAVEL(?: & ENTERTAINMENT)?", "UTILITIES", "APPAREL", "FUEL",
        "ENTERTAINMENT", "HOTEL", "TELECOMMUNICATION", "EDUCATION",
        "FINANCIAL SERVICES", "PERSONAL SERVICES", "BUSINESS SERVICES",
        "AUTOMOBILE", "ELECTRONICS"
    )
    private val CATEGORY_REGEX = Regex("\\s+(?:" + KNOWN_CATEGORIES.joinToString("|") + ")\\s*$", RegexOption.IGNORE_CASE)
    private val STATE_REGEX = Regex("\\s+(?:KA|MH|DL|TN|TS|TG|WB|UP|HR|GJ|RJ|MP|KL|AP|PB|BR|JK|OR|GA|IND|IN)\\s*$", RegexOption.IGNORE_CASE)

    fun simplifyDescription(desc: String): String {
        var cleanDesc = desc.uppercase(Locale.getDefault()).trim()

        // Strip known trailing merchant categories
        cleanDesc = CATEGORY_REGEX.replace(cleanDesc, "").trim()

        // Strip 12-digit UPI RRN / reference number
        cleanDesc = cleanDesc.replace(Regex("\\s+\\d{12}\\b"), "").trim()

        // Strip leading UPI prefix (e.g. "UPI ", "UPI/", "UPI-")
        cleanDesc = cleanDesc.replace(Regex("^UPI[\\s\\-_/]+", RegexOption.IGNORE_CASE), "").trim()

        // Remove asterisks
        cleanDesc = cleanDesc.replace("*", " ")

        // Strip trailing known state/country codes
        cleanDesc = STATE_REGEX.replace(cleanDesc, "")

        // Strip trailing city names
        for (city in CITIES) {
            if (cleanDesc.endsWith(" $city")) {
                cleanDesc = cleanDesc.removeSuffix(" $city")
            }
        }

        // Re-check trailing categories after city/state strip
        cleanDesc = CATEGORY_REGEX.replace(cleanDesc, "").trim()

        // Apply merchant mappings
        for ((pattern, name) in MERCHANT_MAPPINGS) {
            if (cleanDesc.contains(pattern)) {
                return name
            }
        }

        // Collapse multiple spaces
        cleanDesc = cleanDesc.replace(Regex("\\s+"), " ").trim()

        // Title case
        return cleanDesc.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.getDefault()).replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
        }
    }
}
