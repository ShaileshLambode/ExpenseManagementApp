package com.example.expensemanagementapp.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val onEdit: (TransactionEntity) -> Unit,
    private val onDelete: (TransactionEntity) -> Unit
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryDiffCallback()) {

    private var expandedPosition = RecyclerView.NO_POSITION
    private var previousExpandedPosition = RecyclerView.NO_POSITION

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryItem.DateHeader -> TYPE_HEADER
            is HistoryItem.TransactionItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
            TransactionViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind((item as HistoryItem.DateHeader).date)
            is TransactionViewHolder -> {
                val transaction = (item as HistoryItem.TransactionItem).transaction
                val isExpanded = position == expandedPosition
                holder.bind(transaction, isExpanded, onEdit, onDelete)

                holder.itemView.setOnClickListener {
                    val currentPos = holder.adapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        // Determine new expansion state
                        val isCurrentlyExpanded = currentPos == expandedPosition
                        
                        previousExpandedPosition = expandedPosition
                        expandedPosition = if (isCurrentlyExpanded) RecyclerView.NO_POSITION else currentPos

                        // Notify changes for animation
                        if (previousExpandedPosition != RecyclerView.NO_POSITION) {
                             notifyItemChanged(previousExpandedPosition)
                        }
                        if (expandedPosition != RecyclerView.NO_POSITION) {
                            notifyItemChanged(expandedPosition)
                        }
                    }
                }
            }
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
        
        fun bind(date: Long) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            tvDateHeader.text = dateFormat.format(Date(date))
        }
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvTransTitle)
        private val amount: TextView = itemView.findViewById(R.id.tvTransAmount)
        private val mode: TextView = itemView.findViewById(R.id.tvTransMode)
        private val date: TextView = itemView.findViewById(R.id.tvTransDate)
        private val btnMoreOptions: android.widget.ImageButton = itemView.findViewById(R.id.btnMoreOptions)
        
        // Expanded views
        private val llExpandedDetails: LinearLayout = itemView.findViewById(R.id.llExpandedDetails)
        private val category: TextView = itemView.findViewById(R.id.tvTransCategory)
        private val fullDate: TextView = itemView.findViewById(R.id.tvTransFullDate)
        private val type: TextView = itemView.findViewById(R.id.tvTransType)
        private val balanceAfter: TextView = itemView.findViewById(R.id.tvTransBalanceAfter)
        private val messageRow: LinearLayout = itemView.findViewById(R.id.llTransMessage)
        private val messageTv: TextView = itemView.findViewById(R.id.tvTransMessage)

        fun bind(transaction: TransactionEntity, isExpanded: Boolean, onEdit: (TransactionEntity) -> Unit, onDelete: (TransactionEntity) -> Unit) {
            // Collapsed Info
            title.text = transaction.title
            val isExpense = transaction.transaction_type == "EXPENSE"
            amount.text = "${if(isExpense) "-" else "+"} ₹ ${transaction.amount}"
            amount.setTextColor(if(isExpense) itemView.context.getColor(R.color.expense_red) else itemView.context.getColor(R.color.income_green))
            mode.text = transaction.mode
            
            val shortDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            date.text = shortDateFormat.format(Date(transaction.timestamp))

            btnMoreOptions.setOnClickListener { view ->
                val popup = android.widget.PopupMenu(view.context, view)
                popup.menu.add("Edit")
                popup.menu.add("Delete")
                popup.setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "Edit" -> {
                            onEdit(transaction)
                            true
                        }
                        "Delete" -> {
                            onDelete(transaction)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
            // Ensure button doesn't trigger item expand
            btnMoreOptions.isFocusable = false

            // Expanded Info
            llExpandedDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            if (isExpanded) {
                category.text = transaction.category ?: "Uncategorized"
                
                val fullDateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                fullDate.text = fullDateFormat.format(Date(transaction.timestamp))
                
                type.text = transaction.transaction_type
                balanceAfter.text = "₹ %.2f".format(transaction.balance_after)
                
                if (!transaction.message.isNullOrBlank()) {
                    messageRow.visibility = View.VISIBLE
                    messageTv.text = transaction.message
                } else {
                    messageRow.visibility = View.GONE
                }
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return if (oldItem is HistoryItem.TransactionItem && newItem is HistoryItem.TransactionItem) {
                oldItem.transaction.id == newItem.transaction.id
            } else if (oldItem is HistoryItem.DateHeader && newItem is HistoryItem.DateHeader) {
                oldItem.date == newItem.date
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
