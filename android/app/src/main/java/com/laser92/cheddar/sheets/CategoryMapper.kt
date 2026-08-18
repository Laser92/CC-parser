package com.laser92.cheddar.sheets

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryMapper @Inject constructor() {
    
    // Keyword in merchant remark -> Category
    private val remarkToCategory = mapOf(
        "swiggy" to "Swiggy",
        "zomato" to "Online Food",
        "blinkit" to "Blinkit",
        "zepto" to "Zepto",
        "bigbasket" to "Grocery",
        "dmart" to "Grocery",
        "amazon" to "Amazon",
        "flipkart" to "Flipkart",
        "myntra" to "Fashion",
        "ajio" to "Fashion",
        "nykaa" to "Fashion",
        "uber" to "Travel",
        "ola" to "Travel",
        "rapido" to "Travel",
        "irctc" to "Travel",
        "makemytrip" to "Travel",
        "netflix" to "Subscriptions >.<",
        "spotify" to "Subscriptions >.<",
        "hotstar" to "Subscriptions >.<",
        "prime video" to "Subscriptions >.<",
        "youtube" to "Subscriptions >.<",
        "rentomojo" to "Subscriptions >.<",
        "google" to "Google",
        "apple" to "Apple",
        "blue tokai" to "Food & Drinks",
        "starbucks" to "Food & Drinks",
        "minimalist" to "Personal Care",
        "reliance" to "Grocery",
        "jiomart" to "Grocery",
        "eternal" to "Swiggy",
        "orbgen" to "Swiggy"
    )
    
    // Category -> Default card
    private val categoryToCard = mapOf(
        "Swiggy" to "Swiggy",
        "Online Food" to "SBI",
        "Blinkit" to "SBI",
        "Zepto" to "SBI",
        "Grocery" to "SBI",
        "Amazon" to "Amazon",
        "Flipkart" to "SBI",
        "Fashion" to "SBI",
        "Travel" to "SBI",
        "Subscriptions >.<" to "SBI",
        "Google" to "SBI",
        "Apple" to "SBI",
        "Food & Drinks" to "SBI",
        "Personal Care" to "SBI"
    )
    
    /**
     * Applies categories to a merchant remark.
     * 
     * @param merchant The merchant remark string.
     * @return A Pair containing the matched Category and default Card, respectively.
     */
    fun applyCategories(merchant: String): Pair<String, String> {
        val lowerMerchant = merchant.lowercase()
        for ((keyword, category) in remarkToCategory) {
            if (lowerMerchant.contains(keyword)) {
                val card = categoryToCard[category] ?: "SBI"
                return Pair(category, card)
            }
        }
        return Pair("", "SBI")
    }
}
