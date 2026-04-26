package com.example.expensemanagementapp.ui.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.ui.custom.DonutChartView
import com.example.expensemanagementapp.viewmodel.ExpensesViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class IncomeAnalyticsFragment : Fragment() {

    private lateinit var viewModel: ExpensesViewModel
    private lateinit var tvTotalIncome: TextView
    private lateinit var rvBreakdown: RecyclerView
    private lateinit var adapter: CategoryBreakdownAdapter
    private var currentRange = "MONTH"

    private lateinit var trendChart: com.example.expensemanagementapp.ui.custom.SparklineView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analytics_income, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvTotalIncome = view.findViewById(R.id.tvTotalIncome)
        rvBreakdown = view.findViewById(R.id.rvIncomeBreakdown)
        trendChart = view.findViewById(R.id.trendChart)
        val chipGroup: ChipGroup = view.findViewById(R.id.chipGroupTimeRange)

        // Only show income line in this fragment
        trendChart.setVisibility(showExp = false, showInc = true)

        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        val repository = ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ExpensesViewModel::class.java]

        adapter = CategoryBreakdownAdapter()
        rvBreakdown.layoutManager = LinearLayoutManager(requireContext())
        rvBreakdown.adapter = adapter

        chipGroup.check(R.id.chipMonth)
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipWeek -> updateRange("WEEK")
                R.id.chipMonth -> updateRange("MONTH")
                R.id.chipYear -> updateRange("YEAR")
            }
        }

        viewModel.allProfiles.observe(viewLifecycleOwner) { profiles ->
            val activeProfile = profiles.find { it.is_active }
            activeProfile?.let { profile ->
                try {
                    val colorInt = android.graphics.Color.parseColor(profile.color)
                    for (i in 0 until chipGroup.childCount) {
                        val chip = chipGroup.getChildAt(i) as? Chip
                        chip?.let {
                            val chipCsl = android.content.res.ColorStateList(
                                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                                intArrayOf(colorInt, android.graphics.Color.TRANSPARENT)
                            )
                            it.chipBackgroundColor = chipCsl
                            it.setTextColor(android.content.res.ColorStateList(
                                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                                intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.GRAY)
                            ))
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        updateRange("MONTH")
    }

    private fun updateRange(range: String) {
        currentRange = range
        val pair = getDateRange(range)
        
        viewModel.getIncomeCategoryBreakdown(pair.first, pair.second).observe(viewLifecycleOwner) { data ->
            val total = data.sumOf { it.total }
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            tvTotalIncome.text = currencyFormat.format(total)
            
            adapter.setTotalSpending(total)
            adapter.submitList(data)
        }

        viewModel.getTrendData(pair.first, pair.second).observe(viewLifecycleOwner) { trend ->
            trendChart.setData(trend.first, trend.second, trend.third)
        }
    }

    private fun getDateRange(range: String): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        when (range) {
            "WEEK" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "MONTH" -> cal.add(Calendar.MONTH, -1)
            "YEAR" -> cal.add(Calendar.YEAR, -1)
        }
        return Pair(cal.timeInMillis, end)
    }
}
