package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allBalances: LiveData<List<BalanceEntity>> = repository.allBalances
    val userProfile = repository.getProfile()

    fun checkAndInitData() {
        viewModelScope.launch {
            // Check if categories exist, if not add default ones
            // This is a simple check, in a real app we might want more robust init logic
            // We rely on the UI to trigger the initial balance setup
        }
    }

    fun initCategories() {
        viewModelScope.launch {
             val categories = listOf(
                CategoryEntity(name = "Food", icon = "ic_food"),
                CategoryEntity(name = "Transport", icon = "ic_transport"),
                CategoryEntity(name = "Shopping", icon = "ic_shopping"),
                CategoryEntity(name = "Bills", icon = "ic_bills"),
                CategoryEntity(name = "Entertainment", icon = "ic_entertainment"),
                CategoryEntity(name = "Health", icon = "ic_health"),
                CategoryEntity(name = "Other", icon = "ic_other")
            )
            categories.forEach { repository.insertCategory(it) }
        }
    }

    fun setInitialBalance(bankAmount: Double, cashAmount: Double) {
        viewModelScope.launch {
            repository.insertBalance(BalanceEntity(type = "BANK", amount = bankAmount, last_updated = System.currentTimeMillis()))
            repository.insertBalance(BalanceEntity(type = "CASH", amount = cashAmount, last_updated = System.currentTimeMillis()))
            initCategories()
        }
    }
}
