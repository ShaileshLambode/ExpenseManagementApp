package com.example.expensemanagementapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.expensemanagementapp.data.entity.BudgetProfileEntity

@Dao
interface BudgetProfileDao {
    @Query("SELECT * FROM budget_profiles ORDER BY created_at ASC")
    fun getAllProfiles(): LiveData<List<BudgetProfileEntity>>

    @Query("SELECT * FROM budget_profiles ORDER BY created_at ASC")
    suspend fun getAllProfilesSync(): List<BudgetProfileEntity>

    @Query("SELECT * FROM budget_profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): BudgetProfileEntity?

    @Query("SELECT * FROM budget_profiles WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveProfile(): BudgetProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BudgetProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: BudgetProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: BudgetProfileEntity)

    @Query("UPDATE budget_profiles SET is_active = 0")
    suspend fun deactivateAllProfiles()

    @Query("UPDATE budget_profiles SET is_active = 1 WHERE id = :id")
    suspend fun activateProfile(id: Int)
}
