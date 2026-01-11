package com.example.expensemanagementapp.ui.history

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.HistoryViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import java.util.Calendar

class HistoryFragment : Fragment() {

    private lateinit var viewModel: HistoryViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getDatabase(requireContext()).expenseDao()
        val repository = ExpenseRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[HistoryViewModel::class.java]

        val rvHistory = view.findViewById<RecyclerView>(R.id.rvHistory)
        val btnDateFilter = view.findViewById<ImageButton>(R.id.btnDateFilter)
        val btnClearFilter = view.findViewById<ImageButton>(R.id.btnClearFilter)

        val adapter = TransactionAdapter(
            onEdit = { transaction ->
                showEditDialog(transaction)
            },
            onDelete = { transaction ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to delete this transaction?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        viewModel.deleteTransaction(transaction)
                        com.google.android.material.snackbar.Snackbar.make(
                            view,
                            "Transaction deleted",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        )
                            .setAction("UNDO") {
                                viewModel.undoDelete()
                            }
                            .show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        )
        rvHistory.layoutManager = LinearLayoutManager(context)
        rvHistory.adapter = adapter

        // Observe filtered/grouped items
        viewModel.historyItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
        }

        // Observe filter state to toggle clear button
        viewModel.filterState.observe(viewLifecycleOwner) { filter ->
            // If default filter (null dates, all types) -> hide clear button
            val isDefault = filter.startDate == null && filter.types.size == 2
            btnClearFilter.visibility = if (isDefault) View.GONE else View.VISIBLE
        }

        btnDateFilter.setOnClickListener {
            showFilterDialog()
        }

        btnClearFilter.setOnClickListener {
            viewModel.clearFilter()
        }

