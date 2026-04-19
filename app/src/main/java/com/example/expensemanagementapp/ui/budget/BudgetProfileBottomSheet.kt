package com.example.expensemanagementapp.ui.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.BudgetProfileViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class BudgetProfileBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: BudgetProfileViewModel
    private lateinit var adapter: BudgetProfileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_budget_profile, container, false)
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
        viewModel = ViewModelProvider(this, factory)[BudgetProfileViewModel::class.java]

        val rvProfiles: RecyclerView = view.findViewById(R.id.rvProfiles)
        val btnAddProfile: MaterialButton = view.findViewById(R.id.btnAddProfile)

        adapter = BudgetProfileAdapter(
            onProfileSelected = { profile ->
                if (!profile.is_active) {
                    viewModel.switchProfile(profile.id)
                    dismiss()
                    activity?.recreate()
                }
            },
            onDeleteRequested = { profile ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete Budget")
                    .setMessage("Are you sure you want to delete '${profile.name}'? This will delete all transactions and balances associated with this budget.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteProfile(profile)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        rvProfiles.layoutManager = LinearLayoutManager(requireContext())
        rvProfiles.adapter = adapter

        viewModel.allProfiles.observe(viewLifecycleOwner) { profiles ->
            adapter.setData(profiles)
        }

        btnAddProfile.setOnClickListener {
            showAddProfileDialog()
        }
    }

    private fun showAddProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_profile, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etProfileName)
        val etIcon = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etProfileIcon)
        val btnAdd = dialogView.findViewById<android.widget.Button>(R.id.btnConfirmAddProfile)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnAdd.setOnClickListener {
            val name = etName.text.toString()
            val icon = etIcon.text.toString().ifEmpty { "💰" }
            if (name.isNotEmpty()) {
                viewModel.addProfile(name, "#3F51B5", icon)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    companion object {
        const val TAG = "BudgetProfileBottomSheet"
        fun newInstance() = BudgetProfileBottomSheet()
    }
}
