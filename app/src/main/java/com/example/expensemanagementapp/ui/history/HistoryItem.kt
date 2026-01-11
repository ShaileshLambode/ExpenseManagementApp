package com.example.expensemanagementapp.ui.history

import com.example.expensemanagementapp.data.entity.TransactionEntity

sealed class HistoryItem {
    data class DateHeader(val date: Long) : HistoryItem()
    data class TransactionItem(val transaction: TransactionEntity) : HistoryItem()
}
