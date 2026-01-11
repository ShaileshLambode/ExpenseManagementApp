package com.example.expensemanagementapp.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.ExpensesViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText

class ExpensesFragment : Fragment() {

    private lateinit var viewModel: ExpensesViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_expenses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getDatabase(requireContext()).expenseDao()
        val repository = ExpenseRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ExpensesViewModel::class.java]

        val etTitle = view.findViewById<TextInputEditText>(R.id.etExpenseTitle)
        val etAmount = view.findViewById<TextInputEditText>(R.id.etExpenseAmount)
        val btnQuick10 = view.findViewById<Button>(R.id.btnQuick10)
        val btnQuick20 = view.findViewById<Button>(R.id.btnQuick20)
        val btnQuick50 = view.findViewById<Button>(R.id.btnQuick50)
        val rgMode = view.findViewById<RadioGroup>(R.id.rgMode)
        val rbBank = view.findViewById<RadioButton>(R.id.rbBank)
        val rbCash = view.findViewById<RadioButton>(R.id.rbCash)
        val actvCategory = view.findViewById<AutoCompleteTextView>(R.id.actvCategory)
        val btnSave = view.findViewById<Button>(R.id.btnSaveExpense)
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupType)
        val etMessage = view.findViewById<TextInputEditText>(R.id.etExpenseMessage)

        // Setup Categories
        viewModel.allCategories.observe(viewLifecycleOwner) { categories ->
            val categoryNames = categories.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
            actvCategory.setAdapter(adapter)
        }

        // Quick buttons
        val addAmount = { value: Double ->
            val current = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val newAmount = current + value
            if (newAmount % 1.0 == 0.0) {
                etAmount.setText(newAmount.toInt().toString())
            } else {
                etAmount.setText(String.format("%.2f", newAmount))
            }
        }
        
        btnQuick10.setOnClickListener { addAmount(10.0) }
        btnQuick20.setOnClickListener { addAmount(20.0) }
        btnQuick50.setOnClickListener { addAmount(50.0) }
        
        // Toggle Listener
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnTypeRevenue) {
                    btnSave.text = getString(R.string.save_revenue)
                    btnSave.setBackgroundColor(requireContext().getColor(R.color.income_green))
                } else {
                    btnSave.text = getString(R.string.save_expense)
                    btnSave.setBackgroundColor(requireContext().getColor(R.color.expense_red))
                }
            }
        }
        
        // Set initial state
        btnSave.text = getString(R.string.save_expense)
        btnSave.setBackgroundColor(requireContext().getColor(R.color.expense_red))

        btnSave.setOnClickListener {
            val title = etTitle.text.toString()
            val amount = etAmount.text.toString().toDoubleOrNull()
            val category = actvCategory.text.toString()
            val isBank = rbBank.isChecked
            val message = etMessage.text.toString().takeIf { it.isNotBlank() }

            if (title.isBlank() || amount == null || category.isBlank()) {
                Toast.makeText(context, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val isRevenue = toggleGroup.checkedButtonId == R.id.btnTypeRevenue
            val type = if (isRevenue) "INCOME" else "EXPENSE"
            val mode = if (isBank) "BANK" else "CASH"
            
            val prefs = com.example.expensemanagementapp.data.PreferenceManager.getInstance(requireContext())
            val allowNegativeBalance = prefs.isNegativeBalanceAllowed()
            
            viewModel.addTransaction(title, amount, mode, category, System.currentTimeMillis(), type, message, allowNegativeBalance)
        }
        
        // Observe Transaction Result
        viewModel.transactionError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                // Do not clear fields if error
            } else {
                // Success (error is null)
                val isRevenue = toggleGroup.checkedButtonId == R.id.btnTypeRevenue
                val msg = if (isRevenue) getString(R.string.revenue_added) else getString(R.string.expense_added)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                
                // Clear fields
                etTitle.text?.clear()
                etAmount.text?.clear()
                actvCategory.text.clear()
                etMessage.text?.clear()
            }
        }
    }
}
