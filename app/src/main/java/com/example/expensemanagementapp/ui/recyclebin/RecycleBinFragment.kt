package com.example.expensemanagementapp.ui.recyclebin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.RecycleBinViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton

class RecycleBinFragment : Fragment() {

    private lateinit var viewModel: RecycleBinViewModel
    private lateinit var adapter: RecycleBinAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recycle_bin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        val repository = ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[RecycleBinViewModel::class.java]

        // Purge old items on load
        viewModel.purgeOldItems()

        val rvRecycleBin: RecyclerView = view.findViewById(R.id.rvRecycleBin)
        val tvEmptyBin: TextView = view.findViewById(R.id.tvEmptyBin)
        val btnEmptyBin: MaterialButton = view.findViewById(R.id.btnEmptyBin)

        adapter = RecycleBinAdapter(
            onRestoreClicked = { item ->
                viewModel.restoreItem(item)
                Toast.makeText(requireContext(), "Restored ${item.name}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClicked = { item ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Permanently")
                    .setMessage("Are you sure you want to permanently delete '${item.name}'? This action cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.permanentlyDeleteItem(item)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        rvRecycleBin.layoutManager = LinearLayoutManager(requireContext())
        rvRecycleBin.adapter = adapter

        viewModel.allDeletedItems.observe(viewLifecycleOwner) { items ->
            adapter.setData(items)
            if (items.isEmpty()) {
                rvRecycleBin.visibility = View.GONE
                tvEmptyBin.visibility = View.VISIBLE
                btnEmptyBin.visibility = View.GONE
            } else {
                rvRecycleBin.visibility = View.VISIBLE
                tvEmptyBin.visibility = View.GONE
                btnEmptyBin.visibility = View.VISIBLE
            }
        }

        btnEmptyBin.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Empty Recycle Bin")
                .setMessage("Are you sure you want to permanently delete all items in the recycle bin? This action cannot be undone.")
                .setPositiveButton("Empty Bin") { _, _ ->
                    viewModel.emptyBin()
                    Toast.makeText(requireContext(), "Recycle bin emptied", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
