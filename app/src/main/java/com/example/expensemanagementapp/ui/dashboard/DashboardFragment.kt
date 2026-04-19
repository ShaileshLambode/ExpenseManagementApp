package com.example.expensemanagementapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.DashboardViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText

class DashboardFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var legendAdapter: CategoryLegendAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
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
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]

        setupViews(view, factory)
    }

    private fun setupViews(view: View, factory: ViewModelFactory) {
        // Balances
        val tvTotalBalance: TextView = view.findViewById(R.id.tvTotalBalance)
        val tvBankBalance: TextView = view.findViewById(R.id.tvBankBalance)
        val tvCashBalance: TextView = view.findViewById(R.id.tvCashBalance)

        // Active Profile Total Balance
        viewModel.activeProfileBalance.observe(viewLifecycleOwner) { total ->
            tvTotalBalance.text = "₹ %.2f".format(total ?: 0.0)
        }

        // Active Profile Balances
        viewModel.allBalances.observe(viewLifecycleOwner) { balances ->
            var bankTotal = 0.0
            var cashTotal = 0.0
            balances.forEach {
                if (it.type == "BANK") bankTotal = it.amount
                if (it.type == "CASH") cashTotal = it.amount
            }
            tvBankBalance.text = "₹ %.2f".format(bankTotal)
            tvCashBalance.text = "₹ %.2f".format(cashTotal)
        }

        // Profile Silos
        val rvSilos: androidx.recyclerview.widget.RecyclerView = view.findViewById(R.id.rvProfileSilos)
        val siloAdapter = ProfileSiloAdapter()
        rvSilos.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        rvSilos.adapter = siloAdapter

        androidx.lifecycle.MediatorLiveData<Pair<List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity>, List<com.example.expensemanagementapp.data.entity.ProfileBalanceSum>>>().apply {
            var profiles: List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity>? = null
            var balances: List<com.example.expensemanagementapp.data.entity.ProfileBalanceSum>? = null
            
            fun update() {
                val p = profiles
                val b = balances
                if (p != null && b != null) {
                    siloAdapter.setData(p, b)
                }
            }

            addSource(viewModel.allProfiles) { profiles = it; update() }
            addSource(viewModel.allProfileBalances) { balances = it; update() }
        }.observe(viewLifecycleOwner) {}

        // Recent Activity
        val rvRecent: androidx.recyclerview.widget.RecyclerView = view.findViewById(R.id.rvRecentTransactions)
        val tvSeeAll: TextView = view.findViewById(R.id.tvSeeAll)
        
        tvSeeAll.setOnClickListener {
            findNavController().navigate(R.id.navigation_history)
        }

        val recentAdapter = RecentTransactionAdapter()
        rvRecent.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvRecent.adapter = recentAdapter

        viewModel.allProfiles.observe(viewLifecycleOwner) { profiles ->
            recentAdapter.setProfiles(profiles)
            viewModel.recentTransactions.observe(viewLifecycleOwner) { transactions ->
                recentAdapter.submitList(transactions)
            }
        }

        // Upcoming Actions
        val rvUpcomingActions: androidx.recyclerview.widget.RecyclerView = view.findViewById(R.id.rvUpcomingActions)
        val tvManageActions: TextView = view.findViewById(R.id.tvManageActions)
        val btnAddAction: Button = view.findViewById(R.id.btnAddAction)

        val plansViewModel = ViewModelProvider(requireActivity(), factory)[com.example.expensemanagementapp.viewmodel.PlansViewModel::class.java]
        val upcomingAdapter = com.example.expensemanagementapp.ui.plans.PlansAdapter(
            onPlanCompleted = { plan ->
                plansViewModel.markPlanCompleted(plan)
            },
            onPlanClicked = { plan ->
                com.example.expensemanagementapp.ui.plans.EditPlanBottomSheet.newInstance(plan.id)
                    .show(childFragmentManager, "EditPlanBottomSheet")
            },
            onDeleteClicked = { plan ->
                plansViewModel.deletePlan(plan)
            }
        )
        rvUpcomingActions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvUpcomingActions.adapter = upcomingAdapter

        viewModel.pendingPlans.observe(viewLifecycleOwner) { plans ->
            upcomingAdapter.submitList(plans.take(3)) // Show top 3 on dashboard
        }

        btnAddAction.setOnClickListener {
            com.example.expensemanagementapp.ui.add.AddActionBottomSheet.newInstance()
                .show(childFragmentManager, com.example.expensemanagementapp.ui.add.AddActionBottomSheet.TAG)
        }

        tvManageActions.setOnClickListener {
            com.example.expensemanagementapp.ui.plans.ManageActionsBottomSheet.newInstance()
                .show(childFragmentManager, com.example.expensemanagementapp.ui.plans.ManageActionsBottomSheet.TAG)
        }

        // Period Summary
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupPeriod)
        
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnRangeWeek -> viewModel.setRange("WEEK")
                    R.id.btnRangeMonth -> viewModel.setRange("MONTH")
                }
            }
        }
        
        // Donut Chart & Category Breakdown
        val donutChart = view.findViewById<com.example.expensemanagementapp.ui.custom.DonutChartView>(R.id.donutChart)
        val rvLegend: androidx.recyclerview.widget.RecyclerView = view.findViewById(R.id.rvCategoryLegend)

        donutChart.setOnCategoryClickListener { category ->
             category.category?.let { catName ->
                 val bundle = android.os.Bundle().apply {
                     putString("category_filter", catName)
                     putString("period_filter", viewModel.selectedRange.value)
                 }
                 findNavController().navigate(R.id.navigation_history, bundle)
             }
        }

        legendAdapter = CategoryLegendAdapter()
        rvLegend.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvLegend.adapter = legendAdapter

        viewModel.categoryBreakdown.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                donutChart.setData(data)
                legendAdapter.setData(data)
            }
        }
    }
    
    // Reuse the layout for initial setup but adapted for single update or simple logic
    private fun showAdjustBalanceDialog() {
         val dialogView = layoutInflater.inflate(R.layout.dialog_adjust_balance, null)
        val etInitialBank = dialogView.findViewById<TextInputEditText>(R.id.etAdjustBank)
        val etInitialCash = dialogView.findViewById<TextInputEditText>(R.id.etAdjustCash)
        val btnStartApp = dialogView.findViewById<Button>(R.id.btnConfirmAdjust)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnStartApp.setOnClickListener {
            val bankAmount = etInitialBank.text.toString().toDoubleOrNull()
            val cashAmount = etInitialCash.text.toString().toDoubleOrNull()
            
            if (bankAmount != null) viewModel.updateBalance("BANK", bankAmount)
            if (cashAmount != null) viewModel.updateBalance("CASH", cashAmount)
            
            dialog.dismiss()
        }

        dialog.show()
    }
}
