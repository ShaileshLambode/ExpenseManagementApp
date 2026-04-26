package com.example.expensemanagementapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.expensemanagementapp.data.entity.GoalEntity

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE budget_profile_id = :profileId AND is_deleted = 0 ORDER BY created_at DESC")
    fun getGoalsByProfile(profileId: Int): LiveData<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id AND is_deleted = 0")
    suspend fun getGoalById(id: Int): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Delete
    suspend fun deleteGoal(goal: GoalEntity)

    @Query("UPDATE goals SET is_deleted = 1, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDeleteGoal(id: Int, deletedAt: Long)

    @Query("UPDATE goals SET is_deleted = 0, deleted_at = NULL WHERE id = :id")
    suspend fun restoreGoal(id: Int)

    @Query("DELETE FROM goals WHERE is_deleted = 1 AND deleted_at < :cutoff")
    suspend fun purgeOldDeletedGoals(cutoff: Long)
    
    @Query("SELECT * FROM goals WHERE is_deleted = 1")
    fun getDeletedGoals(): LiveData<List<GoalEntity>>

    @Query("SELECT * FROM goals")
    suspend fun getAllGoalsSync(): List<GoalEntity>
}
