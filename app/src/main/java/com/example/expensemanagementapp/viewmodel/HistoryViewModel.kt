package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.ui.history.HistoryItem
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Calendar

data class HistoryFilter(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val types: List<String> = listOf("EXPENSE", "INCOME"),
    val category: String? = null
)

class HistoryViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allProfiles: LiveData<List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity>> = repository.allProfiles
    
    private val activeProfile = allProfiles.map { profiles ->
        profiles.find { it.is_active }
    }

    val activeProfileId: LiveData<Int?> = activeProfile.map { it?.id }

    private val _filterState = MutableLiveData<HistoryFilter>(HistoryFilter())
    val filterState: LiveData<HistoryFilter> = _filterState

    val filteredTransactions: LiveData<List<TransactionEntity>> = activeProfileId.switchMap { profileId ->
        _filterState.switchMap { filter ->
            if (profileId == null) return@switchMap MutableLiveData(emptyList())
            
            val source = if (filter.startDate == null) {
                repository.getAllTransactions(profileId)
            } else {
                val start = getStartOfDay(filter.startDate)
                val end = if (filter.endDate != null) getEndOfDay(filter.endDate) else getEndOfDay(filter.startDate)
                repository.getTransactionsByDateAndTypes(profileId, start, end, filter.types)
            }
            
            source.map { list ->
                if (!filter.category.isNullOrEmpty()) {
                    list.filter { it.category == filter.category }
                } else {
                    list
                }
            }
        }
    }

    val historyItems: MediatorLiveData<List<HistoryItem>> = MediatorLiveData()

    init {
        historyItems.addSource(filteredTransactions) { transactions ->
            historyItems.value = groupTransactionsByDate(transactions)
        }
    }

    fun setFilter(startDate: Long?, endDate: Long?, types: List<String>, category: String? = null) {
        _filterState.value = HistoryFilter(startDate, endDate, types, category)
    }

    fun clearFilter() {
        _filterState.value = HistoryFilter()
    }

    private fun groupTransactionsByDate(transactions: List<TransactionEntity>): List<HistoryItem> {
        val groupedList = mutableListOf<HistoryItem>()
        val calendar = Calendar.getInstance()
        var lastDate: Long = -1

        for (transaction in transactions) {
            calendar.timeInMillis = transaction.timestamp
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val currentDate = calendar.timeInMillis

            if (currentDate != lastDate) {
                groupedList.add(HistoryItem.DateHeader(currentDate))
                lastDate = currentDate
            }
            groupedList.add(HistoryItem.TransactionItem(transaction))
        }
        return groupedList
    }

    private fun getStartOfDay(date: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDay(date: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
    private var lastDeletedTransaction: TransactionEntity? = null

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransactionWithBalance(transaction)
            lastDeletedTransaction = transaction
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            lastDeletedTransaction?.let { tx ->
                repository.addTransactionWithBalance(tx)
                lastDeletedTransaction = null
            }
        }
    }

    fun editTransaction(oldTx: TransactionEntity, newTitle: String, newAmount: Double, newMode: String, newDate: Long) {
        viewModelScope.launch {
            val profileId = oldTx.budget_profile_id
            
            // 1. Revert Old Impact
            val oldBalance = repository.getBalance(oldTx.mode, profileId) ?: return@launch
            var currentOldBalanceAmount = oldBalance.amount
            
            if (oldTx.transaction_type == "EXPENSE") {
                currentOldBalanceAmount += oldTx.amount
            } else {
                currentOldBalanceAmount -= oldTx.amount
            }
            repository.updateBalance(oldBalance.copy(amount = currentOldBalanceAmount))
            
            // 2. Apply New Impact
            val newBalanceEntity = repository.getBalance(newMode, profileId) ?: return@launch
            var finalAmount = newBalanceEntity.amount
            if (oldTx.transaction_type == "EXPENSE") {
                finalAmount -= newAmount
            } else {
                finalAmount += newAmount
            }
            
            val updatedTx = oldTx.copy(
                title = newTitle,
                amount = newAmount,
                mode = newMode,
                timestamp = newDate,
                balance_after = finalAmount 
            )
            
            repository.updateBalance(newBalanceEntity.copy(amount = finalAmount, last_updated = System.currentTimeMillis()))
            repository.updateTransaction(updatedTx)
        }
    }

    suspend fun getTransactionsForExport(startDate: Long, endDate: Long, types: List<String>): List<TransactionEntity> {
        val profileId = activeProfileId.value ?: return emptyList()
        val start = getStartOfDay(startDate)
        val end = getEndOfDay(endDate)
        
        return repository.getTransactionsByDateAndTypesSync(profileId, start, end, types)
    }
}
