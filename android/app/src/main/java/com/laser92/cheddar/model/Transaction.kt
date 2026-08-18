package com.laser92.cheddar.model

import java.time.LocalDate
import java.time.LocalTime

enum class TransactionType { CREDIT, DEBIT }

data class Transaction(
    val date: LocalDate,
    val time: LocalTime? = null,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val remark: String
)
