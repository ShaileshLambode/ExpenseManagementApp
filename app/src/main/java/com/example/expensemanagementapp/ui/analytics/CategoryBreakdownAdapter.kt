package com.example.expensemanagementapp.ui.analytics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.CategorySum
import java.text.NumberFormat
import java.util.*

class CategoryBreakdownAdapter : ListAdapter<CategorySum, CategoryBreakdownAdapter.ViewHolder>(DiffCallback()) {

    private var totalSpending: Double = 0.0

    fun setTotalSpending(total: Double) {
        this.totalSpending = total
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_breakdown, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, totalSpending, position)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvCategoryAmount)
        private val tvPercentage: TextView = itemView.findViewById(R.id.tvCategoryPercentage)
        private val viewColor: View = itemView.findViewById(R.id.viewCategoryColor)

        // Same colors as the DonutChart
        private val colors = listOf(
            "#F87171", "#60A5FA", "#FBBF24", "#A78BFA", "#94A3B8", "#2D52E5", "#F59E0B"
        )

        fun bind(item: CategorySum, totalSpending: Double, position: Int) {
            tvName.text = item.category ?: "Others"
            
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            tvAmount.text = currencyFormat.format(item.total)
            
            if (totalSpending > 0) {
                val percent = (item.total / totalSpending * 100).toInt()
                tvPercentage.text = "$percent% of total"
            } else {
                tvPercentage.text = "0% of total"
            }
            
            val color = android.graphics.Color.parseColor(colors[position % colors.size])
            val drawable = viewColor.background as android.graphics.drawable.GradientDrawable
            drawable.setColor(color)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CategorySum>() {
        override fun areItemsTheSame(oldItem: CategorySum, newItem: CategorySum): Boolean = oldItem.category == newItem.category
        override fun areContentsTheSame(oldItem: CategorySum, newItem: CategorySum): Boolean = oldItem == newItem
    }
}
