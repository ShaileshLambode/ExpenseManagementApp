package com.example.expensemanagementapp.ui.recyclebin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.viewmodel.RecycleBinItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecycleBinAdapter(
    private val onRestoreClicked: (RecycleBinItem) -> Unit,
    private val onDeleteClicked: (RecycleBinItem) -> Unit
) : RecyclerView.Adapter<RecycleBinAdapter.ViewHolder>() {

    private var items: List<RecycleBinItem> = emptyList()

    fun setData(newItems: List<RecycleBinItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recycle_bin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvType: TextView = view.findViewById(R.id.tvItemType)
        private val tvName: TextView = view.findViewById(R.id.tvItemName)
        private val tvTimeLeft: TextView = view.findViewById(R.id.tvTimeLeft)
        private val tvDeletedDate: TextView = view.findViewById(R.id.tvDeletedDate)
        private val btnRestore: ImageButton = view.findViewById(R.id.btnRestore)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePermanent)

        fun bind(item: RecycleBinItem) {
            tvType.text = item.typeName
            tvName.text = item.name

            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvDeletedDate.text = "Deleted: ${sdf.format(Date(item.deletedAt))}"

            val daysPassed = (System.currentTimeMillis() - item.deletedAt) / (1000 * 60 * 60 * 24)
            val daysLeft = maxOf(0L, 30L - daysPassed)
            tvTimeLeft.text = "$daysLeft days left"

            btnRestore.setOnClickListener { onRestoreClicked(item) }
            btnDelete.setOnClickListener { onDeleteClicked(item) }
        }
    }
}
