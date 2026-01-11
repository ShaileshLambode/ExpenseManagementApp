package com.example.expensemanagementapp.repository

import androidx.lifecycle.LiveData
import com.example.expensemanagementapp.data.dao.ExpenseDao
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.data.entity.ProfileEntity

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allBalances: LiveData<List<BalanceEntity>> = expenseDao.getAllBalances()
    val recentTransactions: LiveData<List<TransactionEntity>> = expenseDao.getRecentTransactions()
    val pendingPlans: LiveData<List<PendingPlanEntity>> = expenseDao.getPendingPlans()
    val allCategories: LiveData<List<CategoryEntity>> = expenseDao.getAllCategories()

    // --- Balance Operations ---
    suspend fun getBalance(type: String): BalanceEntity? {
        return expenseDao.getBalance(type)
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

    fun getTodayTotalExpense(startOfDay: Long, endOfDay: Long): LiveData<Double?> {
        return expenseDao.getTodayTotalExpense(startOfDay, endOfDay)
    }

    val allTransactions: LiveData<List<TransactionEntity>> = expenseDao.getAllTransactions()

    fun getTransactionsByDate(start: Long, end: Long): LiveData<List<TransactionEntity>> {
        // Fallback or deprecated
        return expenseDao.getTransactionsByDateAndTypes(start, end, listOf("EXPENSE", "INCOME"))
    }

    fun getTransactionsByTypes(types: List<String>): LiveData<List<TransactionEntity>> {
        return expenseDao.getTransactionsByTypes(types)
    }

    fun getTransactionsByDateAndTypes(start: Long, end: Long, types: List<String>): LiveData<List<TransactionEntity>> {
        return expenseDao.getTransactionsByDateAndTypes(start, end, types)
    }

    suspend fun getTransactionsByDateSync(start: Long, end: Long): List<TransactionEntity> {
        return expenseDao.getTransactionsByDateSync(start, end)
    }

    suspend fun getTransactionsByDateAndTypesSync(start: Long, end: Long, types: List<String>): List<TransactionEntity> {
        return expenseDao.getTransactionsByDateAndTypesSync(start, end, types)
    }

    fun getSumByTypeAndDateRange(type: String, start: Long, end: Long): LiveData<Double?> {
        return expenseDao.getSumByTypeAndDateRange(type, start, end)
    }

    fun getCategoryBreakdown(start: Long, end: Long): LiveData<List<com.example.expensemanagementapp.data.entity.CategorySum>> {
        return expenseDao.getCategoryBreakdown(start, end)
    }

    fun getTrendTransactions(start: Long, end: Long): LiveData<List<TransactionEntity>> {
        return expenseDao.getTrendTransactions(start, end)
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

    suspend fun deleteTransactionWithBalance(transaction: TransactionEntity) {
        expenseDao.deleteTransactionWithBalanceUpdate(transaction)
    }
}
