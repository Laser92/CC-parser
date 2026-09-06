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
        "rentomojo" to "Subscriptions >.<",
        "wifi" to "Subscriptions >.<",
        "coitonic" to "Clothes",
        "ratnadeep" to "Ratnadeep",
        "zepto" to "Blinkit",
        "amazon" to "Amazon",
        "devaraj enterpr" to "Petrol",
        "anand" to "Outside Food"
    )
    
    // Category -> Default card
    private val categoryToCard = mapOf(
        "swiggy" to "Swiggy",
        "instamart" to "Swiggy",
        "blinkit" to "SBI",
        "online food" to "SBI",
        "online" to "SBI",
        "bistro" to "SBI",
        "subscriptions >.<" to "SBI"
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
