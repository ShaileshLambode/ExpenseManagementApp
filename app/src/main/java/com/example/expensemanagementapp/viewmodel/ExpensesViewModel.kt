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

    private val _insufficientBalanceError = MutableLiveData<String?>()
    val insufficientBalanceError: LiveData<String?> = _insufficientBalanceError

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

    fun addTransaction(title: String, amount: Double, mode: String, category: String, timestamp: Long, type: String, message: String?, allowNegativeBalance: Boolean = true) {
        val profileId = activeProfileId.value ?: return
        
        viewModelScope.launch {
            // Check balance for expense
            if (type == "EXPENSE" && !allowNegativeBalance) {
                val balance = repository.getBalance(mode, profileId)
                val currentAmount = balance?.amount ?: 0.0
                if (currentAmount - amount < 0) {
                     _insufficientBalanceError.postValue("Insufficient balance in $mode account!")
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

    fun getIncomeCategoryBreakdown(start: Long, end: Long): LiveData<List<com.example.expensemanagementapp.data.entity.CategorySum>> {
        return activeProfileId.switchMap { profileId: Int? ->
            if (profileId != null) {
                repository.getIncomeCategoryBreakdown(profileId, start, end)
            } else {
                MutableLiveData(emptyList())
            }
        }
    }

    fun getIncomeVsExpenseTotals(start: Long, end: Long): LiveData<List<com.example.expensemanagementapp.data.entity.CategorySum>> {
        return activeProfileId.switchMap { profileId: Int? ->
            if (profileId != null) {
                repository.getIncomeVsExpenseTotals(profileId, start, end)
            } else {
                MutableLiveData(emptyList())
            }
        }
    }

    fun getSumForAnalytics(type: String, start: Long, end: Long): LiveData<Double?> {
        return activeProfileId.switchMap { profileId: Int? ->
            if (profileId != null) {
                repository.getSumByTypeSync(profileId, type, start, end)
            } else {
                MutableLiveData(0.0)
            }
        }
    }

    fun getTrendData(start: Long, end: Long): LiveData<Triple<List<Double>, List<Double>, List<Long>>> {
        return activeProfileId.switchMap { profileId ->
            if (profileId != null) {
                repository.getTrendTransactions(profileId, start, end).switchMap { transactions ->
                    androidx.lifecycle.liveData(kotlinx.coroutines.Dispatchers.Default) {
                        val dailyExpense = java.util.TreeMap<Long, Double>()
                        val dailyIncome = java.util.TreeMap<Long, Double>()
                        
                        val calendar = java.util.Calendar.getInstance()
                        calendar.timeInMillis = start
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        calendar.set(java.util.Calendar.MINUTE, 0)
                        calendar.set(java.util.Calendar.SECOND, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
            
                        val endCalendar = java.util.Calendar.getInstance()
                        endCalendar.timeInMillis = end
                        endCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        endCalendar.set(java.util.Calendar.MINUTE, 0)
                        endCalendar.set(java.util.Calendar.SECOND, 0)
                        endCalendar.set(java.util.Calendar.MILLISECOND, 0)
                        
                        while (!calendar.after(endCalendar)) {
                            val day = calendar.timeInMillis
                            dailyExpense[day] = 0.0
                            dailyIncome[day] = 0.0
                            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
            
                        transactions.forEach {
                            calendar.timeInMillis = it.timestamp
                            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            calendar.set(java.util.Calendar.MINUTE, 0)
                            calendar.set(java.util.Calendar.SECOND, 0)
                            calendar.set(java.util.Calendar.MILLISECOND, 0)
                            val day = calendar.timeInMillis
                            
                            if (it.transaction_type == "EXPENSE") {
                                if (dailyExpense.containsKey(day)) {
                                    dailyExpense[day] = (dailyExpense[day] ?: 0.0) + it.amount
                                }
                            } else if (it.transaction_type == "INCOME") {
                                 if (dailyIncome.containsKey(day)) {
                                    dailyIncome[day] = (dailyIncome[day] ?: 0.0) + it.amount
                                }
                            }
                        }
                        emit(Triple(dailyExpense.values.toList(), dailyIncome.values.toList(), dailyExpense.keys.toList()))
                    }
                }
            } else {
                MutableLiveData(Triple(emptyList(), emptyList(), emptyList()))
            }
        }
    }
}
