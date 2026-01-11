package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class ExpensesViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allCategories: LiveData<List<CategoryEntity>> = repository.allCategories
    
    // LiveData for today's total expense
    private val _todayTotal = MutableLiveData<Double>()
    val todayTotal: LiveData<Double> = _todayTotal

    // LiveData for transaction status/error
    private val _transactionError = MutableLiveData<String?>()
    val transactionError: LiveData<String?> = _transactionError

    fun addTransaction(title: String, amount: Double, mode: String, category: String, timestamp: Long, type: String, message: String?, allowNegativeBalance: Boolean) {
        viewModelScope.launch {
            // Check balance for expense if needed (Read-only check, safe to do outside transaction for user feedback)
            if (type == "EXPENSE" && !allowNegativeBalance) {
                val balance = repository.getBalance(mode)
                val currentAmount = balance?.amount ?: 0.0
                if (currentAmount - amount < 0) {
                     _transactionError.postValue("Insufficient balance in $mode account!")
                     return@launch
                }
            }

            // Create Transaction Object (balance_after will be updated in DAO)
            val transaction = TransactionEntity(
                title = title,
                amount = amount,
                mode = mode,
                category = category,
                transaction_type = type,
                timestamp = timestamp,
                balance_after = 0.0, // Placeholder
                message = message
            )
            
            // Atomic Insert + Update
            try {
                repository.addTransactionWithBalance(transaction)
                // Refresh today's total
                loadTodayTotal()
                 _transactionError.postValue(null)
            } catch (e: Exception) {
                _transactionError.postValue("Error adding transaction: ${e.message}")
            }
        }
    }

    fun loadTodayTotal() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis
        
        // Use a background coroutine to fetch and update
        viewModelScope.launch {
            val total = repository.getSumByTypeAndDateRange("EXPENSE", startOfDay, endOfDay)
            // Ideally observe LiveData, but for single shot update after addTransaction this works if repository returns value. 
            // However, repository.getSumByTypeAndDateRange returns LiveData.
            // So we don't need to manually fetch here if we are observing it in the UI.
            // But if _todayTotal is manual LiveData, we need to fetch value.
            // repository.getSumByTypeAndDateRange returns LiveData<Double?>.
            // Let's rely on the LiveData pattern.
        }
    }
    
    // Changing to LiveData observe pattern
    fun getTodayTotal(start: Long, end: Long): LiveData<Double?> {
        return repository.getSumByTypeAndDateRange("EXPENSE", start, end)
    }
}
