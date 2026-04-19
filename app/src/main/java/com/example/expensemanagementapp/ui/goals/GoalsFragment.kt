package com.example.expensemanagementapp.ui.goals

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.data.entity.GoalEntity
import com.example.expensemanagementapp.databinding.FragmentGoalsBinding
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.GoalsViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GoalsFragment : Fragment() {

    private var _binding: FragmentGoalsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: GoalsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        val repository = ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[GoalsViewModel::class.java]

        val adapter = GoalAdapter(
            onContribute = { goal -> showContributeDialog(goal) },
            onDelete = { goal -> showDeleteConfirm(goal) }
        )

        binding.rvGoals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGoals.adapter = adapter

        viewModel.goals.observe(viewLifecycleOwner) { goals ->
            adapter.submitList(goals)
            binding.layoutEmptyGoals.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnAddGoal.setOnClickListener { showAddGoalDialog() }
    }

    private fun showAddGoalDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_goal, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etGoalName)
        val etTarget = dialogView.findViewById<TextInputEditText>(R.id.etGoalTarget)
        val etDeadline = dialogView.findViewById<TextInputEditText>(R.id.etGoalDeadline)
        
        var selectedDeadline: Long? = null
        val calendar = Calendar.getInstance()

        etDeadline.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                calendar.set(y, m, d)
                selectedDeadline = calendar.timeInMillis
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                etDeadline.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Savings Goal")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString()
                val target = etTarget.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && target > 0) {
                    viewModel.addGoal(name, target, selectedDeadline, "🎯", "#2D52E5")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showContributeDialog(goal: GoalEntity) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_contribute, null)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etContributeAmount)
        val spinnerMode = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerContributeMode)
        
        val modes = arrayOf("BANK", "CASH")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modes)
        spinnerMode.setAdapter(adapter)
        spinnerMode.setText(modes[0], false)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Funds to ${goal.name}")
            .setView(dialogView)
            .setPositiveButton("Contribute") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val mode = spinnerMode.text.toString()
                if (amount > 0) {
                    viewModel.contribute(goal, amount, mode)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(goal: GoalEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Goal")
            .setMessage("Are you sure you want to delete '${goal.name}'?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteGoal(goal) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
