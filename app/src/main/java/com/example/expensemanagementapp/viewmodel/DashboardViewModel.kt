package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategorySum
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardViewModel(private val repository: ExpenseRepository) : ViewModel() {
    val allBalances: LiveData<List<BalanceEntity>> = repository.allBalances

    private val _selectedRange = MutableLiveData("MONTH") // WEEK or MONTH
    val selectedRange: LiveData<String> = _selectedRange

    // Today's Expense
    val todayExpense: LiveData<Double?> = repository.recentTransactions.switchMap { _ ->
        // Trigger generic update, using a new query based on current time.
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        repository.getTodayTotalExpense(start, end)
    }
    
    // Summary Data (Total Expense, Income for selected range)
    val periodSummary: LiveData<Pair<Double, Double>> = _selectedRange.switchMap { range ->
        val (start, end) = getDateRange(range)
        val expense = repository.getSumByTypeAndDateRange("EXPENSE", start, end)
        val income = repository.getSumByTypeAndDateRange("INCOME", start, end)
        
        val result = MediatorLiveData<Pair<Double, Double>>()
        var lastExpense = 0.0
        var lastIncome = 0.0
        
        result.addSource(expense) { e ->
            lastExpense = e ?: 0.0
            result.value = Pair(lastExpense, lastIncome)
        }
        result.addSource(income) { i ->
            lastIncome = i ?: 0.0
            result.value = Pair(lastExpense, lastIncome)
        }
        result
    }

    // Category Breakdown for selected range
    val categoryBreakdown: LiveData<List<CategorySum>> = _selectedRange.switchMap { range ->
        val (start, end) = getDateRange(range)
        repository.getCategoryBreakdown(start, end)
    }

    // Trend Data
    // Trend Data
    val trendData: LiveData<Pair<List<Double>, List<Double>>> = _selectedRange.switchMap { range ->
        val (start, end) = getDateRange(range)
        repository.getTrendTransactions(start, end).switchMap { transactions ->
            androidx.lifecycle.liveData(kotlinx.coroutines.Dispatchers.Default) {
                // Pre-fill dates with 0.0 to ensure continuous line
                val dailyExpense = java.util.TreeMap<Long, Double>()
                val dailyIncome = java.util.TreeMap<Long, Double>()
                
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = start
                
                // Normalize start explicitly
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
    
                val endCalendar = Calendar.getInstance()
                endCalendar.timeInMillis = end
                // We want to include the current day equivalent of 'end'
                endCalendar.set(Calendar.HOUR_OF_DAY, 0)
                endCalendar.set(Calendar.MINUTE, 0)
                endCalendar.set(Calendar.SECOND, 0)
                endCalendar.set(Calendar.MILLISECOND, 0)
                
                // Avoid infinite loop if dates are messed up
                if (calendar.after(endCalendar)) {
                    // unexpected, just emit empty
                    emit(Pair(emptyList(), emptyList()))
                    return@liveData
                }

                while (!calendar.after(endCalendar)) {
                    val day = calendar.timeInMillis
                    dailyExpense[day] = 0.0
                    dailyIncome[day] = 0.0
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
    
                // Fill with actual data
                transactions.forEach {
                    calendar.timeInMillis = it.timestamp
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
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
                
                emit(Pair(dailyExpense.values.toList(), dailyIncome.values.toList()))
            }
        }
    }

    // Insights


    fun setRange(range: String) {
        _selectedRange.value = range
    }

    fun updateBalance(type: String, newAmount: Double) {
        viewModelScope.launch {
            val balance = repository.getBalance(type)
            if (balance != null) {
                repository.updateBalance(balance.copy(amount = newAmount, last_updated = System.currentTimeMillis()))
            } else {
                repository.insertBalance(BalanceEntity(type = type, amount = newAmount, last_updated = System.currentTimeMillis()))
            }
        }
    }

    private fun getDateRange(range: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis // Now
        
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when (range) {
            "TODAY" -> {
                // Start is already 00:00 of today due to set calls above
            }
            "WEEK" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6) // Last 7 days including today
            }
            "MONTH" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return Pair(calendar.timeInMillis, end)
    }
}
