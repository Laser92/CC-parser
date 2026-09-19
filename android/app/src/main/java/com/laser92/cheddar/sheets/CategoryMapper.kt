package com.laser92.cheddar.sheets

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryMapper @Inject constructor() {
    
    // Keyword in merchant remark -> Category
    private val remarkToCategory = mapOf(
        "swiggy" to "Swiggy",
        "instamart" to "Instamart",
        "blinkit foods limit" to "Bistro",
        "blinkit" to "Blinkit",
        "zomato" to "Online Food",
        "bistro" to "Bistro",
        "rentomojo" to "Subscriptions",
        "wifi" to "Utilities",
        "netflix" to "Subscriptions",
        "spotify" to "Subscriptions",
        "google" to "Subscriptions",
        "apple" to "Subscriptions",
        "coitonic" to "Clothes",
        "ratnadeep" to "Ratnadeep",
        "zepto" to "Blinkit",
        "amazon" to "Amazon",
        "devaraj enterpr" to "Petrol",
        "anand" to "Outside Food",

        // Food & Dining
        "dominos" to "Food",
        "mcdonalds" to "Food",
        "mcdonald" to "Food",
        "kfc" to "Food",
        "burger king" to "Food",
        "pizza hut" to "Food",
        "starbucks" to "Food",
        "chaayos" to "Food",
        "haldirams" to "Food",
        "barbeque nation" to "Food",
        "subway" to "Food",
        "cafe coffee day" to "Food",
        "ccd" to "Food",
        "dunkin" to "Food",
        "baskin robbins" to "Food",
        "faasos" to "Food",
        "box8" to "Food",
        "eatfit" to "Food",
        "biryani" to "Food",

        // Groceries
        "bigbasket" to "Groceries",
        "dmart" to "Groceries",
        "reliance fresh" to "Groceries",
        "reliance smart" to "Groceries",
        "more supermarket" to "Groceries",
        "more retail" to "Groceries",
        "spencer" to "Groceries",
        "nature.*basket" to "Groceries",
        "jiomart" to "Groceries",
        "star bazaar" to "Groceries",

        // Quick Commerce
        "dunzo" to "Quick Commerce",
        "bb now" to "Quick Commerce",
        "bbnow" to "Quick Commerce",

        // Shopping
        "ajio" to "Shopping",
        "nykaa" to "Shopping",
        "meesho" to "Shopping",
        "tata cliq" to "Shopping",
        "h&m" to "Shopping",
        "zara" to "Shopping",
        "decathlon" to "Shopping",
        "ikea" to "Shopping",
        "miniso" to "Shopping",
        "pepperfry" to "Shopping",
        "urban ladder" to "Shopping",
        "croma" to "Shopping",
        "reliance digital" to "Shopping",
        "vijay sales" to "Shopping",

        // Transport
        "rapido" to "Transport",
        "metro" to "Transport",
        "irctc" to "Transport",
        "makemytrip" to "Travel",
        "goibibo" to "Travel",
        "cleartrip" to "Travel",
        "yatra" to "Travel",
        "ixigo" to "Travel",
        "indigo" to "Travel",
        "air india" to "Travel",
        "vistara" to "Travel",
        "spicejet" to "Travel",

        // Subscriptions
        "youtube" to "Subscriptions",
        "hotstar" to "Subscriptions",
        "prime video" to "Subscriptions",
        "chatgpt" to "Subscriptions",
        "openai" to "Subscriptions",
        "notion" to "Subscriptions",
        "github" to "Subscriptions",
        "adobe" to "Subscriptions",
        "microsoft" to "Subscriptions",
        "linkedin" to "Subscriptions",
        "icloud" to "Subscriptions",

        // Fuel
        "indian oil" to "Fuel",
        "iocl" to "Fuel",
        "bharat petroleum" to "Fuel",
        "bpcl" to "Fuel",
        "hp petrol" to "Fuel",
        "hpcl" to "Fuel",
        "shell" to "Fuel",

        // Utilities
        "electricity" to "Utilities",
        "bescom" to "Utilities",
        "water bill" to "Utilities",
        "gas bill" to "Utilities",
        "broadband" to "Utilities",
        "jio fiber" to "Utilities",
        "airtel" to "Utilities",
        "vodafone" to "Utilities",
        "bsnl" to "Utilities",
        "tata play" to "Utilities",
        "dish tv" to "Utilities",

        // Healthcare
        "apollo" to "Healthcare",
        "medplus" to "Healthcare",
        "pharmeasy" to "Healthcare",
        "1mg" to "Healthcare",
        "netmeds" to "Healthcare",
        "practo" to "Healthcare",
        "lenskart" to "Healthcare",

        // Education
        "coursera" to "Education",
        "udemy" to "Education",
        "unacademy" to "Education",
        "byjus" to "Education",
        "upgrad" to "Education",
        "skillshare" to "Education"
    )
    
    // Category -> Default card
    private val categoryToCard = mapOf(
        "swiggy" to "Swiggy",
        "instamart" to "Swiggy",
        "blinkit" to "SBI",
        "online food" to "SBI",
        "online" to "SBI",
        "bistro" to "SBI",
        "subscriptions" to "SBI",
        "fuel" to "SBI",
        "groceries" to "SBI",
        "utilities" to "SBI"
    )
    
    /**
     * Applies categories to a merchant remark.
     * 
     * @param merchant The merchant remark string.
     * @return A Pair containing the matched Category and default Card, respectively.
     */
    fun applyCategories(merchant: String): Pair<String, String> {
        val lowerMerchant = merchant.lowercase()
        // Check longer keywords first to prevent partial matches taking precedence
        val sortedKeys = remarkToCategory.keys.sortedByDescending { it.length }
        
        for (keyword in sortedKeys) {
            if (lowerMerchant.contains(keyword)) {
                val category = remarkToCategory[keyword]!!
                val card = categoryToCard[category.lowercase()] ?: ""
                return Pair(category, card)
            }
        }
        return Pair("", "")
    }
}
