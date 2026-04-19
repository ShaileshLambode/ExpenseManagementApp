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
    @Query("SELECT * FROM balances WHERE type = :type AND budget_profile_id = :profileId LIMIT 1")
    suspend fun getBalance(type: String, profileId: Int): BalanceEntity?

    @Query("SELECT * FROM balances WHERE budget_profile_id = :profileId")
    fun getBalancesByProfile(profileId: Int): LiveData<List<BalanceEntity>>

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

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId ORDER BY timestamp DESC LIMIT 15")
    fun getRecentTransactions(profileId: Int): LiveData<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPaged(profileId: Int, limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT SUM(amount) FROM transactions WHERE budget_profile_id = :profileId AND transaction_type = 'EXPENSE' AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    fun getTodayTotalExpense(profileId: Int, startOfDay: Long, endOfDay: Long): LiveData<Double?>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId ORDER BY timestamp DESC")
    fun getAllTransactions(profileId: Int): LiveData<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId AND transaction_type IN (:types) ORDER BY timestamp DESC")
    fun getTransactionsByTypes(profileId: Int, types: List<String>): LiveData<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId AND timestamp >= :start AND timestamp <= :end AND transaction_type IN (:types) ORDER BY timestamp DESC")
    fun getTransactionsByDateAndTypes(profileId: Int, start: Long, end: Long, types: List<String>): LiveData<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE budget_profile_id = :profileId AND transaction_type = :type AND timestamp >= :start AND timestamp <= :end")
    fun getSumByTypeAndDateRange(profileId: Int, type: String, start: Long, end: Long): LiveData<Double?>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE budget_profile_id = :profileId AND timestamp BETWEEN :start AND :end AND transaction_type = 'EXPENSE' GROUP BY category")
    fun getCategoryBreakdown(profileId: Int, start: Long, end: Long): LiveData<List<CategorySum>>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getTrendTransactions(profileId: Int, start: Long, end: Long): LiveData<List<TransactionEntity>>

    // --- Pending Plans ---
    @Insert
    suspend fun insertPlan(plan: PendingPlanEntity)

    @Update
    suspend fun updatePlan(plan: PendingPlanEntity)

    @Query("SELECT * FROM pending_plans WHERE budget_profile_id = :profileId AND status = 'PENDING' ORDER BY due_date ASC")
    fun getPendingPlans(profileId: Int): LiveData<List<PendingPlanEntity>>

    @Delete
    suspend fun deletePlan(plan: PendingPlanEntity)

    // --- Categories ---
    @Insert
    suspend fun insertCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories")
    fun getAllCategories(): LiveData<List<CategoryEntity>>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId AND timestamp >= :start AND timestamp <= :end ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateSync(profileId: Int, start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId AND timestamp >= :start AND timestamp <= :end AND transaction_type IN (:types) ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateAndTypesSync(profileId: Int, start: Long, end: Long, types: List<String>): List<TransactionEntity>

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
        val currentBalance = getBalance(transaction.mode, transaction.budget_profile_id)
        val currentAmount = currentBalance?.amount ?: 0.0
        
        var newAmount = currentAmount
        if (transaction.transaction_type == "EXPENSE") {
            newAmount -= transaction.amount
        } else if (transaction.transaction_type == "INCOME") {
            newAmount += transaction.amount
        }

        // 2. Update Balance
        val updatedBalance = currentBalance?.copy(amount = newAmount, last_updated = System.currentTimeMillis()) 
            ?: BalanceEntity(
                budget_profile_id = transaction.budget_profile_id,
                type = transaction.mode, 
                amount = newAmount, 
                last_updated = System.currentTimeMillis()
            )
        
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
    suspend fun contributeToGoal(profileId: Int, goalId: Int, amount: Double, mode: String, goalName: String) {
        // 1. Update Balance (Deduct from Bank/Cash)
        val currentBalance = getBalance(mode, profileId)
        val newBalanceAmount = (currentBalance?.amount ?: 0.0) - amount
        
        val updatedBalance = currentBalance?.copy(amount = newBalanceAmount, last_updated = System.currentTimeMillis())
            ?: BalanceEntity(budget_profile_id = profileId, type = mode, amount = newBalanceAmount, last_updated = System.currentTimeMillis())
        
        if (currentBalance != null) updateBalance(updatedBalance) else insertBalance(updatedBalance)
        
        // 2. Update Goal Current Amount
        updateGoalAmount(goalId, amount)
        
        // 3. Insert Transaction as Expense
        insertTransaction(TransactionEntity(
            budget_profile_id = profileId,
            title = "Goals: $goalName",
            amount = amount,
            mode = mode,
            category = "Goals",
            transaction_type = "EXPENSE",
            timestamp = System.currentTimeMillis(),
            balance_after = newBalanceAmount
        ))
    }

    @Query("UPDATE goals SET current_amount = current_amount + :amount WHERE id = :goalId")
    suspend fun updateGoalAmount(goalId: Int, amount: Double)

    @Query("SELECT SUM(amount) FROM balances")
    fun getTotalCombinedBalance(): LiveData<Double?>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 5")
    fun getRecentTransactionsAllProfiles(): LiveData<List<TransactionEntity>>

    @Query("SELECT budget_profile_id, SUM(amount) as total FROM balances GROUP BY budget_profile_id")
    fun getAllProfileBalances(): LiveData<List<com.example.expensemanagementapp.data.entity.ProfileBalanceSum>>

    @androidx.room.Transaction
    suspend fun deleteTransactionWithBalanceUpdate(transaction: TransactionEntity) {
        // 1. Revert Balance
        val currentBalance = getBalance(transaction.mode, transaction.budget_profile_id)
        
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
