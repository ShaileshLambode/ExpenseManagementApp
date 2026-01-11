package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val mode: String, // BANK or CASH
    val category: String?,
    val transaction_type: String, // EXPENSE or INCOME
    val timestamp: Long,
    val balance_after: Double,
    val message: String? = null
)
