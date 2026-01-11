package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

class PlansViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val pendingPlans: LiveData<List<PendingPlanEntity>> = repository.pendingPlans
    val allCategories = repository.allCategories

    fun addPlan(title: String, amount: Double, mode: String, category: String, planType: String, dueDate: Long?) {
        viewModelScope.launch {
            val plan = PendingPlanEntity(
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

    fun markPlanCompleted(plan: PendingPlanEntity) {
        viewModelScope.launch {
            // 1. Update Plan Status
            repository.updatePlan(plan.copy(status = "COMPLETED"))

            // 2. Adjust Balance
            val balance = repository.getBalance(plan.mode)
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
}
