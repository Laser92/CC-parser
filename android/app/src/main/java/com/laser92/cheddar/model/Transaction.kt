package com.laser92.cheddar.model

import java.time.LocalDateTime

enum class TransactionType { CREDIT, DEBIT }

data class Transaction(
    val date: LocalDateTime,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val remark: String
)
