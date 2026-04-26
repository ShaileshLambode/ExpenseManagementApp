package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_plans",
    foreignKeys = [
        ForeignKey(
            entity = BudgetProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PendingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val budget_profile_id: Int = 1,
    val title: String,
    val amount: Double,
    val mode: String, // BANK or CASH
    val category: String,
    val plan_type: String, // PAY or RECEIVE
    val due_date: Long?,
    val status: String, // PENDING or COMPLETED
    val created_at: Long,
    val is_deleted: Boolean = false,
    val deleted_at: Long? = null
)
