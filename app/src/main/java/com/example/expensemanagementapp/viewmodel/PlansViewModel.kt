package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

class PlansViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allProfiles: LiveData<List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity>> = repository.allProfiles
    
    private val activeProfile = allProfiles.map { profiles: List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity> ->
        profiles.find { it.is_active }
    }

    val activeProfileId: LiveData<Int?> = activeProfile.map { it?.id }

    val pendingPlans: LiveData<List<PendingPlanEntity>> = activeProfileId.switchMap { profileId: Int? ->
        if (profileId != null) {
            repository.getPendingPlans(profileId)
        } else {
            androidx.lifecycle.MutableLiveData(emptyList())
        }
    }

    val recurringTransactions: LiveData<List<com.example.expensemanagementapp.data.entity.RecurringTransactionEntity>> = activeProfileId.switchMap { profileId ->
        if (profileId != null) {
            repository.getRecurringByProfile(profileId)
        } else {
            androidx.lifecycle.MutableLiveData(emptyList())
        }
    }

    val allCategories = repository.allCategories

    fun addPlan(title: String, amount: Double, mode: String, category: String, planType: String, dueDate: Long?) {
        val profileId = activeProfileId.value ?: return
        viewModelScope.launch {
            val plan = PendingPlanEntity(
                budget_profile_id = profileId,
                title = title,
                amount = amount,
                mode = mode,
                category = category,
                plan_type = planType,
                due_date = dueDate,
                status = "PENDING",
                created_at = System.currentTimeMillis()
            )
            repository.insertPlan(plan)
        }
    }

    fun addRecurring(title: String, amount: Double, mode: String, category: String, type: String, frequency: String, startDate: Long) {
        val profileId = activeProfileId.value ?: return
        viewModelScope.launch {
            repository.insertRecurring(com.example.expensemanagementapp.data.entity.RecurringTransactionEntity(
                budget_profile_id = profileId,
                title = title,
                amount = amount,
                mode = mode,
                category = category,
                transaction_type = if (type == "PAY") "EXPENSE" else "INCOME",
                frequency = frequency,
                start_date = startDate,
                next_due_date = startDate
            ))
        }
    }

    fun markPlanCompleted(plan: PendingPlanEntity) {
        viewModelScope.launch {
            val profileId = plan.budget_profile_id
            
            // 1. Update Plan Status
            repository.updatePlan(plan.copy(status = "COMPLETED"))

            // 2. Adjust Balance
            val balance = repository.getBalance(plan.mode, profileId)
            if (balance != null) {
                var newAmount = balance.amount
                if (plan.plan_type == "PAY") {
                    newAmount -= plan.amount
                } else {
                    newAmount += plan.amount
                }
                repository.updateBalance(balance.copy(amount = newAmount, last_updated = System.currentTimeMillis()))

                // 3. Create Transaction
                val transaction = TransactionEntity(
                    budget_profile_id = profileId,
                    title = "Plan Completed: ${plan.title}",
                    amount = plan.amount,
                    mode = plan.mode,
                    category = plan.category,
                    transaction_type = if (plan.plan_type == "PAY") "EXPENSE" else "INCOME",
                    timestamp = System.currentTimeMillis(),
                    balance_after = newAmount
                )
                repository.insertTransaction(transaction)
            }
        }
    }
    fun updatePlan(plan: PendingPlanEntity) {
        viewModelScope.launch {
            repository.updatePlan(plan)
        }
    }

    fun deletePlan(plan: PendingPlanEntity) {
        viewModelScope.launch {
            repository.deletePlan(plan)
        }
    }

    fun deleteRecurring(recurring: com.example.expensemanagementapp.data.entity.RecurringTransactionEntity) {
        viewModelScope.launch {
            repository.deleteRecurring(recurring)
        }
    }
}
