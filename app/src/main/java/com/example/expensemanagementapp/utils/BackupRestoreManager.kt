package com.example.expensemanagementapp.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.data.entity.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val app: String = "Mulya",
    val balances: List<BalanceEntity>,
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val pendingPlans: List<PendingPlanEntity>,
    val profile: ProfileEntity?,
    val profileImageBase64: String?,
    val preferences: Map<String, Any>
)

object BackupRestoreManager {

    private const val PREFS_NAME = "Settings"

    suspend fun createBackup(context: Context): String = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val dao = database.expenseDao()

        // 1. Fetch Data
        val balances = dao.getAllBalancesSync()
        val transactions = dao.getAllTransactionsSync()
        val categories = dao.getAllCategoriesSync()
        val pendingPlans = dao.getAllPendingPlansSync()
        
        val profile = dao.getProfileSync()
        
        // 2. Profile Image
        var profileImageBase64: String? = null
        if (profile?.imageUri != null) {
            try {
                val uri = Uri.parse(profile.imageUri)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    profileImageBase64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val preferences = prefs.all.filterValues { it != null } as Map<String, Any>

        // 4. Create Object
        val backupData = BackupData(
            balances = balances,
            transactions = transactions,
            categories = categories,
            pendingPlans = pendingPlans,
            profile = profile,
            profileImageBase64 = profileImageBase64,
            preferences = preferences
        )

        // 5. Serialize
        val json = Gson().toJson(backupData)
        
        // 6. Save to Cache First
        val fileName = "mulya_backup_${SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(Date())}.json"
        val cacheFile = File(context.cacheDir, fileName)
        FileOutputStream(cacheFile).use { it.write(json.toByteArray()) }
        
        // 7. Save to Downloads (Copy logic from ExportUtils)
        saveToDownloads(context, cacheFile, fileName)
        
        return@withContext fileName // Return filename or success message path
    }

    private fun saveToDownloads(context: Context, sourceFile: File, fileName: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                resolver.openOutputStream(uri).use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output!!)
                    }
                }
            } else {
                throw java.io.IOException("Failed to create MediaStore entry")
            }
        } else {
            // Legacy approach
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun restoreBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.expenseDao()

            // 1. Stream Parse JSON (Avoid OOM)
            val backupData: BackupData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    Gson().fromJson(reader, BackupData::class.java)
                }
            } ?: throw Exception("Could not read backup file")

            // 2. Validation
            if (backupData.app != "Mulya") throw Exception("Invalid Backup File")

            // 3. Atomic Restore using Transaction
            database.withTransaction {
                // Clear existing data
                database.clearAllTables()

                // Restore Profile Image
                var newImageUri: String? = null
                if (backupData.profileImageBase64 != null) {
                    try {
                        val bytes = Base64.decode(backupData.profileImageBase64, Base64.DEFAULT)
                        val file = File(context.filesDir, "restored_profile_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { it.write(bytes) }
                        newImageUri = Uri.fromFile(file).toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Insert Data
                backupData.balances.forEach { dao.insertBalance(it) }
                backupData.transactions.forEach { dao.insertTransaction(it) }
                backupData.categories.forEach { dao.insertCategory(it) }
                backupData.pendingPlans.forEach { dao.insertPlan(it) }
                
                if (backupData.profile != null) {
                    val restoredProfile = if (newImageUri != null) {
                        backupData.profile.copy(imageUri = newImageUri)
                    } else {
                        backupData.profile
                    }
                    dao.insertProfile(restoredProfile)
                }
            }

            // 4. Restore Preferences (Outside transaction as it's not DB)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            with(prefs.edit()) {
                clear()
                backupData.preferences.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Float -> putFloat(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is String -> putString(key, value)
                        is Double -> putFloat(key, value.toFloat())
                    }
                }
                apply()
            }

            return@withContext true

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
