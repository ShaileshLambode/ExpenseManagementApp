package com.example.expensemanagementapp.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.BudgetProfileEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

class RecentTransactionAdapter : ListAdapter<TransactionEntity, RecentTransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {
    private var profiles: List<BudgetProfileEntity> = emptyList()

    fun setProfiles(newProfiles: List<BudgetProfileEntity>) {
        profiles = newProfiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction_modern, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position), profiles)
    }

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTransactionTitle)
        private val tvMeta: TextView = view.findViewById(R.id.tvTransactionMeta)
        private val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
        private val tvIcon: TextView = view.findViewById(R.id.tvCategoryIcon)
        private val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())

        fun bind(transaction: TransactionEntity, profiles: List<BudgetProfileEntity>) {
            tvTitle.text = transaction.title
            
            val profile = profiles.find { it.id == transaction.budget_profile_id }
            val profileName = profile?.name ?: "Unknown"
            val dateStr = dateFormat.format(Date(transaction.timestamp))
            tvMeta.text = "$profileName • $dateStr"
            
            val sign = if (transaction.transaction_type == "EXPENSE") "-" else "+"
            tvAmount.text = "$sign ₹ ${transaction.amount}"
            
            if (transaction.transaction_type == "EXPENSE") {
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.expense_red))
            } else {
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.income_green))
            }
            
            // Icon mapping (Simplified for now)
            tvIcon.text = when(transaction.category) {
                "Food" -> "🍔"
                "Transport" -> "🚗"
                "Shopping" -> "🛍️"
                "Bills" -> "📄"
                "Entertainment" -> "🎮"
                "Health" -> "🏥"
                else -> "💰"
            }
        }
    }

    class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean = oldItem == newItem
    }
}
