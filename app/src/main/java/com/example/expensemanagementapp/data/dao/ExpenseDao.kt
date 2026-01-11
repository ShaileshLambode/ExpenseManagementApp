package com.example.expensemanagementapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.data.entity.CategorySum
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.data.entity.ProfileEntity

@Dao
interface ExpenseDao {

    // --- Balances ---
    @Query("SELECT * FROM balances WHERE type = :type LIMIT 1")
    suspend fun getBalance(type: String): BalanceEntity?

    @Query("SELECT * FROM balances")
    fun getAllBalances(): LiveData<List<BalanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalance(balance: BalanceEntity)

    @Update
    suspend fun updateBalance(balance: BalanceEntity)

    // --- Transactions ---
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 15")
    fun getRecentTransactions(): LiveData<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPaged(limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT SUM(amount) FROM transactions WHERE transaction_type = 'EXPENSE' AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getTodayTotalExpense(startOfDay: Long, endOfDay: Long): LiveData<Double?>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): LiveData<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transaction_type IN (:types) ORDER BY timestamp DESC")
    fun getTransactionsByTypes(types: List<String>): LiveData<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp <= :end AND transaction_type IN (:types) ORDER BY timestamp DESC")
    fun getTransactionsByDateAndTypes(start: Long, end: Long, types: List<String>): LiveData<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE transaction_type = :type AND timestamp >= :start AND timestamp <= :end")
    fun getSumByTypeAndDateRange(type: String, start: Long, end: Long): LiveData<Double?>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE timestamp BETWEEN :start AND :end AND transaction_type = 'EXPENSE' GROUP BY category")
    fun getCategoryBreakdown(start: Long, end: Long): LiveData<List<CategorySum>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getTrendTransactions(start: Long, end: Long): LiveData<List<TransactionEntity>>

    // --- Pending Plans ---
    @Insert
    suspend fun insertPlan(plan: PendingPlanEntity)

    @Update
    suspend fun updatePlan(plan: PendingPlanEntity)

    @Query("SELECT * FROM pending_plans WHERE status = 'PENDING' ORDER BY due_date ASC")
    fun getPendingPlans(): LiveData<List<PendingPlanEntity>>

    @Delete
    suspend fun deletePlan(plan: PendingPlanEntity)

    // --- Categories ---
    @Insert
    suspend fun insertCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories")
    fun getAllCategories(): LiveData<List<CategoryEntity>>
    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateSync(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp <= :end AND transaction_type IN (:types) ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateAndTypesSync(start: Long, end: Long, types: List<String>): List<TransactionEntity>

    // --- Sync Methods for Backup ---
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileSync(): ProfileEntity?

    @Query("SELECT * FROM balances")
    suspend fun getAllBalancesSync(): List<BalanceEntity>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>

    @Query("SELECT * FROM pending_plans")
    suspend fun getAllPendingPlansSync(): List<PendingPlanEntity>

    // --- Profile ---
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): LiveData<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    // --- Atomic Operations ---
    @androidx.room.Transaction
    suspend fun addTransactionWithBalanceUpdate(transaction: TransactionEntity) {
        // 1. Calculate new balance
        val currentBalance = getBalance(transaction.mode)
        val currentAmount = currentBalance?.amount ?: 0.0
        
        var newAmount = currentAmount
        if (transaction.transaction_type == "EXPENSE") {
            newAmount -= transaction.amount
        } else if (transaction.transaction_type == "INCOME") {
            newAmount += transaction.amount
        }

        // 2. Update Balance
        val updatedBalance = currentBalance?.copy(amount = newAmount, last_updated = System.currentTimeMillis()) 
            ?: BalanceEntity(type = transaction.mode, amount = newAmount, last_updated = System.currentTimeMillis())
        
        if (currentBalance != null) {
            updateBalance(updatedBalance)
        } else {
            insertBalance(updatedBalance)
        }

        // 3. Insert Transaction (update balance_after)
        val finalTransaction = transaction.copy(balance_after = newAmount)
        insertTransaction(finalTransaction)
    }

    @androidx.room.Transaction
    suspend fun deleteTransactionWithBalanceUpdate(transaction: TransactionEntity) {
        // 1. Revert Balance
        val currentBalance = getBalance(transaction.mode)
        
        if (currentBalance != null) {
             var newAmount = currentBalance.amount
             if (transaction.transaction_type == "EXPENSE") {
                 newAmount += transaction.amount
             } else if (transaction.transaction_type == "INCOME") {
                 newAmount -= transaction.amount
             }
             
             updateBalance(currentBalance.copy(amount = newAmount, last_updated = System.currentTimeMillis()))
        }

        // 2. Delete Transaction
        deleteTransaction(transaction)
    }
}
