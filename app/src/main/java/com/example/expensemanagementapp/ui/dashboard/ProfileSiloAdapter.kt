package com.example.expensemanagementapp.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.entity.BudgetProfileEntity
import com.example.expensemanagementapp.data.entity.ProfileBalanceSum

class ProfileSiloAdapter : RecyclerView.Adapter<ProfileSiloAdapter.SiloViewHolder>() {

    private var profiles: List<BudgetProfileEntity> = emptyList()
    private var balances: Map<Int, Double> = emptyMap()

    fun setData(newProfiles: List<BudgetProfileEntity>, newBalances: List<ProfileBalanceSum>) {
        profiles = newProfiles
        balances = newBalances.associate { it.budget_profile_id to it.total }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiloViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_silo, parent, false)
        return SiloViewHolder(view)
    }

    override fun onBindViewHolder(holder: SiloViewHolder, position: Int) {
        holder.bind(profiles[position], balances[profiles[position].id] ?: 0.0)
    }

    override fun getItemCount(): Int = profiles.size

    class SiloViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvIcon: TextView = view.findViewById(R.id.tvProfileIcon)
        private val tvName: TextView = view.findViewById(R.id.tvProfileName)
        private val tvBalance: TextView = view.findViewById(R.id.tvProfileBalance)

        fun bind(profile: BudgetProfileEntity, balance: Double) {
            tvIcon.text = profile.icon
            tvName.text = profile.name
            tvBalance.text = "₹ %.2f".format(balance)
        }
    }
}
