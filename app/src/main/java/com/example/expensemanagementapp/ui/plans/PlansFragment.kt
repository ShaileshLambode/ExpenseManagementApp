package com.example.expensemanagementapp.ui.plans

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.PlansViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import java.util.Calendar

class PlansFragment : Fragment() {

    private lateinit var viewModel: PlansViewModel
    private var selectedDueDate: Long? = null
    private var categoryNames: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plans, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getDatabase(requireContext()).expenseDao()
        val repository = ExpenseRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[PlansViewModel::class.java]

        val etTitle = view.findViewById<TextInputEditText>(R.id.etPlanTitle)
        val etAmount = view.findViewById<TextInputEditText>(R.id.etPlanAmount)
        val etDate = view.findViewById<TextInputEditText>(R.id.etPlanDate)
        val switchUndefinedDate = view.findViewById<MaterialSwitch>(R.id.switchUndefinedDate)
        val rgPlanType = view.findViewById<RadioGroup>(R.id.rgPlanType)
        val rbPay = view.findViewById<RadioButton>(R.id.rbPay)
        val rgPlanMode = view.findViewById<RadioGroup>(R.id.rgPlanMode)
        val rbPlanBank = view.findViewById<RadioButton>(R.id.rbPlanBank)
        val btnAdd = view.findViewById<Button>(R.id.btnAddPlan)
        val rvPlans = view.findViewById<RecyclerView>(R.id.rvPlans)
        val actvCategory = view.findViewById<android.widget.AutoCompleteTextView>(R.id.etPlanCategory)

        // Setup Categories Observer
        viewModel.allCategories.observe(viewLifecycleOwner) { categories ->
             categoryNames = categories.map { it.name }
             val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
             actvCategory.setAdapter(adapter)
        }

        // Adapter
        val adapter = PlansAdapter(
            onPlanCompleted = { plan ->
                viewModel.markPlanCompleted(plan)
                Toast.makeText(context, getString(R.string.plan_completed), Toast.LENGTH_SHORT).show()
            },
            onPlanClicked = { plan ->
                showEditPlanDialog(plan)
            },
            onDeleteClicked = { plan ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.delete))
                    .setMessage(getString(R.string.confirm_delete_plan))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        viewModel.deletePlan(plan)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        )
        rvPlans.layoutManager = LinearLayoutManager(context)
        rvPlans.adapter = adapter

        viewModel.pendingPlans.observe(viewLifecycleOwner) { plans ->
            adapter.submitList(plans)
        }

        // Switch Logic
        switchUndefinedDate.setOnCheckedChangeListener { _, isChecked ->
            etDate.isEnabled = !isChecked
            if (isChecked) {
                etDate.text?.clear()
                selectedDueDate = null
            }
        }

        // Date Picker
        etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, day)
                    selectedDueDate = selectedCal.timeInMillis
                    etDate.setText("$day/${month + 1}/$year")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
            datePickerDialog.show()
        }

        btnAdd.setOnClickListener {
            val title = etTitle.text.toString()
            val amount = etAmount.text.toString().toDoubleOrNull()
            val category = actvCategory.text.toString()
            val isUndefinedDate = switchUndefinedDate.isChecked

            if (title.isBlank() || amount == null || category.isBlank()) {
                Toast.makeText(context, getString(R.string.error_enter_details), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!isUndefinedDate && selectedDueDate == null) {
                Toast.makeText(context, getString(R.string.error_select_date), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = if (rbPay.isChecked) "PAY" else "RECEIVE"
            val mode = if (rbPlanBank.isChecked) "BANK" else "CASH"

            viewModel.addPlan(title, amount, mode, category, type, selectedDueDate)
            
            etTitle.text?.clear()
            etAmount.text?.clear()
            actvCategory.text.clear()
            etDate.text?.clear()
            selectedDueDate = null
            switchUndefinedDate.isChecked = false
            etDate.isEnabled = true
            Toast.makeText(context, getString(R.string.plan_added), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditPlanDialog(plan: PendingPlanEntity) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_plan, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etEditPlanTitle)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etEditPlanAmount)
        val actvCategory = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.etEditPlanCategory)
        val etDate = dialogView.findViewById<TextInputEditText>(R.id.etEditPlanDate)
        val switchUndefinedDate = dialogView.findViewById<MaterialSwitch>(R.id.switchEditUndefinedDate)
        val rGroupType = dialogView.findViewById<RadioGroup>(R.id.rgEditPlanType)
        val rGroupMode = dialogView.findViewById<RadioGroup>(R.id.rgEditPlanMode)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveEdit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEdit)
        val rbPay = dialogView.findViewById<RadioButton>(R.id.rbEditPay)
        val rbReceive = dialogView.findViewById<RadioButton>(R.id.rbEditReceive)
        val rbBank = dialogView.findViewById<RadioButton>(R.id.rbEditPlanBank)
        val rbCash = dialogView.findViewById<RadioButton>(R.id.rbEditPlanCash)

        // Pre-fill data
        etTitle.setText(plan.title)
        etAmount.setText(plan.amount.toString())
        actvCategory.setText(plan.category)
        
        // Setup categories for edit dialog using pre-loaded list
        val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
        actvCategory.setAdapter(adapter)

        var editSelectedDate: Long? = plan.due_date

        if (editSelectedDate != null) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = editSelectedDate!!
            etDate.setText("${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}")
            switchUndefinedDate.isChecked = false
            etDate.isEnabled = true
        } else {
            switchUndefinedDate.isChecked = true
            etDate.isEnabled = false
            etDate.text?.clear()
        }

        if (plan.plan_type == "PAY") rbPay.isChecked = true else rbReceive.isChecked = true
        if (plan.mode == "BANK") rbBank.isChecked = true else rbCash.isChecked = true

        // Listeners
        switchUndefinedDate.setOnCheckedChangeListener { _, isChecked ->
            etDate.isEnabled = !isChecked
            if (isChecked) {
                etDate.text?.clear()
                editSelectedDate = null
            }
        }

        etDate.setOnClickListener {
             val calendar = Calendar.getInstance()
             if (editSelectedDate != null) {
                 calendar.timeInMillis = editSelectedDate!!
             }
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, day)
                    editSelectedDate = selectedCal.timeInMillis
                    etDate.setText("$day/${month + 1}/$year")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
            datePickerDialog.show()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val newTitle = etTitle.text.toString()
            val newAmount = etAmount.text.toString().toDoubleOrNull()
            val newCategory = actvCategory.text.toString()

            if (newTitle.isBlank() || newAmount == null || newCategory.isBlank()) {
                Toast.makeText(context, getString(R.string.error_enter_details), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!switchUndefinedDate.isChecked && editSelectedDate == null) {
                 Toast.makeText(context, getString(R.string.error_select_date), Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
            }

            val newType = if (rbPay.isChecked) "PAY" else "RECEIVE"
            val newMode = if (rbBank.isChecked) "BANK" else "CASH"

            val updatedPlan = plan.copy(
                title = newTitle,
                amount = newAmount,
                category = newCategory,
                plan_type = newType,
                mode = newMode,
                due_date = editSelectedDate
            )

            viewModel.updatePlan(updatedPlan)
            Toast.makeText(context, getString(R.string.plan_updated), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
