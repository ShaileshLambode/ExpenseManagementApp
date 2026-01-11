package com.example.expensemanagementapp.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "Settings"
        private const val KEY_CURRENCY_SYMBOL = "CURRENCY_SYMBOL" // Matched SettingsFragment keys
        private const val KEY_DARK_MODE = "THEME_MODE" // Matched SettingsFragment keys (Int or Boolean? SettingsFragment uses Int...)
        private const val KEY_ALLOW_NEGATIVE_BALANCE = "ALLOW_NEGATIVE_BALANCE"
        private const val KEY_IS_INDIAN_FORMAT = "FORMAT_INDIAN"
        private const val KEY_DEFAULT_CASH = "DEFAULT_CASH"
        private const val KEY_LOW_BALANCE_NOTIF = "NOTIF_LOW_BALANCE"
        private const val KEY_LOW_BALANCE_THRESHOLD = "LOW_BALANCE_THRESHOLD"
        private const val KEY_AUTO_BACKUP = "AUTO_BACKUP"
        
        @Volatile
        private var INSTANCE: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }


    // Currency
    fun getCurrencySymbol(): String {
        return prefs.getString(KEY_CURRENCY_SYMBOL, "₹") ?: "₹"
    }

    fun setCurrencySymbol(symbol: String) {
        prefs.edit().putString(KEY_CURRENCY_SYMBOL, symbol).apply()
    }

    // Format
    fun isIndianFormat(): Boolean {
        return prefs.getBoolean(KEY_IS_INDIAN_FORMAT, true)
    }
    
    fun setIndianFormat(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_INDIAN_FORMAT, enabled).apply()
    }

    // Theme (SettingsFragment uses Int: 1=No, 2=Yes, -1=System)
    // We'll expose simple boolean for now or full int if needed by MainActivity
    fun isDarkMode(): Boolean {
        // Legacy compatibility: check Int first
        val mode = prefs.getInt(KEY_DARK_MODE, -1)
        return mode == 2 // MODE_NIGHT_YES = 2
    }

    fun setDarkMode(enabled: Boolean) {
        // 2 = Yes, 1 = No
        val mode = if (enabled) 2 else 1
        prefs.edit().putInt(KEY_DARK_MODE, mode).apply()
    }
    
    // Balance
    fun isNegativeBalanceAllowed(): Boolean {
        return prefs.getBoolean(KEY_ALLOW_NEGATIVE_BALANCE, true)
    }
    
    fun setNegativeBalanceAllowed(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_NEGATIVE_BALANCE, allowed).apply()
    }
    
    // Helpers for other settings
    fun getLowBalanceThreshold(): Float = prefs.getFloat(KEY_LOW_BALANCE_THRESHOLD, 1000f)
    
    fun setLowBalanceThreshold(amount: Float) {
        prefs.edit().putFloat(KEY_LOW_BALANCE_THRESHOLD, amount).apply()
    }

    fun isLowBalanceNotificationEnabled(): Boolean = prefs.getBoolean(KEY_LOW_BALANCE_NOTIF, false)
    
    fun setLowBalanceNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_BALANCE_NOTIF, enabled).apply()
    }

    fun isBudgetWarningEnabled(): Boolean = prefs.getBoolean("NOTIF_BUDGET_WARNING", true)

    fun setBudgetWarningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("NOTIF_BUDGET_WARNING", enabled).apply()
    }

    fun isDefaultaccountCash(): Boolean = prefs.getBoolean(KEY_DEFAULT_CASH, false)

    fun setDefaultAccountCash(isCash: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_CASH, isCash).apply()
    }

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_BACKUP, false)

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }
}