        val chipGroupPeriod = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupPeriod)
        
        chipGroupPeriod.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val checkedId = checkedIds[0]
            val currentTypes = viewModel.filterState.value?.types ?: listOf("EXPENSE", "INCOME")
            val currentCategory = viewModel.filterState.value?.category

            var start: Long? = null
            var end: Long? = null
            
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            when (checkedId) {
                R.id.chipToday -> {
                    start = calendar.timeInMillis
                    // End is implicitly end of that day in ViewModel logic if end is null? 
                    // No, ViewModel logic: end = if (endDate != null) getEndOfDay(endDate) else getEndOfDay(startDate)
                    // So just setting start is enough for single day?
                    // Yes.
                }
                R.id.chipWeek -> {
                    end = System.currentTimeMillis() // or end of today
                    calendar.add(Calendar.DAY_OF_YEAR, -6)
                    start = calendar.timeInMillis
                }
                R.id.chipMonth -> {
                    end = System.currentTimeMillis()
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    start = calendar.timeInMillis
                }
                R.id.chipAll -> {
                    // Start/End null
                }
            }
            
            if (checkedId == R.id.chipAll) {
                // If All, we usually clear Category too? Or just Period?
                // If the user manually clicks "All", maybe they expect Full History.
                // But navigating from Dashboard sets a view.
                // Let's preserve Category if simpler, OR clear it if "All" means "Reset".
                // "Reset" button exists separately.
                // Let's preserve Category to be safe, or just Period.
                // Actually, Chip is "Period".
                viewModel.setFilter(null, null, currentTypes, currentCategory)
            } else {
                viewModel.setFilter(start, end, currentTypes, currentCategory)
            }
        }
    
        // Handle Navigation Arguments
        val categoryArg = arguments?.getString("category_filter")
        val periodArg = arguments?.getString("period_filter")
        
        if (categoryArg != null || periodArg != null) {
            // Apply Period Arg
            if (periodArg != null) {
                when (periodArg) {
                    "TODAY" -> chipGroupPeriod.check(R.id.chipToday)
                    "WEEK" -> chipGroupPeriod.check(R.id.chipWeek)
                    "MONTH" -> chipGroupPeriod.check(R.id.chipMonth)
                    else -> chipGroupPeriod.check(R.id.chipAll)
                }
            }
            // Apply Category Arg immediately (Listener might have fired but without category if we just checked)
            // If we check() programmatically, listener fires. 
            // BUT at that point `viewModel.filterState.value?.category` might be null.
            // So we need to ensure Category is set.
            
            // Post it or force set.
            view.post {
                val currentFilter = viewModel.filterState.value
                val types = currentFilter?.types ?: listOf("EXPENSE", "INCOME")
                
                // Recalculate dates based on periodArg to be sure, or trust listener?
                // Listener fires synchronously usually.
                // But listener reads `currentCategory` from ViewModel, which is null initially.
                // So we need to update ViewModel with Category AFTER listener fires?
                // Or set it explicitly.
                
                // Let's just manually set filter here to override whatever listener did partialy.
                var start: Long? = null
                var end: Long? = null
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                if (periodArg == "TODAY") {
                    start = calendar.timeInMillis
                } else if (periodArg == "WEEK") {
                    end = System.currentTimeMillis()
                    calendar.add(Calendar.DAY_OF_YEAR, -6)
                    start = calendar.timeInMillis
                } else if (periodArg == "MONTH") {
                    end = System.currentTimeMillis()
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    start = calendar.timeInMillis
                }
                
                viewModel.setFilter(start, end, types, categoryArg)
            }
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExport).setOnClickListener {
            showExportDialog()
        }
    }

    private fun showFilterDialog() {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_history, null)
        val etStartDate =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etStartDate)
        val etEndDate =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEndDate)
        val cbExpense =
            dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbExpense)
        val cbRevenue =
            dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbRevenue)
        val btnClear = dialogView.findViewById<android.widget.Button>(R.id.btnClear)
        val btnApply = dialogView.findViewById<android.widget.Button>(R.id.btnApply)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        var startDate: Long? = null
        var endDate: Long? = null
        val calendar = Calendar.getInstance()

        // Paging Logic requires startDate mandatory if applying... but let's see current filter
        val currentFilter = viewModel.filterState.value
        if (currentFilter != null) {
            startDate = currentFilter.startDate
            endDate = currentFilter.endDate
            if (startDate != null) {
                // Formatting
                val dateFormat =
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                etStartDate.setText(dateFormat.format(java.util.Date(startDate)))
            }
            if (endDate != null) {
                val dateFormat =
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                etEndDate.setText(dateFormat.format(java.util.Date(endDate)))
            }
            cbExpense.isChecked = currentFilter.types.contains("EXPENSE")
            cbRevenue.isChecked = currentFilter.types.contains("INCOME")
        }

        etStartDate.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, day)
                    startDate = selectedCal.timeInMillis
                    val dateFormat =
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    etStartDate.setText(dateFormat.format(selectedCal.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        etEndDate.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, day)
                    endDate = selectedCal.timeInMillis
                    val dateFormat =
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    etEndDate.setText(dateFormat.format(selectedCal.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.minDate = startDate ?: System.currentTimeMillis()
            datePickerDialog.show()
        }

        btnClear.setOnClickListener {
            viewModel.clearFilter()
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            // Validation: Start Date mandatory? The prompt says "Start Date selector (mandatory)".
            // If user only wants to filter types on ALL dates, they might be annoyed. 
            // But strict requirement: "If End Date is NOT selected, show only transactions from the Start Date".
            // This implies the specific "Date Range" feature requires Start Date.
            // However, what if I check "Expenses" and leave dates empty?
            // "If neither is checked → show an empty state message".

            // I will enforce Start Date IF dates are being touched, or maybe just enforce it if they want date filtering.
            // But if they leave it empty, maybe we assume "All Dates"? 
            // The prompt says "Start Date selector (mandatory)". I'll enforce it.

            if (startDate == null) {
                // But wait, if they just want to filter types for ALL history?
                // "2. Date Range Selection ... Start Date selector (mandatory)" refers to the Date Range feature.
                // "3. Transaction Type Filters" is separate.
                // I will assume if startDate is NULL, we filter types on ALL history.
                // This is better UX.

                // However, the prompt says "Start Date selector (mandatory)".
                // I will try to support it. If startDate is null, I will NOT set a date range.
            }

            val types = mutableListOf<String>()
            if (cbExpense.isChecked) types.add("EXPENSE")
            if (cbRevenue.isChecked) types.add("INCOME")

            if (types.isEmpty()) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Select at least one type",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (startDate != null && endDate != null && endDate!! < startDate!!) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "End Date cannot be before Start Date",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Enforce Start Date if filter is applied? 
            // "Start Date selector (mandatory)"
            if (startDate == null) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Start Date is required",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            viewModel.setFilter(startDate, endDate, types)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditDialog(transaction: com.example.expensemanagementapp.data.entity.TransactionEntity) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_transaction, null)
        val etTitle =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditTitle)
        val etAmount =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditAmount)
        val rgMode = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgEditMode)
        val rbBank = dialogView.findViewById<android.widget.RadioButton>(R.id.rbEditBank)
        val rbCash = dialogView.findViewById<android.widget.RadioButton>(R.id.rbEditCash)
        val etDate =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditDate)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancelEdit)
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnSaveEdit)

        etTitle.setText(transaction.title)
        etAmount.setText(transaction.amount.toString())
        if (transaction.mode == "BANK") rbBank.isChecked = true else rbCash.isChecked = true

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = transaction.timestamp
        var selectedDate = transaction.timestamp

        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        etDate.setText(dateFormat.format(java.util.Date(selectedDate)))

        etDate.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, day)
                    selectedDate = selectedCal.timeInMillis
                    etDate.setText(dateFormat.format(selectedCal.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString()
            val amount = etAmount.text.toString().toDoubleOrNull()
            val mode = if (rbBank.isChecked) "BANK" else "CASH"

            if (title.isBlank() || amount == null) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Please fill all fields",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            viewModel.editTransaction(transaction, title, amount, mode, selectedDate)
            dialog.dismiss()
        }

        dialog.show()


    }

    private fun showExportDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_export, null)
        val rgExportType = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgExportType)
        val btnShare = dialogView.findViewById<android.widget.Button>(R.id.btnExportShare)
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnExportSave)
        val etStartDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etExportStartDate)
        val etEndDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etExportEndDate)
        val cbExpense = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbExportExpense)
        val cbRevenue = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbExportRevenue)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Default Dates: Current Month
        val calendar = Calendar.getInstance()
        var endDate = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        var startDate = calendar.timeInMillis
        
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        etStartDate.setText(dateFormat.format(java.util.Date(startDate)))
        etEndDate.setText(dateFormat.format(java.util.Date(endDate)))

        etStartDate.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, day)
                    startDate = selectedCal.timeInMillis
                    etStartDate.setText(dateFormat.format(selectedCal.time))
                    // Reset end date if it becomes invalid? Or just let user fix it.
                    // The prompt says "other dates before starting date must be disabled" in END DATE picker.
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        etEndDate.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, day)
                    endDate = selectedCal.timeInMillis
                    etEndDate.setText(dateFormat.format(selectedCal.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            // Disable dates before startDate
            datePickerDialog.datePicker.minDate = startDate
            datePickerDialog.show()
        }

        fun executeExport(isSave: Boolean) {
            val selectedId = rgExportType.checkedRadioButtonId
            val isCsv = selectedId == R.id.rbCsv
            
            if (endDate < startDate) {
                android.widget.Toast.makeText(requireContext(), "End Date cannot be before Start Date", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val types = mutableListOf<String>()
            if (cbExpense.isChecked) types.add("EXPENSE")
            if (cbRevenue.isChecked) types.add("INCOME")

            if (types.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Select at least one transaction type", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val transactions = viewModel.getTransactionsForExport(startDate, endDate, types)
                if (transactions.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "No transactions found in this range", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                if (isSave) {
                    com.example.expensemanagementapp.utils.ExportUtils.exportAndSave(requireContext(), transactions, isCsv)
                } else {
                    com.example.expensemanagementapp.utils.ExportUtils.exportAndShare(requireContext(), transactions, isCsv)
                }
                dialog.dismiss()
            }
        }

        btnShare.setOnClickListener { executeExport(false) }
        btnSave.setOnClickListener { executeExport(true) }
        
        dialog.show()
    }
}