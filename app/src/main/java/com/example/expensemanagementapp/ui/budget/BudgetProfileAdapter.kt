package com.example.expensemanagementapp.ui.budget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.BudgetProfileEntity

class BudgetProfileAdapter(
    private val onProfileSelected: (BudgetProfileEntity) -> Unit,
    private val onEditRequested: (BudgetProfileEntity) -> Unit,
    private val onDeleteRequested: (BudgetProfileEntity) -> Unit
) : RecyclerView.Adapter<BudgetProfileAdapter.ProfileViewHolder>() {

    private var profiles: List<BudgetProfileEntity> = emptyList()

    fun setData(newProfiles: List<BudgetProfileEntity>) {
        profiles = newProfiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_budget_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.bind(profile)
    }

    override fun getItemCount(): Int = profiles.size

    inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvIcon: TextView = view.findViewById(R.id.tvProfileIcon)
        private val tvName: TextView = view.findViewById(R.id.tvProfileName)
        private val tvStatus: TextView = view.findViewById(R.id.tvProfileStatus)
        private val rbSelected: RadioButton = view.findViewById(R.id.rbSelected)
        private val btnEdit: android.widget.ImageButton = view.findViewById(R.id.btnEditProfile)
        private val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDeleteProfile)

        fun bind(profile: BudgetProfileEntity) {
            tvName.text = profile.name
            tvIcon.text = profile.icon
            
            // Apply profile color (Hardcoded as requested)
            try {
                val colorInt = android.graphics.Color.parseColor("#3F51B5")
                tvIcon.background?.setTint(colorInt)
            } catch (e: Exception) {
                // Fallback
            }

            rbSelected.isChecked = profile.is_active
            tvStatus.visibility = if (profile.is_active) View.VISIBLE else View.GONE
            
            // Show delete button only for non-active profiles
            btnDelete.visibility = if (profile.is_active) View.GONE else View.VISIBLE
            btnDelete.setOnClickListener {
                onDeleteRequested(profile)
            }
            
            btnEdit.setOnClickListener {
                onEditRequested(profile)
            }

            itemView.setOnClickListener {
                onProfileSelected(profile)
            }
        }
    }
}
