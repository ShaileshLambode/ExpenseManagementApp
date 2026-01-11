package com.example.expensemanagementapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = false)
    val id: Int = 1, // Single user profile, fixed ID
    val name: String,
    val dob: Long?, // Timestamp
    val city: String,
    val state: String,
    val mobile: String,
    val imageUri: String?, // URI string for profile image
    val gender: String? = null
)
