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
        "BLING QUEEN" to "Bling Queen"
    )

    fun simplifyDescription(desc: String): String {
        var cleanDesc = desc.uppercase(Locale.getDefault())

        // Remove asterisks
        cleanDesc = cleanDesc.replace("*", " ")

        // Strip trailing 2-3 letter state/country codes
        val stateRegex = Regex("\\s+[A-Z]{2,3}$")
        cleanDesc = cleanDesc.replace(stateRegex, "")

        // Strip trailing city names
        for (city in CITIES) {
            if (cleanDesc.endsWith(" $city")) {
                cleanDesc = cleanDesc.removeSuffix(" $city")
            }
        }

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
