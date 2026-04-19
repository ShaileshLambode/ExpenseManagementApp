package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "balances",
    indices = [androidx.room.Index(value = ["budget_profile_id", "type"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = BudgetProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val budget_profile_id: Int = 1,
    val type: String, // BANK or CASH
    val amount: Double,
    val last_updated: Long
)
