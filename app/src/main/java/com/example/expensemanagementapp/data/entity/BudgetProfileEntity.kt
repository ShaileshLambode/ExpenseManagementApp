package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_profiles")
data class BudgetProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: String = "#3F51B5",
    val icon: String = "💰",
    val is_active: Boolean = false,
    val created_at: Long = System.currentTimeMillis(),
    val is_deleted: Boolean = false,
    val deleted_at: Long? = null
)
