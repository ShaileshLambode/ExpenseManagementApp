package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.*
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

sealed class RecycleBinItem {
    abstract val id: Int
    abstract val deletedAt: Long
    abstract val name: String
    abstract val typeName: String
    
    data class BudgetProfile(val entity: BudgetProfileEntity) : RecycleBinItem() {
        override val id = entity.id
        override val deletedAt = entity.deleted_at ?: 0L
        override val name = entity.name
        override val typeName = "Budget"
    }
    
    data class Transaction(val entity: TransactionEntity) : RecycleBinItem() {
        override val id = entity.id
        override val deletedAt = entity.deleted_at ?: 0L
        override val name = entity.title
        override val typeName = "Transaction"
    }
    
    data class Plan(val entity: PendingPlanEntity) : RecycleBinItem() {
        override val id = entity.id
        override val deletedAt = entity.deleted_at ?: 0L
        override val name = entity.title
        override val typeName = "Plan"
    }
    
    data class Category(val entity: CategoryEntity) : RecycleBinItem() {
        override val id = entity.id
        override val deletedAt = entity.deleted_at ?: 0L
        override val name = entity.name
        override val typeName = "Category"
    }
    
    data class Goal(val entity: GoalEntity) : RecycleBinItem() {
        override val id = entity.id
        override val deletedAt = entity.deleted_at ?: 0L
        override val name = entity.name
        override val typeName = "Goal"
    }
}

class RecycleBinViewModel(private val repository: ExpenseRepository) : ViewModel() {
    
    private val deletedProfiles = repository.deletedProfiles
    private val deletedTransactions = repository.deletedTransactions
    private val deletedPlans = repository.deletedPlans
    private val deletedCategories = repository.deletedCategories
    private val deletedGoals = repository.deletedGoals

    val allDeletedItems = MediatorLiveData<List<RecycleBinItem>>().apply {
        var profiles = emptyList<BudgetProfileEntity>()
        var transactions = emptyList<TransactionEntity>()
        var plans = emptyList<PendingPlanEntity>()
        var categories = emptyList<CategoryEntity>()
        var goals = emptyList<GoalEntity>()
        
        fun update() {
            val combined = mutableListOf<RecycleBinItem>()
            combined.addAll(profiles.map { RecycleBinItem.BudgetProfile(it) })
            combined.addAll(transactions.map { RecycleBinItem.Transaction(it) })
            combined.addAll(plans.map { RecycleBinItem.Plan(it) })
            combined.addAll(categories.map { RecycleBinItem.Category(it) })
            combined.addAll(goals.map { RecycleBinItem.Goal(it) })
            
            // Sort by deletedAt descending (newest deleted first)
            value = combined.sortedByDescending { it.deletedAt }
        }

        addSource(deletedProfiles) { profiles = it; update() }
        addSource(deletedTransactions) { transactions = it; update() }
        addSource(deletedPlans) { plans = it; update() }
        addSource(deletedCategories) { categories = it; update() }
        addSource(deletedGoals) { goals = it; update() }
    }

    fun purgeOldItems() {
        viewModelScope.launch {
            // 30 days in ms
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            repository.purgeOldDeletedProfiles(cutoff)
            repository.purgeOldDeletedTransactions(cutoff)
            repository.purgeOldDeletedPlans(cutoff)
            repository.purgeOldDeletedCategories(cutoff)
            repository.purgeOldDeletedGoals(cutoff)
        }
    }

    fun emptyBin() {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() + 10000L // Purge everything
            repository.purgeOldDeletedProfiles(cutoff)
            repository.purgeOldDeletedTransactions(cutoff)
            repository.purgeOldDeletedPlans(cutoff)
            repository.purgeOldDeletedCategories(cutoff)
            repository.purgeOldDeletedGoals(cutoff)
        }
    }

    fun restoreItem(item: RecycleBinItem) {
        viewModelScope.launch {
            when (item) {
                is RecycleBinItem.BudgetProfile -> repository.restoreBudgetProfile(item.id)
                is RecycleBinItem.Transaction -> repository.restoreTransactionWithBalance(item.entity)
                is RecycleBinItem.Plan -> repository.restorePlan(item.id)
                is RecycleBinItem.Category -> repository.restoreCategory(item.id)
                is RecycleBinItem.Goal -> repository.restoreGoal(item.id)
            }
        }
    }

    fun permanentlyDeleteItem(item: RecycleBinItem) {
        viewModelScope.launch {
            when (item) {
                is RecycleBinItem.BudgetProfile -> repository.deleteBudgetProfile(item.entity)
                is RecycleBinItem.Transaction -> repository.deleteTransaction(item.entity)
                is RecycleBinItem.Plan -> repository.deletePlan(item.entity)
                is RecycleBinItem.Category -> repository.deleteCategory(item.entity)
                is RecycleBinItem.Goal -> repository.deleteGoal(item.entity)
            }
        }
    }
}
