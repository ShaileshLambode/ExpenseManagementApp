package com.example.expensemanagementapp.utils

import android.content.Context
import android.preference.PreferenceManager
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    
    private const val PREF_CURRENCY_SYMBOL = "CURRENCY_SYMBOL"
    private const val PREF_FORMAT_INDIAN = "FORMAT_INDIAN"
    
    fun getSymbol(context: Context): String {
        return com.example.expensemanagementapp.data.PreferenceManager.getInstance(context).getCurrencySymbol()
    }

    fun isIndianFormat(context: Context): Boolean {
        return com.example.expensemanagementapp.data.PreferenceManager.getInstance(context).isIndianFormat()
    }

    fun format(context: Context, amount: Double): String {
        val symbol = getSymbol(context)
        val isIndian = isIndianFormat(context)
        
        val formattedNumber = if (isIndian) {
            formatIndian(amount)
        } else {
            NumberFormat.getNumberInstance(Locale.US).format(amount)
        }
        
        return "$symbol $formattedNumber"
    }

    private fun formatIndian(amount: Double): String {
        val df = DecimalFormat("#,##,##0.00")
        // Custom logic for 1,00,000 style if DecimalFormat doesn't handle it by default for "hi_IN"
        // Java's "en_IN" usually handles it well.
        val indianFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        // We just want the number part usually, but let's see. 
        // For simplicity, let's use a robust string manipulation for exact "2,22,222" style if needed, 
        // but java.text.NumberFormat with Locale("en", "IN") is standard.
        
        // Remove symbol from standard formatter
        val formatted = indianFormat.format(amount)
        return formatted.replace("₹", "").replace("Rs.", "").trim()
    }
}
