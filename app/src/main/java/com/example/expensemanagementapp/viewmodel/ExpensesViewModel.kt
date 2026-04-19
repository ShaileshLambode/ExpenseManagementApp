package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class ExpensesViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allCategories: LiveData<List<CategoryEntity>> = repository.allCategories
    
    val allProfiles: LiveData<List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity>> = repository.allProfiles
    
    private val activeProfile = allProfiles.map { profiles: List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity> ->
        profiles.find { it.is_active } ?: profiles.firstOrNull()
    }

    val activeProfileId: LiveData<Int?> = activeProfile.map { it?.id }

    // LiveData for transaction status/error
    private val _transactionError = MutableLiveData<String?>()
    val transactionError: LiveData<String?> = _transactionError

    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                repository.addTransactionWithBalance(transaction)
                _transactionError.postValue(null)
            } catch (e: Exception) {
                _transactionError.postValue("Error adding transaction: ${e.message}")
            }
        }
    }

    fun addTransaction(title: String, amount: Double, mode: String, category: String, timestamp: Long, type: String, message: String?, allowNegativeBalance: Boolean) {
        val profileId = activeProfileId.value ?: return
        
        viewModelScope.launch {
            // Check balance for expense if needed
            if (type == "EXPENSE" && !allowNegativeBalance) {
                val balance = repository.getBalance(mode, profileId)
                val currentAmount = balance?.amount ?: 0.0
                if (currentAmount - amount < 0) {
                     _transactionError.postValue("Insufficient balance in $mode account!")
                     return@launch
                }
            }

            // Create Transaction Object
            val transaction = TransactionEntity(
                budget_profile_id = profileId,
                title = title,
                amount = amount,
                mode = mode,
                category = category,
                transaction_type = type,
                timestamp = timestamp,
                balance_after = 0.0,
                message = message
            )
            
            try {
                repository.addTransactionWithBalance(transaction)
                 _transactionError.postValue(null)
            } catch (e: Exception) {
                _transactionError.postValue("Error adding transaction: ${e.message}")
            }
        }
    }
    
    fun getTodayTotal(start: Long, end: Long): LiveData<Double?> {
        return activeProfileId.switchMap { profileId: Int? ->
            if (profileId != null) {
                repository.getSumByTypeAndDateRange(profileId, "EXPENSE", start, end)
            } else {
                MutableLiveData(0.0)
            }
        }
    }

    fun getCategoryBreakdown(start: Long, end: Long): LiveData<List<com.example.expensemanagementapp.data.entity.CategorySum>> {
        return activeProfileId.switchMap { profileId: Int? ->
            if (profileId != null) {
                repository.getCategoryBreakdown(profileId, start, end)
            } else {
                MutableLiveData(emptyList())
            }
        }
    }
}
