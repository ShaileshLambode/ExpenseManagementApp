package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = BudgetProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val budget_profile_id: Int,
    val title: String,
    val amount: Double,
    val mode: String,
    val category: String,
    val transaction_type: String,
    val frequency: String, // DAILY, WEEKLY, MONTHLY, YEARLY
    val start_date: Long,
    val end_date: Long? = null,
    val next_due_date: Long,
    val message: String? = null,
    val is_active: Boolean = true,
    val created_at: Long = System.currentTimeMillis()
)
