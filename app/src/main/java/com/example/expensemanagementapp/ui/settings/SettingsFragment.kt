package com.example.expensemanagementapp.ui.settings

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        setupThemeOptions(view)
        setupCurrencyOptions(view)
        setupFinancialPreferences(view)
        setupNotificationOptions(view)
        setupDataActions(view)
        
        return view
    }

    private fun getPrefs() = com.example.expensemanagementapp.data.PreferenceManager.getInstance(requireContext())

    private fun setupThemeOptions(view: View) {
        val rgTheme = view.findViewById<RadioGroup>(R.id.rgTheme)
        val prefs = getPrefs()
        val isDark = prefs.isDarkMode()

        // Sync UI roughly (This simple boolean check isn't perfect for "System", but sufficient for basic toggle)
        if (isDark) {
            rgTheme.check(R.id.rbThemeDark)
        } else {
             rgTheme.check(R.id.rbThemeLight)
        }

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val isNight = checkedId == R.id.rbThemeDark
            prefs.setDarkMode(isNight)
            
            // Apply
            val mode = if (isNight) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun setupCurrencyOptions(view: View) {
        val etCurrencySymbol = view.findViewById<EditText>(R.id.etCurrencySymbol)
        val rgNumberFormat = view.findViewById<RadioGroup>(R.id.rgNumberFormat)
        val prefs = getPrefs()

        // Symbol
        etCurrencySymbol.setText(prefs.getCurrencySymbol())
        etCurrencySymbol.addTextChangedListener { text ->
             prefs.setCurrencySymbol(text.toString())
        }

        // Format
        if (prefs.isIndianFormat()) {
            rgNumberFormat.check(R.id.rbFormatIndian)
        } else {
            rgNumberFormat.check(R.id.rbFormatInternational)
        }

        rgNumberFormat.setOnCheckedChangeListener { _, checkedId ->
            prefs.setIndianFormat(checkedId == R.id.rbFormatIndian)
        }
    }

    private fun setupFinancialPreferences(view: View) {
        val rgDefaultAccount = view.findViewById<RadioGroup>(R.id.rgDefaultAccount)
        val switchNegativeBalance = view.findViewById<SwitchMaterial>(R.id.switchNegativeBalance)
        val prefs = getPrefs()

        // Default Account
        val isCashDefault = prefs.isDefaultaccountCash()
        if (isCashDefault) rgDefaultAccount.check(R.id.rbAccountCash) else rgDefaultAccount.check(R.id.rbAccountBank)

        rgDefaultAccount.setOnCheckedChangeListener { _, checkedId ->
            prefs.setDefaultAccountCash(checkedId == R.id.rbAccountCash)
        }

        // Negative Balance
        switchNegativeBalance.isChecked = prefs.isNegativeBalanceAllowed()
        switchNegativeBalance.setOnCheckedChangeListener { _, isChecked ->
            prefs.setNegativeBalanceAllowed(isChecked)
        }
    }
    
    private fun setupNotificationOptions(view: View) {
        val switchLowBalance = view.findViewById<SwitchMaterial>(R.id.switchLowBalance)
        val switchBudgetWarning = view.findViewById<SwitchMaterial>(R.id.switchBudgetWarning)
        val tilThreshold = view.findViewById<TextInputLayout>(R.id.tilLowBalanceThreshold)
        val etThreshold = view.findViewById<EditText>(R.id.etLowBalanceThreshold)
        val prefs = getPrefs()

        // Low Balance
        val lowBalanceEnabled = prefs.isLowBalanceNotificationEnabled()
        switchLowBalance.isChecked = lowBalanceEnabled
        tilThreshold.visibility = if (lowBalanceEnabled) View.VISIBLE else View.GONE
        etThreshold.setText(prefs.getLowBalanceThreshold().toString())

        switchLowBalance.setOnCheckedChangeListener { _, isChecked ->
            prefs.setLowBalanceNotificationEnabled(isChecked)
            tilThreshold.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        etThreshold.addTextChangedListener { text ->
            val value = text.toString().toFloatOrNull() ?: 0f
            prefs.setLowBalanceThreshold(value)
        }

        // Budget Warning
        switchBudgetWarning.isChecked = prefs.isBudgetWarningEnabled()
        switchBudgetWarning.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBudgetWarningEnabled(isChecked)
        }
    }

    private fun setupDataActions(view: View) {
        val switchAutoBackup = view.findViewById<SwitchMaterial>(R.id.switchAutoBackup)
        val prefs = getPrefs()

        // Auto Backup
        switchAutoBackup.isChecked = prefs.isAutoBackupEnabled()
        switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAutoBackupEnabled(isChecked)
        }

        view.findViewById<View>(R.id.btnBackup).setOnClickListener {
             androidx.navigation.Navigation.findNavController(view).navigate(R.id.navigation_backup)
        }

        view.findViewById<View>(R.id.btnExport).setOnClickListener {
             showExportDialog()
        }

        view.findViewById<View>(R.id.btnHelp).setOnClickListener {
             androidx.navigation.Navigation.findNavController(view).navigate(R.id.navigation_help)
        }

        view.findViewById<View>(R.id.btnAbout).setOnClickListener {
             androidx.navigation.Navigation.findNavController(view).navigate(R.id.navigation_about)
        }

        view.findViewById<View>(R.id.btnClearData).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.clear_all_data))
                .setMessage(getString(R.string.clear_data_warning))
                .setPositiveButton(getString(R.string.clear_and_restart)) { _, _ ->
                    clearAllData()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .setCancelable(false)
                .show()
        }
    }
    
    private fun showExportDialog() {
        val options = arrayOf("CSV", "JSON")
        var selectedFormat = 0
        
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.export_format))
            .setSingleChoiceItems(options, 0) { _, which -> selectedFormat = which }
            .setPositiveButton(getString(R.string.export)) { _, _ ->
                val isCsv = selectedFormat == 0
                chooseExportMethod(isCsv)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun chooseExportMethod(isCsv: Boolean) {
        val options = arrayOf(getString(R.string.save_to_downloads), getString(R.string.share))
        
        AlertDialog.Builder(requireContext())
             .setTitle(getString(R.string.export_method))
             .setItems(options) { _, which ->
                 val isSave = which == 0
                 performExport(isCsv, isSave)
             }
             .show()
    }

    private fun performExport(isCsv: Boolean, isSave: Boolean) {
        lifecycleScope.launch {
            val transactions = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext()).expenseDao().getAllTransactionsSync()
            }
            
            if (transactions.isEmpty()) {
                withContext(Dispatchers.Main) {
                     Toast.makeText(context, getString(R.string.no_transactions_export), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (isSave) {
                    com.example.expensemanagementapp.utils.ExportUtils.exportAndSave(requireContext(), transactions, isCsv)
                } else {
                    com.example.expensemanagementapp.utils.ExportUtils.exportAndShare(requireContext(), transactions, isCsv)
                }
            }
        }
    }

    private fun clearAllData() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                 AppDatabase.getDatabase(requireContext()).clearAllTables()
                 requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE).edit().clear().apply()
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, getString(R.string.data_cleared), Toast.LENGTH_SHORT).show()
                val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }
}

