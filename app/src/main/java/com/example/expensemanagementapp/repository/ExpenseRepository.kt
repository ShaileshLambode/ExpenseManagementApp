package com.example.expensemanagementapp.repository

import androidx.lifecycle.LiveData
import com.example.expensemanagementapp.data.dao.BudgetProfileDao
import com.example.expensemanagementapp.data.dao.ExpenseDao
import com.example.expensemanagementapp.data.dao.GoalDao
import com.example.expensemanagementapp.data.dao.RecurringTransactionDao
import com.example.expensemanagementapp.data.entity.*

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val budgetProfileDao: BudgetProfileDao,
    private val goalDao: GoalDao,
    private val recurringTransactionDao: RecurringTransactionDao
) {

    fun getBalancesByProfile(profileId: Int): LiveData<List<BalanceEntity>> = expenseDao.getBalancesByProfile(profileId)
    val allBalances: LiveData<List<BalanceEntity>> = expenseDao.getAllBalances()
    
    fun getRecentTransactions(profileId: Int): LiveData<List<TransactionEntity>> = expenseDao.getRecentTransactions(profileId)
    
    fun getPendingPlans(profileId: Int): LiveData<List<PendingPlanEntity>> = expenseDao.getPendingPlans(profileId)
    
    val allCategories: LiveData<List<CategoryEntity>> = expenseDao.getAllCategories()

    // --- Budget Profile Operations ---
    val allProfiles: LiveData<List<BudgetProfileEntity>> = budgetProfileDao.getAllProfiles()
    
    suspend fun getActiveProfile(): BudgetProfileEntity? = budgetProfileDao.getActiveProfile()

    suspend fun getProfilesSync(): List<BudgetProfileEntity> = budgetProfileDao.getAllProfilesSync()
    
    suspend fun insertBudgetProfile(profile: BudgetProfileEntity): Long = budgetProfileDao.insertProfile(profile)
    
    suspend fun updateBudgetProfile(profile: BudgetProfileEntity) = budgetProfileDao.updateProfile(profile)
    
    suspend fun deleteBudgetProfile(profile: BudgetProfileEntity) = budgetProfileDao.deleteProfile(profile)
    
    suspend fun switchProfile(profileId: Int) {
        budgetProfileDao.deactivateAllProfiles()
        budgetProfileDao.activateProfile(profileId)
    }

    // --- Goal Operations ---
    fun getGoalsByProfile(profileId: Int): LiveData<List<GoalEntity>> = goalDao.getGoalsByProfile(profileId)
    
    suspend fun insertGoal(goal: GoalEntity) = goalDao.insertGoal(goal)
    
    suspend fun updateGoal(goal: GoalEntity) = goalDao.updateGoal(goal)
    
    suspend fun deleteGoal(goal: GoalEntity) = goalDao.deleteGoal(goal)

    // --- Recurring Transaction Operations ---
    fun getRecurringByProfile(profileId: Int): LiveData<List<RecurringTransactionEntity>> = recurringTransactionDao.getRecurringByProfile(profileId)
    
    suspend fun getDueRecurringTransactions(currentTime: Long) = recurringTransactionDao.getDueRecurringTransactions(currentTime)
    
    suspend fun insertRecurring(recurring: RecurringTransactionEntity) = recurringTransactionDao.insertRecurring(recurring)
    
    suspend fun updateRecurring(recurring: RecurringTransactionEntity) = recurringTransactionDao.updateRecurring(recurring)
    
    suspend fun deleteRecurring(recurring: RecurringTransactionEntity) = recurringTransactionDao.deleteRecurring(recurring)

    // --- Balance Operations ---
    suspend fun getBalance(type: String, profileId: Int): BalanceEntity? {
        return expenseDao.getBalance(type, profileId)
    }

    suspend fun insertBalance(balance: BalanceEntity) {
        expenseDao.insertBalance(balance)
    }

    suspend fun updateBalance(balance: BalanceEntity) {
        expenseDao.updateBalance(balance)
    }

    // --- Transaction Operations ---
    suspend fun insertTransaction(transaction: TransactionEntity) {
        expenseDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        expenseDao.deleteTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        expenseDao.updateTransaction(transaction)
    }

    fun getTodayTotalExpense(profileId: Int, startOfDay: Long, endOfDay: Long): LiveData<Double?> {
        return expenseDao.getTodayTotalExpense(profileId, startOfDay, endOfDay)
    }

    fun getAllTransactions(profileId: Int): LiveData<List<TransactionEntity>> = expenseDao.getAllTransactions(profileId)

    fun getTransactionsByDateAndTypes(profileId: Int, start: Long, end: Long, types: List<String>): LiveData<List<TransactionEntity>> {
        return expenseDao.getTransactionsByDateAndTypes(profileId, start, end, types)
    }

    suspend fun getTransactionsByDateSync(profileId: Int, start: Long, end: Long): List<TransactionEntity> {
        return expenseDao.getTransactionsByDateSync(profileId, start, end)
    }

    suspend fun getTransactionsByDateAndTypesSync(profileId: Int, start: Long, end: Long, types: List<String>): List<TransactionEntity> {
        return expenseDao.getTransactionsByDateAndTypesSync(profileId, start, end, types)
    }

    fun getSumByTypeAndDateRange(profileId: Int, type: String, start: Long, end: Long): LiveData<Double?> {
        return expenseDao.getSumByTypeAndDateRange(profileId, type, start, end)
    }

    fun getCategoryBreakdown(profileId: Int, start: Long, end: Long): LiveData<List<CategorySum>> {
        return expenseDao.getCategoryBreakdown(profileId, start, end)
    }

    fun getTrendTransactions(profileId: Int, start: Long, end: Long): LiveData<List<TransactionEntity>> {
        return expenseDao.getTrendTransactions(profileId, start, end)
    }

    // --- Plan Operations ---
    suspend fun insertPlan(plan: PendingPlanEntity) {
        expenseDao.insertPlan(plan)
    }

    suspend fun updatePlan(plan: PendingPlanEntity) {
        expenseDao.updatePlan(plan)
    }

    suspend fun deletePlan(plan: PendingPlanEntity) {
        expenseDao.deletePlan(plan)
    }

    // --- Category Operations ---
    suspend fun insertCategory(category: CategoryEntity) {
        expenseDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        expenseDao.deleteCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) {
        expenseDao.updateCategory(category)
    }

    suspend fun getAllCategoriesSync(): List<CategoryEntity> {
        return expenseDao.getAllCategoriesSync()
    }

    // --- Profile Operations ---
    fun getProfile(): LiveData<ProfileEntity?> {
        return expenseDao.getProfile()
    }

    suspend fun insertProfile(profile: ProfileEntity) {
        expenseDao.insertProfile(profile)
    }

    // --- Atomic Operations ---
    suspend fun addTransactionWithBalance(transaction: TransactionEntity) {
        expenseDao.addTransactionWithBalanceUpdate(transaction)
    }

    suspend fun contributeToGoal(profileId: Int, goalId: Int, amount: Double, mode: String, goalName: String) {
        expenseDao.contributeToGoal(profileId, goalId, amount, mode, goalName)
    }

    suspend fun deleteTransactionWithBalance(transaction: TransactionEntity) {
        expenseDao.deleteTransactionWithBalanceUpdate(transaction)
    }

    val totalCombinedBalance: LiveData<Double?> = expenseDao.getTotalCombinedBalance()
    
    val recentTransactionsAllProfiles: LiveData<List<TransactionEntity>> = expenseDao.getRecentTransactionsAllProfiles()
    
    val allProfileBalances: LiveData<List<com.example.expensemanagementapp.data.entity.ProfileBalanceSum>> = expenseDao.getAllProfileBalances()
}
