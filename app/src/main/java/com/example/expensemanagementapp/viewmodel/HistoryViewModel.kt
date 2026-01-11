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

    private val _filterState = MutableLiveData<HistoryFilter>(HistoryFilter())
    val filterState: LiveData<HistoryFilter> = _filterState

    val filteredTransactions: LiveData<List<TransactionEntity>> = _filterState.switchMap { filter ->
        val source = if (filter.startDate == null) {
            if (filter.types.containsAll(listOf("EXPENSE", "INCOME")) && filter.types.size == 2) {
                 repository.allTransactions
            } else {
                 repository.getTransactionsByTypes(filter.types)
            }
        } else {
            val start = getStartOfDay(filter.startDate)
            val end = if (filter.endDate != null) getEndOfDay(filter.endDate) else getEndOfDay(filter.startDate)
            
            repository.getTransactionsByDateAndTypes(start, end, filter.types)
        }
        
        source.map { list ->
            if (!filter.category.isNullOrEmpty()) {
                list.filter { it.category == filter.category }
            } else {
                list
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
            // 1. Revert Old Impact
            val oldBalance = repository.getBalance(oldTx.mode) ?: return@launch
            
            // We must refetch balance because it might be the same entity
            var currentOldBalanceAmount = oldBalance.amount
            
            if (oldTx.transaction_type == "EXPENSE") {
                currentOldBalanceAmount += oldTx.amount
            } else {
                currentOldBalanceAmount -= oldTx.amount
            }
            
            // Update the old balance first
            repository.updateBalance(oldBalance.copy(amount = currentOldBalanceAmount))
            
            // 2. Apply New Impact
            // Fetch fresh balance for new mode (even if same as old mode, we just updated it so we need fresh fetch or use calculated)
            // To be safe, fetch fresh.
            val newBalanceEntity = repository.getBalance(newMode) ?: return@launch
            
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
        val start = getStartOfDay(startDate)
        val end = getEndOfDay(endDate)
        
        if (types.containsAll(listOf("EXPENSE", "INCOME")) && types.size == 2) {
             return repository.getTransactionsByDateSync(start, end)
        }
        return repository.getTransactionsByDateAndTypesSync(start, end, types)
    }
}
