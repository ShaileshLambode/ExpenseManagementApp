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
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardViewModel(private val repository: ExpenseRepository) : ViewModel() {
    val allProfiles: LiveData<List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity>> = repository.allProfiles
    
    private val activeProfile = allProfiles.map { profiles ->
        profiles.find { it.is_active }
    }

    val activeProfileId: LiveData<Int?> = activeProfile.map { it?.id }

    private val _selectedRange = MutableLiveData("MONTH") // WEEK or MONTH
    val selectedRange: LiveData<String> = _selectedRange

    // Balances for active profile
    val allBalances: LiveData<List<BalanceEntity>> = activeProfileId.switchMap { profileId ->
        if (profileId != null) {
            repository.getBalancesByProfile(profileId)
        } else {
            MutableLiveData(emptyList())
        }
    }

    val activeProfileBalance: LiveData<Double> = allBalances.map { balances ->
        balances.sumOf { it.amount }
    }

    // Combined Balance (All Profiles) - Keep for internal use or analytics if needed, but UI will use activeProfileBalance
    val totalCombinedBalance: LiveData<Double?> = repository.totalCombinedBalance

    // Recent Transactions (Active Profile)
    val recentTransactions: LiveData<List<TransactionEntity>> = activeProfileId.switchMap { profileId ->
        if (profileId != null) {
            repository.getRecentTransactions(profileId)
        } else {
            MutableLiveData(emptyList())
        }
    }

    // Pending Plans (Active Profile)
    val pendingPlans: LiveData<List<com.example.expensemanagementapp.data.entity.PendingPlanEntity>> = activeProfileId.switchMap { profileId ->
        if (profileId != null) {
            repository.getPendingPlans(profileId)
        } else {
            MutableLiveData(emptyList())
        }
    }

    // All Profile Balances
    val allProfileBalances: LiveData<List<com.example.expensemanagementapp.data.entity.ProfileBalanceSum>> = repository.allProfileBalances

    // Today's Expense
    val todayExpense: LiveData<Double?> = activeProfileId.switchMap { profileId ->
        if (profileId != null) {
            val calendar = Calendar.getInstance()
            val end = calendar.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            repository.getTodayTotalExpense(profileId, start, end)
        } else {
            MutableLiveData(0.0)
        }
    }
    
    // Summary Data (Total Expense, Income for selected range)
    val periodSummary: LiveData<Pair<Double, Double>> = MediatorLiveData<Pair<Double, Double>>().apply {
        var range = _selectedRange.value ?: "MONTH"
        var profileId: Int? = null

        fun update() {
            val id = profileId
            if (id != null) {
                val (start, end) = getDateRange(range)
                val expense = repository.getSumByTypeAndDateRange(id, "EXPENSE", start, end)
                val income = repository.getSumByTypeAndDateRange(id, "INCOME", start, end)
                
                removeSource(expense)
                removeSource(income)

                addSource(expense) { e ->
                    val lastIncome = value?.second ?: 0.0
                    value = Pair(e ?: 0.0, lastIncome)
                }
                addSource(income) { i ->
                    val lastExpense = value?.first ?: 0.0
                    value = Pair(lastExpense, i ?: 0.0)
                }
            }
        }

        addSource(_selectedRange) { r ->
            range = r
            update()
        }
        addSource(activeProfileId) { id ->
            profileId = id
            update()
        }
    }

    // Category Breakdown for selected range
    val categoryBreakdown: LiveData<List<CategorySum>> = MediatorLiveData<List<CategorySum>>().apply {
        var range = _selectedRange.value ?: "MONTH"
        var profileId: Int? = null

        fun update() {
            val id = profileId
            if (id != null) {
                val (start, end) = getDateRange(range)
                val liveData = repository.getCategoryBreakdown(id, start, end)
                removeSource(liveData)
                addSource(liveData) { value = it }
            }
        }

        addSource(_selectedRange) { r ->
            range = r
            update()
        }
        addSource(activeProfileId) { id ->
            profileId = id
            update()
        }
    }

    // Trend Data
    val trendData: LiveData<Pair<List<Double>, List<Double>>> = MediatorLiveData<Pair<List<Double>, List<Double>>>().apply {
        var range = _selectedRange.value ?: "MONTH"
        var profileId: Int? = null

        fun update() {
            val id = profileId
            if (id != null) {
                val (start, end) = getDateRange(range)
                val liveData = repository.getTrendTransactions(id, start, end).switchMap { transactions ->
                    androidx.lifecycle.liveData(kotlinx.coroutines.Dispatchers.Default) {
                        val dailyExpense = java.util.TreeMap<Long, Double>()
                        val dailyIncome = java.util.TreeMap<Long, Double>()
                        
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = start
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
            
                        val endCalendar = Calendar.getInstance()
                        endCalendar.timeInMillis = end
                        endCalendar.set(Calendar.HOUR_OF_DAY, 0)
                        endCalendar.set(Calendar.MINUTE, 0)
                        endCalendar.set(Calendar.SECOND, 0)
                        endCalendar.set(Calendar.MILLISECOND, 0)
                        
                        while (!calendar.after(endCalendar)) {
                            val day = calendar.timeInMillis
                            dailyExpense[day] = 0.0
                            dailyIncome[day] = 0.0
                            calendar.add(Calendar.DAY_OF_YEAR, 1)
                        }
            
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
                removeSource(liveData)
                addSource(liveData) { value = it }
            }
        }

        addSource(_selectedRange) { r ->
            range = r
            update()
        }
        addSource(activeProfileId) { id ->
            profileId = id
            update()
        }
    }

    fun setRange(range: String) {
        _selectedRange.value = range
    }

    fun updateBalance(type: String, newAmount: Double) {
        viewModelScope.launch {
            val id = activeProfileId.value ?: return@launch
            val balance = repository.getBalance(type, id)
            if (balance != null) {
                repository.updateBalance(balance.copy(amount = newAmount, last_updated = System.currentTimeMillis()))
            } else {
                repository.insertBalance(BalanceEntity(budget_profile_id = id, type = type, amount = newAmount, last_updated = System.currentTimeMillis()))
            }
        }
    }

    private fun getDateRange(range: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when (range) {
            "TODAY" -> {}
            "WEEK" -> calendar.add(Calendar.DAY_OF_YEAR, -6)
            "MONTH" -> calendar.set(Calendar.DAY_OF_MONTH, 1)
        }
        return Pair(calendar.timeInMillis, end)
    }
}
