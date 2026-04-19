package com.example.expensemanagementapp.ui.goals

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.GoalEntity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoalAdapter(
    private val onContribute: (GoalEntity) -> Unit,
    private val onDelete: (GoalEntity) -> Unit
) : ListAdapter<GoalEntity, GoalAdapter.GoalViewHolder>(GoalDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_goal, parent, false)
        return GoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        holder.bind(getItem(position), onContribute, onDelete)
    }

    class GoalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvIcon: TextView = view.findViewById(R.id.tvGoalIcon)
        private val tvName: TextView = view.findViewById(R.id.tvGoalName)
        private val tvStatus: TextView = view.findViewById(R.id.tvGoalStatus)
        private val tvDeadline: TextView = view.findViewById(R.id.tvGoalDeadline)
        private val progress: CircularProgressIndicator = view.findViewById(R.id.progressGoal)
        private val tvPercent: TextView = view.findViewById(R.id.tvProgressPercent)
        private val btnContribute: MaterialButton = view.findViewById(R.id.btnContribute)
        private val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

        fun bind(goal: GoalEntity, onContribute: (GoalEntity) -> Unit, onDelete: (GoalEntity) -> Unit) {
            tvIcon.text = goal.icon
            tvName.text = goal.name
            tvStatus.text = "₹ ${goal.current_amount.toInt()} / ₹ ${goal.target_amount.toInt()}"
            
            if (goal.deadline != null) {
                tvDeadline.text = "Due: ${dateFormat.format(Date(goal.deadline))}"
                tvDeadline.visibility = View.VISIBLE
            } else {
                tvDeadline.visibility = View.GONE
            }

            val percent = if (goal.target_amount > 0) {
                ((goal.current_amount / goal.target_amount) * 100).toInt()
            } else 0
            
            progress.progress = percent.coerceAtMost(100)
            tvPercent.text = "$percent%"
            
            btnContribute.setOnClickListener { onContribute(goal) }
            
            itemView.setOnLongClickListener {
                onDelete(goal)
                true
            }
        }
    }

    class GoalDiffCallback : DiffUtil.ItemCallback<GoalEntity>() {
        override fun areItemsTheSame(oldItem: GoalEntity, newItem: GoalEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: GoalEntity, newItem: GoalEntity): Boolean = oldItem == newItem
    }
}
