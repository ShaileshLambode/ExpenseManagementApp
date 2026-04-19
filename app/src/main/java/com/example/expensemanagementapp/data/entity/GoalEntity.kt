package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = BudgetProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val budget_profile_id: Int,
    val name: String,
    val target_amount: Double,
    val current_amount: Double = 0.0,
    val deadline: Long? = null,
    val icon: String = "🎯",
    val color: String = "#3F51B5",
    val is_completed: Boolean = false,
    val created_at: Long = System.currentTimeMillis()
)
