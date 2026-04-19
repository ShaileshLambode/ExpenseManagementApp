package com.example.expensemanagementapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.expensemanagementapp.worker.RecurringTransactionWorker
import java.util.concurrent.TimeUnit

class ExpenseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleRecurringTransactions()
    }

    private fun scheduleRecurringTransactions() {
        val workRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(24, TimeUnit.HOURS)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RecurringTransactionWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
