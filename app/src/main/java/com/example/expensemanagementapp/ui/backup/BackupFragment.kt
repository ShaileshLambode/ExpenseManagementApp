package com.example.expensemanagementapp.ui.backup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.utils.BackupRestoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File // Added import
import androidx.core.content.FileProvider // Added import (Assuming dependency exists, standard in AndroidX)

class BackupFragment : Fragment() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            performBackup()
        } else {
             Toast.makeText(context, getString(R.string.permission_required_backup), Toast.LENGTH_LONG).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            confirmRestore(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_backup, container, false)
        
        view.findViewById<Button>(R.id.btnCreateBackup).setOnClickListener {
            checkPermissionAndBackup()
        }

        view.findViewById<Button>(R.id.btnRestoreBackup).setOnClickListener {
            openFilePicker()
        }
        
        return view
    }

    private fun checkPermissionAndBackup() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            performBackup()
        } else {
             if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                performBackup()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun performBackup() {
        lifecycleScope.launch {
            try {
                val fileName = BackupRestoreManager.createBackup(requireContext())
                
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.backup_successful))
                        .setMessage(getString(R.string.backup_saved_msg, fileName))
                        .setPositiveButton(getString(R.string.ok), null)
                        .setNeutralButton(getString(R.string.share_backup)) { _, _ ->
                            shareBackupFile(fileName)
                        }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.backup_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareBackupFile(fileName: String) {
        try {
            val file = File(requireContext().cacheDir, fileName)
            if (!file.exists()) {
                 Toast.makeText(context, "File not found for sharing", Toast.LENGTH_SHORT).show()
                 return
            }
            
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_backup)))
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun openFilePicker() {
        try {
            filePickerLauncher.launch("application/json")
        } catch (e: Exception) {
             Toast.makeText(context, "File picker unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.restore_confirm_title))
            .setMessage(getString(R.string.restore_warning))
            .setPositiveButton(getString(R.string.restore_action)) { _, _ ->
                performRestore(uri)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .show()
    }

    private fun performRestore(uri: Uri) {
         lifecycleScope.launch {
            val success = BackupRestoreManager.restoreBackup(requireContext(), uri)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                    // Restart App
                     val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                } else {
                    Toast.makeText(context, getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                }
            }
         }
    }
}
