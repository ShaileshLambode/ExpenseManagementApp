package com.example.expensemanagementapp.ui.plans

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.viewmodel.PlansViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ManageActionsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: PlansViewModel
    private lateinit var adapter: PlansAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manage_actions_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = com.example.expensemanagementapp.data.AppDatabase.getDatabase(requireContext().applicationContext as android.app.Application)
        val repository = com.example.expensemanagementapp.repository.ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[PlansViewModel::class.java]

        val rvManageActions = view.findViewById<RecyclerView>(R.id.rvManageActions)
        adapter = PlansAdapter(
            onPlanCompleted = { plan ->
                viewModel.markPlanCompleted(plan)
            },
            onPlanClicked = { plan ->
                EditPlanBottomSheet.newInstance(plan.id)
                    .show(childFragmentManager, "EditPlanBottomSheet")
            },
            onDeleteClicked = { plan ->
                viewModel.deletePlan(plan)
            }
        )
        rvManageActions.layoutManager = LinearLayoutManager(requireContext())
        rvManageActions.adapter = adapter

        viewModel.pendingPlans.observe(viewLifecycleOwner) { plans ->
            adapter.submitList(plans)
        }

        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "ManageActionsBottomSheet"
        fun newInstance() = ManageActionsBottomSheet()
    }
}
