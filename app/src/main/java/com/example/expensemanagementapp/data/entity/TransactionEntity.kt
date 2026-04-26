package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = BudgetProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val budget_profile_id: Int = 1,
    val title: String,
    val amount: Double,
    val mode: String, // BANK or CASH
    val category: String?,
    val transaction_type: String, // EXPENSE or INCOME
    val timestamp: Long,
    val balance_after: Double,
    val message: String? = null,
    val is_deleted: Boolean = false,
    val deleted_at: Long? = null
)
