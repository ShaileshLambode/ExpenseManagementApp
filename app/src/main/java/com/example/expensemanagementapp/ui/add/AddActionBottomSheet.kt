package com.example.expensemanagementapp.ui.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.databinding.FragmentAddActionBottomSheetBinding
import com.example.expensemanagementapp.viewmodel.PlansViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddActionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAddActionBottomSheetBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: PlansViewModel
    private lateinit var repository: com.example.expensemanagementapp.repository.ExpenseRepository
    private var amountString = "0"
    private var selectedDueDate: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddActionBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val db = com.example.expensemanagementapp.data.AppDatabase.getDatabase(requireContext().applicationContext as android.app.Application)
        repository = com.example.expensemanagementapp.repository.ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[PlansViewModel::class.java]

        setupKeypad()
        setupSpinners()
        
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }
        
        binding.btnSave.setOnClickListener {
            saveAction()
        }

        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Due Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        
        picker.addOnPositiveButtonClickListener { selection ->
            selectedDueDate = selection
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.btnSelectDate.text = sdf.format(Date(selection))
        }
        
        picker.show(childFragmentManager, "DATE_PICKER")
    }

    private fun showAddCategoryDialog() {
        val editText = android.widget.EditText(requireContext())
        editText.hint = "Category Name"
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext())
        container.addView(editText)
        editText.setPadding(padding, padding, padding, padding)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Category")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        repository.insertCategory(com.example.expensemanagementapp.data.entity.CategoryEntity(
                            name = name,
                            icon = "ic_other"
                        ))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupKeypad() {
        val listener = View.OnClickListener { v ->
            val text = (v as Button).text.toString()
            if (amountString == "0") {
                amountString = text
            } else {
                amountString += text
            }
            updateAmountDisplay()
        }

        findButtonsAndSetListener(binding.root, listener)

        binding.btnBackspace.setOnClickListener {
            if (amountString.length > 1) {
                amountString = amountString.substring(0, amountString.length - 1)
            } else {
                amountString = "0"
            }
            updateAmountDisplay()
        }
    }

    private fun findButtonsAndSetListener(root: View, listener: View.OnClickListener) {
        if (root is Button && root.id != R.id.btnSave && root.id != R.id.btnBackspace 
            && root.id != R.id.btnExpense && root.id != R.id.btnIncome && root.id != R.id.btnSelectDate) {
            root.setOnClickListener(listener)
        } else if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findButtonsAndSetListener(root.getChildAt(i), listener)
            }
        }
    }

    private fun updateAmountDisplay() {
        binding.tvAmountDisplay.text = amountString
    }

    private fun setupSpinners() {
        // Categories
        viewModel.allCategories.observe(viewLifecycleOwner) { categories ->
            val categoryNames = categories.map { it.name }.distinct().toMutableList()
            categoryNames.add("Edit Categories...")
            
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCategory.adapter = adapter
            
            binding.spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (categoryNames[position] == "Edit Categories...") {
                        // In a real scenario we might open Manage Categories here. Just reset to 0 for simplicity.
                        binding.spinnerCategory.setSelection(0)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }
        }

        // Modes
        val modes = arrayOf("BANK", "CASH")
        val modeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMode.adapter = modeAdapter
    }

    private fun saveAction() {
        val amount = amountString.toDoubleOrNull() ?: 0.0
        val title = binding.etTitle.text.toString()
        val type = if (binding.toggleGroupType.checkedButtonId == R.id.btnExpense) "PAY" else "RECEIVE"
        val mode = binding.spinnerMode.selectedItem.toString()
        val category = binding.spinnerCategory.selectedItem?.toString() ?: "Others"
        
        if (amount <= 0) {
            Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDueDate == null) {
            Toast.makeText(requireContext(), "Select a due date", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addPlan(title, amount, mode, category, type, selectedDueDate)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddActionBottomSheet"
        fun newInstance() = AddActionBottomSheet()
    }
}
