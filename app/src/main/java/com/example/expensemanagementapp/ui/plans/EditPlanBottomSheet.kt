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
import androidx.lifecycle.ViewModelProvider
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.PlansViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

class EditPlanBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: PlansViewModel
    private var planId: Int = -1
    private var editSelectedDate: Long? = null
    private var categoryNames: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_edit_plan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        planId = arguments?.getInt("PLAN_ID") ?: return dismiss()

        val db = AppDatabase.getDatabase(requireContext().applicationContext as android.app.Application)
        val repository = ExpenseRepository(db.expenseDao(), db.budgetProfileDao(), db.goalDao(), db.recurringTransactionDao())
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[PlansViewModel::class.java]

        val etTitle = view.findViewById<TextInputEditText>(R.id.etEditPlanTitle)
        val etAmount = view.findViewById<TextInputEditText>(R.id.etEditPlanAmount)
        val actvCategory = view.findViewById<android.widget.AutoCompleteTextView>(R.id.etEditPlanCategory)
        val etDate = view.findViewById<TextInputEditText>(R.id.etEditPlanDate)
        val cbNoFixedDate = view.findViewById<android.widget.CheckBox>(R.id.cbNoFixedDate)
        val rbPay = view.findViewById<RadioButton>(R.id.rbEditPay)
        val rbReceive = view.findViewById<RadioButton>(R.id.rbEditReceive)
        val rbBank = view.findViewById<RadioButton>(R.id.rbEditPlanBank)
        val rbCash = view.findViewById<RadioButton>(R.id.rbEditPlanCash)
        val btnSave = view.findViewById<Button>(R.id.btnSaveEdit)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelEdit)

        viewModel.allCategories.observe(viewLifecycleOwner) { categories ->
             categoryNames = categories.map { it.name }
             val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
             actvCategory.setAdapter(adapter)
        }

        viewModel.pendingPlans.observe(viewLifecycleOwner) { plans ->
            val plan = plans.find { it.id == planId } ?: return@observe
            etTitle.setText(plan.title)
            etAmount.setText(plan.amount.toString())
            actvCategory.setText(plan.category)

            editSelectedDate = plan.due_date
            if (editSelectedDate != null) {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = editSelectedDate!!
                etDate.setText("${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}")
                cbNoFixedDate.isChecked = false
                etDate.isEnabled = true
            } else {
                cbNoFixedDate.isChecked = true
                etDate.isEnabled = false
                etDate.text?.clear()
            }

            if (plan.plan_type == "PAY") rbPay.isChecked = true else rbReceive.isChecked = true
            if (plan.mode == "BANK") rbBank.isChecked = true else rbCash.isChecked = true

            cbNoFixedDate.setOnCheckedChangeListener { _, isChecked ->
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
                dismiss()
            }

            btnSave.setOnClickListener {
                val newTitle = etTitle.text.toString()
                val newAmount = etAmount.text.toString().toDoubleOrNull()
                val newCategory = actvCategory.text.toString()

                if (newTitle.isBlank() || newAmount == null || newCategory.isBlank()) {
                    Toast.makeText(context, getString(R.string.error_enter_details), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (!cbNoFixedDate.isChecked && editSelectedDate == null) {
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
                dismiss()
            }
        }
    }

    companion object {
        fun newInstance(planId: Int): EditPlanBottomSheet {
            val fragment = EditPlanBottomSheet()
            val args = Bundle()
            args.putInt("PLAN_ID", planId)
            fragment.arguments = args
            return fragment
        }
    }
}
