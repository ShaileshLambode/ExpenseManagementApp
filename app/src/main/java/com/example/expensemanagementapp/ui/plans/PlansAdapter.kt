package com.example.expensemanagementapp.ui.plans

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlansAdapter(
    private val onPlanCompleted: (PendingPlanEntity) -> Unit,
    private val onPlanClicked: (PendingPlanEntity) -> Unit,
    private val onDeleteClicked: (PendingPlanEntity) -> Unit
) : ListAdapter<PendingPlanEntity, PlansAdapter.PlanViewHolder>(PlanDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val plan = getItem(position)
        holder.bind(plan, onPlanCompleted, onPlanClicked, onDeleteClicked)
    }

    class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvPlanTitle)
        private val category: TextView = itemView.findViewById(R.id.tvPlanCategory)
        private val date: TextView = itemView.findViewById(R.id.tvPlanDate)
        private val mode: TextView = itemView.findViewById(R.id.tvPlanMode)
        private val amount: TextView = itemView.findViewById(R.id.tvPlanAmount)
        private val checkbox: CheckBox = itemView.findViewById(R.id.cbCompleted)
        private val deleteBtn: android.widget.ImageButton = itemView.findViewById(R.id.btnDeletePlan)

        fun bind(plan: PendingPlanEntity, onPlanCompleted: (PendingPlanEntity) -> Unit, onPlanClicked: (PendingPlanEntity) -> Unit, onDeleteClicked: (PendingPlanEntity) -> Unit) {
            title.text = plan.title
            category.text = plan.category
            if (plan.due_date != null) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                date.text = itemView.context.getString(R.string.due_on, dateFormat.format(Date(plan.due_date)))
            } else {
                date.text = itemView.context.getString(R.string.no_due_date)
            }
            mode.text = plan.mode
            amount.text = "${if(plan.plan_type == "PAY") "-" else "+"} ${itemView.context.getString(R.string.currency_symbol)} ${plan.amount}"
            
            // Color logic
            if (plan.plan_type == "RECEIVE") {
                amount.setTextColor(itemView.context.getColor(R.color.income_green))
            } else {
                amount.setTextColor(itemView.context.getColor(R.color.expense_red))
            }

            checkbox.isChecked = false
            checkbox.setOnClickListener {
                if (checkbox.isChecked) {
                    onPlanCompleted(plan)
                }
            }

            itemView.setOnClickListener {
                onPlanClicked(plan)
            }

            deleteBtn.setOnClickListener {
                onDeleteClicked(plan)
            }
        }
    }

    class PlanDiffCallback : DiffUtil.ItemCallback<PendingPlanEntity>() {
        override fun areItemsTheSame(oldItem: PendingPlanEntity, newItem: PendingPlanEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PendingPlanEntity, newItem: PendingPlanEntity): Boolean {
            return oldItem == newItem
        }
    }
}
