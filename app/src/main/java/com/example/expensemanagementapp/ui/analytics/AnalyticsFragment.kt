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
import com.google.android.material.chip.ChipGroup
import java.text.NumberFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private lateinit var viewModel: ExpensesViewModel
    private lateinit var donutChart: DonutChartView
    private lateinit var tvTotalSpending: TextView
    private lateinit var rvBreakdown: RecyclerView
    private lateinit var adapter: CategoryBreakdownAdapter
    
    private var currentRange = "MONTH"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        donutChart = view.findViewById(R.id.donutChart)
        tvTotalSpending = view.findViewById(R.id.tvTotalSpending)
        rvBreakdown = view.findViewById(R.id.rvCategoryBreakdown)
        val chipGroup: ChipGroup = view.findViewById(R.id.chipGroupTimeRange)

        // Setup ViewModel (Sharing with Activity if needed, but here we can use a fresh one for this fragment's scope)
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        val repository = ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ExpensesViewModel::class.java]

        // Setup RecyclerView
        adapter = CategoryBreakdownAdapter()
        rvBreakdown.layoutManager = LinearLayoutManager(requireContext())
        rvBreakdown.adapter = adapter

        // Setup Range Selection
        chipGroup.check(R.id.chipMonth)
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipWeek -> updateRange("WEEK")
                R.id.chipMonth -> updateRange("MONTH")
                R.id.chipYear -> updateRange("YEAR")
            }
        }

        updateRange("MONTH")
    }

    private fun updateRange(range: String) {
        currentRange = range
        val pair = getDateRange(range)
        
        // Remove previous observers if any (Fragment lifecycle handles this usually, but we are re-triggering)
        // Actually, we can just switchMap in ViewModel, but for simplicity here:
        viewModel.getCategoryBreakdown(pair.first, pair.second).observe(viewLifecycleOwner) { data ->
            val total = data.sumOf { it.total }
            
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            tvTotalSpending.text = currencyFormat.format(total)
            
            donutChart.setData(data)
            adapter.setTotalSpending(total)
            adapter.submitList(data)
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
        
        val start = cal.timeInMillis
        return Pair(start, end)
    }
}
