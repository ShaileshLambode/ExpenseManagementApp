package com.example.expensemanagementapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import java.util.Calendar

class RecurringTransactionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )

        val now = System.currentTimeMillis()
        val dueTransactions = db.recurringTransactionDao().getDueRecurringTransactions(now)

        for (recurring in dueTransactions) {
            // 1. Process the transaction
            val transaction = TransactionEntity(
                budget_profile_id = recurring.budget_profile_id,
                title = recurring.title,
                amount = recurring.amount,
                mode = recurring.mode,
                category = recurring.category,
                transaction_type = recurring.transaction_type,
                timestamp = recurring.next_due_date, // Record it at the due date
                balance_after = 0.0,
                message = recurring.message
            )
            repository.addTransactionWithBalance(transaction)

            // 2. Calculate next due date
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = recurring.next_due_date
            
            when (recurring.frequency) {
                "DAILY" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> calendar.add(Calendar.MONTH, 1)
                "YEARLY" -> calendar.add(Calendar.YEAR, 1)
            }

            val nextDue = calendar.timeInMillis
            
            // Check if expired
            var active = true
            if (recurring.end_date != null && nextDue > recurring.end_date) {
                active = false
            }

            db.recurringTransactionDao().updateRecurring(recurring.copy(
                next_due_date = nextDue,
                is_active = active
            ))
        }

        return Result.success()
    }
}
