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

        val dao = AppDatabase.getDatabase(requireContext()).expenseDao()
        val repository = ExpenseRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]

        setupViews(view)
    }

    private fun setupViews(view: View) {
        // Balances
        val tvTotalBalance: TextView = view.findViewById(R.id.tvTotalBalance)
        val tvBankBalance: TextView = view.findViewById(R.id.tvBankBalance)
        val tvCashBalance: TextView = view.findViewById(R.id.tvCashBalance)
        val btnAdjustBalance: Button = view.findViewById(R.id.btnAdjustBalance)

        viewModel.allBalances.observe(viewLifecycleOwner) { balances ->
            var bankTotal = 0.0
            var cashTotal = 0.0
            balances.forEach {
                if (it.type == "BANK") bankTotal = it.amount
                if (it.type == "CASH") cashTotal = it.amount
            }
            tvBankBalance.text = "₹ %.2f".format(bankTotal)
            tvCashBalance.text = "₹ %.2f".format(cashTotal)
            tvTotalBalance.text = "₹ %.2f".format(bankTotal + cashTotal)
        }

        btnAdjustBalance.setOnClickListener { showAdjustBalanceDialog() }

        // Today's Expense
        val tvTodayExpense: TextView = view.findViewById(R.id.tvTodayExpense)
        viewModel.todayExpense.observe(viewLifecycleOwner) { total ->
             tvTodayExpense.text = "₹ %.2f".format(total ?: 0.0)
        }

        // Period Summary
        val tvPeriodExpense: TextView = view.findViewById(R.id.tvPeriodExpense)
        val tvPeriodIncome: TextView = view.findViewById(R.id.tvPeriodIncome)

        // Set initial selection logic if needed, but XML has checkedButton set.
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupPeriod)
        
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnRangeToday -> viewModel.setRange("TODAY")
                    R.id.btnRangeWeek -> viewModel.setRange("WEEK")
                    R.id.btnRangeMonth -> viewModel.setRange("MONTH")
                }
            }
        }

        // Insights and Trend
        val sparkline = view.findViewById<com.example.expensemanagementapp.ui.custom.SparklineView>(R.id.sparklineTrend)
        val cbExpense = view.findViewById<CheckBox>(R.id.cbExpense)
        val cbIncome = view.findViewById<CheckBox>(R.id.cbIncome)

        cbExpense.setOnCheckedChangeListener { _, isChecked ->
            sparkline.setVisibility(isChecked, cbIncome.isChecked)
        }
        cbIncome.setOnCheckedChangeListener { _, isChecked ->
            sparkline.setVisibility(cbExpense.isChecked, isChecked)
        }
        viewModel.trendData.observe(viewLifecycleOwner) { (expense, income) ->
            sparkline.setData(expense, income)
            sparkline.setVisibility(cbExpense.isChecked, cbIncome.isChecked)
        }
        
        // Donut Chart & Category Breakdown
        val donutChart = view.findViewById<com.example.expensemanagementapp.ui.custom.DonutChartView>(R.id.donutChart)
        val rvLegend: androidx.recyclerview.widget.RecyclerView = view.findViewById(R.id.rvCategoryLegend)
        val tvNoData: TextView = view.findViewById(R.id.tvNoData)

        donutChart.setOnCategoryClickListener { category ->
             category.category?.let { catName ->
                 val bundle = android.os.Bundle().apply {
                     putString("category_filter", catName)
                     putString("period_filter", viewModel.selectedRange.value) // Pass current range
                 }
                 try {
                     findNavController().navigate(R.id.navigation_history, bundle)
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
        }
        
        viewModel.periodSummary.observe(viewLifecycleOwner) { (expense, income) ->
            tvPeriodExpense.text = "₹ %.2f".format(expense)
            tvPeriodIncome.text = "₹ %.2f".format(income)
        }

        legendAdapter = CategoryLegendAdapter()
        rvLegend.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvLegend.adapter = legendAdapter

        viewModel.categoryBreakdown.observe(viewLifecycleOwner) { data ->
            if (data.isNullOrEmpty()) {
                tvNoData.visibility = View.VISIBLE
                donutChart.visibility = View.GONE
                rvLegend.visibility = View.GONE
            } else {
                tvNoData.visibility = View.GONE
                donutChart.visibility = View.VISIBLE
                rvLegend.visibility = View.VISIBLE
                
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
