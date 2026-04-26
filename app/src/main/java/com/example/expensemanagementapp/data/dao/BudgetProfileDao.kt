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
    @Query("SELECT * FROM budget_profiles WHERE is_deleted = 0 ORDER BY created_at ASC")
    fun getAllProfiles(): LiveData<List<BudgetProfileEntity>>

    @Query("SELECT * FROM budget_profiles WHERE is_deleted = 0 ORDER BY created_at ASC")
    suspend fun getAllProfilesSync(): List<BudgetProfileEntity>

    @Query("SELECT * FROM budget_profiles WHERE id = :id AND is_deleted = 0")
    suspend fun getProfileById(id: Int): BudgetProfileEntity?

    @Query("SELECT * FROM budget_profiles WHERE is_active = 1 AND is_deleted = 0 LIMIT 1")
    suspend fun getActiveProfile(): BudgetProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BudgetProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: BudgetProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: BudgetProfileEntity)

    @Query("UPDATE budget_profiles SET is_active = 0")
    suspend fun deactivateAllProfiles()

    @Query("UPDATE budget_profiles SET is_active = 1 WHERE id = :id AND is_deleted = 0")
    suspend fun activateProfile(id: Int)

    @Query("UPDATE budget_profiles SET is_deleted = 1, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDeleteProfile(id: Int, deletedAt: Long)

    @Query("UPDATE budget_profiles SET is_deleted = 0, deleted_at = NULL WHERE id = :id")
    suspend fun restoreProfile(id: Int)

    @Query("DELETE FROM budget_profiles WHERE is_deleted = 1 AND deleted_at < :cutoff")
    suspend fun purgeOldDeletedProfiles(cutoff: Long)
    
    @Query("SELECT * FROM budget_profiles WHERE is_deleted = 1")
    fun getDeletedProfiles(): LiveData<List<BudgetProfileEntity>>
}
