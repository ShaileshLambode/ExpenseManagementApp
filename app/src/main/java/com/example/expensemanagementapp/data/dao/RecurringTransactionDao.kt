package com.example.expensemanagementapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.expensemanagementapp.data.entity.RecurringTransactionEntity

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions WHERE budget_profile_id = :profileId ORDER BY created_at DESC")
    fun getRecurringByProfile(profileId: Int): LiveData<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE is_active = 1 AND next_due_date <= :currentTime")
    suspend fun getDueRecurringTransactions(currentTime: Long): List<RecurringTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringTransactionEntity)

    @Update
    suspend fun updateRecurring(recurring: RecurringTransactionEntity)

    @Delete
    suspend fun deleteRecurring(recurring: RecurringTransactionEntity)

    @Query("SELECT * FROM recurring_transactions")
    suspend fun getAllRecurringSync(): List<RecurringTransactionEntity>
}
