package com.laser92.cheddar.model

import kotlinx.serialization.Serializable

@Serializable
data class ColumnConfig(
    val type: String,  // date, amount, remark, card, category, my_share, nitt_share, custom
    val label: String,
    val formula: String = ""
)

val DEFAULT_COLUMNS = listOf(
    ColumnConfig("date", "Date"),
    ColumnConfig("amount", "Amount"),
    ColumnConfig("my_share", "My Share"),
    ColumnConfig("nitt_share", "Nitt Share"),
    ColumnConfig("remark", "Remark"),
    ColumnConfig("category", "Category"),
    ColumnConfig("card", "Card"),
)

val COLUMN_TYPES = listOf(
    "date" to "Date",
    "amount" to "Amount",
    "remark" to "Remark",
    "card" to "Card",
    "category" to "Category",
    "my_share" to "My Share",
    "nitt_share" to "Nitt Share",
    "custom" to "Custom Formula",
)
