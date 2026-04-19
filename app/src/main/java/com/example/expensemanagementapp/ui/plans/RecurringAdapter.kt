package com.example.expensemanagementapp.ui.plans

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.RecurringTransactionEntity
import java.text.SimpleDateFormat
import java.util.*

class RecurringAdapter(
    private val onDelete: (RecurringTransactionEntity) -> Unit
) : ListAdapter<RecurringTransactionEntity, RecurringAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recurring, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDelete)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvRecurringTitle)
        private val tvAmount: TextView = view.findViewById(R.id.tvRecurringAmount)
        private val tvMeta: TextView = view.findViewById(R.id.tvRecurringMeta)
        private val tvNextDue: TextView = view.findViewById(R.id.tvNextDue)
        private val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDeleteRecurring)
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(item: RecurringTransactionEntity, onDelete: (RecurringTransactionEntity) -> Unit) {
            tvTitle.text = item.title
            tvAmount.text = "₹ ${item.amount}"
            tvMeta.text = "${item.frequency} • ${item.category} • ${item.mode}"
            tvNextDue.text = "Next: ${dateFormat.format(Date(item.next_due_date))}"
            
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecurringTransactionEntity>() {
        override fun areItemsTheSame(oldItem: RecurringTransactionEntity, newItem: RecurringTransactionEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RecurringTransactionEntity, newItem: RecurringTransactionEntity): Boolean = oldItem == newItem
    }
}
