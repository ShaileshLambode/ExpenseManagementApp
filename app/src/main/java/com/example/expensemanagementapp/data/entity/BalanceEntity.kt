package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "balances")
data class BalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // BANK or CASH
    val amount: Double,
    val last_updated: Long
)
