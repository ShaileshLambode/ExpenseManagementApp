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
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.databinding.FragmentAddTransactionBottomSheetBinding
import com.example.expensemanagementapp.viewmodel.ExpensesViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AddTransactionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAddTransactionBottomSheetBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ExpensesViewModel
    private lateinit var repository: com.example.expensemanagementapp.repository.ExpenseRepository
    private var currentProfileId: Int? = null
    private var amountString = "0"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTransactionBottomSheetBinding.inflate(inflater, container, false)
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
        viewModel = ViewModelProvider(requireActivity(), factory)[ExpensesViewModel::class.java]

        viewModel.activeProfileId.observe(viewLifecycleOwner) { id ->
            currentProfileId = id
            
            // Apply profile color to Save button
            viewModel.allProfiles.value?.find { it.id == id }?.let { profile ->
                try {
                    val colorInt = android.graphics.Color.parseColor(profile.color)
                    binding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
                } catch (e: Exception) {}
            }
        }

        setupKeypad()
        setupSpinners()
        
        binding.btnSave.setOnClickListener {
            saveTransaction()
        }

        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }

        binding.toggleGroupType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnIncome) {
                    binding.layoutCategory.visibility = View.GONE
                } else {
                    binding.layoutCategory.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showAddCategoryDialog() {
        val context = requireContext()
        val linearLayout = android.widget.LinearLayout(context)
        linearLayout.orientation = android.widget.LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        linearLayout.setPadding(padding, padding, padding, padding)

        val etEmoji = android.widget.EditText(context)
        etEmoji.hint = "Emoji (e.g. 🍔)"
        etEmoji.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val etName = android.widget.EditText(context)
        etName.hint = "Category Name"
        etName.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        linearLayout.addView(etEmoji)
        linearLayout.addView(etName)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("New Category")
            .setView(linearLayout)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString()
                val emoji = etEmoji.text.toString()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        repository.insertCategory(com.example.expensemanagementapp.data.entity.CategoryEntity(
                            name = name,
                            icon = emoji.ifBlank { "📁" }
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
            && root.id != R.id.btnExpense && root.id != R.id.btnIncome) {
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
            val displayList = categories.map { it.name }.distinct().toMutableList()
            displayList.add("Edit Categories...")
            
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayList)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCategory.adapter = adapter
            
            binding.spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (displayList[position] == "Edit Categories...") {
                        showManageCategoriesDialog(categories)
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

    private fun showManageCategoriesDialog(categories: List<com.example.expensemanagementapp.data.entity.CategoryEntity>) {
        val context = requireContext()
        val scrollView = android.widget.ScrollView(context)
        val linearLayout = android.widget.LinearLayout(context)
        linearLayout.orientation = android.widget.LinearLayout.VERTICAL
        scrollView.addView(linearLayout)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Manage Categories")
            .setView(scrollView)
            .setPositiveButton("Add New") { _, _ ->
                showAddCategoryDialog()
            }
            .setNegativeButton("Close", null)
            .create()

        val inflater = android.view.LayoutInflater.from(context)
        categories.forEach { category ->
            val view = inflater.inflate(R.layout.item_manage_category, linearLayout, false)
            val tvEmoji = view.findViewById<android.widget.TextView>(R.id.tvCategoryEmoji)
            val tvName = view.findViewById<android.widget.TextView>(R.id.tvCategoryName)
            val btnEdit = view.findViewById<android.widget.ImageButton>(R.id.btnEditCategory)
            val btnDelete = view.findViewById<android.widget.ImageButton>(R.id.btnDeleteCategory)

            tvEmoji.text = category.icon ?: "📁"
            tvName.text = category.name

            btnEdit.setOnClickListener {
                dialog.dismiss()
                showEditCategoryDialog(category)
            }

            btnDelete.setOnClickListener {
                dialog.dismiss()
                com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Delete Category")
                    .setMessage("Delete '${category.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            repository.deleteCategory(category)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            linearLayout.addView(view)
        }

        dialog.show()
    }

    private fun showEditCategoryDialog(category: com.example.expensemanagementapp.data.entity.CategoryEntity) {
        val context = requireContext()
        val linearLayout = android.widget.LinearLayout(context)
        linearLayout.orientation = android.widget.LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        linearLayout.setPadding(padding, padding, padding, padding)

        val etEmoji = android.widget.EditText(context)
        etEmoji.hint = "Emoji (e.g. 🍔)"
        etEmoji.setText(category.icon ?: "")
        etEmoji.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val etName = android.widget.EditText(context)
        etName.hint = "Category Name"
        etName.setText(category.name)
        etName.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        linearLayout.addView(etEmoji)
        linearLayout.addView(etName)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Edit Category")
            .setView(linearLayout)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString()
                val newEmoji = etEmoji.text.toString()
                if (newName.isNotBlank()) {
                    lifecycleScope.launch {
                        repository.updateCategory(category.copy(
                            name = newName,
                            icon = newEmoji.ifBlank { "📁" }
                        ))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveTransaction() {
        val amount = amountString.toDoubleOrNull() ?: 0.0
        val title = binding.etTitle.text.toString()
        val type = if (binding.toggleGroupType.checkedButtonId == R.id.btnExpense) "EXPENSE" else "INCOME"
        val mode = binding.spinnerMode.selectedItem.toString()
        val category = if (type == "INCOME") "Income" else (binding.spinnerCategory.selectedItem?.toString() ?: "Others")
        
        if (amount <= 0) {
            Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        currentProfileId?.let { profileId ->
            if (type == "EXPENSE") {
                lifecycleScope.launch {
                    val balance = repository.getBalance(mode, profileId)
                    val currentAmount = balance?.amount ?: 0.0
                    if (currentAmount - amount < 0) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Insufficient Balance")
                            .setMessage("Your $mode balance is too low to complete this transaction. Please adjust your balance or choose a different account.")
                            .setPositiveButton("OK", null)
                            .show()
                        return@launch
                    }
                    val transaction = TransactionEntity(
                        budget_profile_id = profileId,
                        title = title,
                        amount = amount,
                        mode = mode,
                        category = category,
                        transaction_type = type,
                        timestamp = System.currentTimeMillis(),
                        balance_after = 0.0
                    )
                    viewModel.addTransaction(transaction)
                    dismiss()
                }
            } else {
                val transaction = TransactionEntity(
                    budget_profile_id = profileId,
                    title = title,
                    amount = amount,
                    mode = mode,
                    category = category,
                    transaction_type = type,
                    timestamp = System.currentTimeMillis(),
                    balance_after = 0.0
                )
                viewModel.addTransaction(transaction)
                dismiss()
            }
        } ?: run {
            Toast.makeText(requireContext(), "No active profile selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddTransactionBottomSheet"
        fun newInstance() = AddTransactionBottomSheet()
    }
}
