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
    val budgetProfiles: List<BudgetProfileEntity>? = null,
    val goals: List<GoalEntity>? = null,
    val recurringTransactions: List<RecurringTransactionEntity>? = null,
    val profile: ProfileEntity?,
    val profileImageBase64: String?,
    val preferences: Map<String, Any>
)

object BackupRestoreManager {

    private const val PREFS_NAME = "Settings"

    suspend fun createBackup(context: Context): String = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val expenseDao = database.expenseDao()
        val profileDao = database.budgetProfileDao()
        val goalDao = database.goalDao()
        val recurringDao = database.recurringTransactionDao()

        // 1. Fetch Data
        val balances = expenseDao.getAllBalancesSync()
        val transactions = expenseDao.getAllTransactionsSync()
        val categories = expenseDao.getAllCategoriesSync()
        val pendingPlans = expenseDao.getAllPendingPlansSync()
        val budgetProfiles = profileDao.getAllProfilesSync()
        val goals = goalDao.getAllGoalsSync()
        val recurringTransactions = recurringDao.getAllRecurringSync()
        
        val profile = expenseDao.getProfileSync()
        
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
            budgetProfiles = budgetProfiles,
            goals = goals,
            recurringTransactions = recurringTransactions,
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
        
        // 7. Save to Downloads
        saveToDownloads(context, cacheFile, fileName)
        
        return@withContext fileName
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
            val expenseDao = database.expenseDao()
            val profileDao = database.budgetProfileDao()
            val goalDao = database.goalDao()
            val recurringDao = database.recurringTransactionDao()

            // 1. Stream Parse JSON
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

                // A. Restore Budget Profiles (Must be first for FKs)
                backupData.budgetProfiles?.forEach { profileDao.insertProfile(it) }

                // B. Recovery Mechanism for missing profiles (in case of old/corrupt backups)
                val referencedProfileIds = mutableSetOf<Int>()
                backupData.balances.forEach { referencedProfileIds.add(it.budget_profile_id) }
                backupData.transactions.forEach { referencedProfileIds.add(it.budget_profile_id) }
                backupData.pendingPlans.forEach { referencedProfileIds.add(it.budget_profile_id) }
                
                val activeProfileIdFromPrefs = (backupData.preferences["ACTIVE_PROFILE_ID"] as? Double)?.toInt() ?: 1
                
                referencedProfileIds.forEach { id ->
                    if (profileDao.getProfileById(id) == null) {
                        profileDao.insertProfile(
                            BudgetProfileEntity(
                                id = id,
                                name = "Restored Profile $id",
                                color = "#3F51B5",
                                icon = "💰",
                                is_active = (id == activeProfileIdFromPrefs),
                                created_at = System.currentTimeMillis()
                            )
                        )
                    }
                }

                // C. Restore Categories (Independent)
                backupData.categories.forEach { expenseDao.insertCategory(it) }

                // D. Restore Profile Image
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

                // E. Insert Related Data
                backupData.balances.forEach { expenseDao.insertBalance(it) }
                backupData.transactions.forEach { expenseDao.insertTransaction(it) }
                backupData.pendingPlans.forEach { expenseDao.insertPlan(it) }
                backupData.goals?.forEach { goalDao.insertGoal(it) }
                backupData.recurringTransactions?.forEach { recurringDao.insertRecurring(it) }
                
                if (backupData.profile != null) {
                    val restoredProfile = if (newImageUri != null) {
                        backupData.profile.copy(imageUri = newImageUri)
                    } else {
                        backupData.profile
                    }
                    expenseDao.insertProfile(restoredProfile)
                }
            }

            // 4. Restore Preferences
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
                        is Double -> {
                            // SharedPreferences doesn't support Double directly, check if it was Int or Float
                            if (value == value.toInt().toDouble()) {
                                putInt(key, value.toInt())
                            } else {
                                putFloat(key, value.toFloat())
                            }
                        }
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
