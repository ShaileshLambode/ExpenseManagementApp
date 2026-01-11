package com.example.expensemanagementapp.ui.dashboard

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.CategorySum

class CategoryLegendAdapter : RecyclerView.Adapter<CategoryLegendAdapter.ViewHolder>() {

    private var data: List<CategorySum> = emptyList()
    private val colors = listOf(
        "#4285F4", "#DB4437", "#F4B400", "#0F9D58", "#AB47BC",
        "#00ACC1", "#FF7043", "#9E9E9E", "#5C6BC0", "#26A69A"
    )

    fun setData(newData: List<CategorySum>) {
        data = newData
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorIndicator: View = view.findViewById(R.id.viewColorIndicator)
        val tvCategory: TextView = view.findViewById(R.id.tvCategoryName)
        val tvAmount: TextView = view.findViewById(R.id.tvCategoryAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_legend, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        val color = android.graphics.Color.parseColor(colors[position % colors.size])
        
        val background = GradientDrawable()
        background.shape = GradientDrawable.OVAL
        background.setColor(color)
        holder.colorIndicator.background = background

        holder.tvCategory.text = item.category ?: "Uncategorized"
        
        // Fix 1: Category Name uses high-contrast light color
        // Already handled by ?android:attr/textColorPrimary in XML, matches "near-white" in dark mode.
        
        holder.tvAmount.text = "%.2f".format(item.total)
        
        // Fix 2: Amount value uses the same color as its donut slice
        holder.tvAmount.setTextColor(color)
    }

    override fun getItemCount() = data.size
}
