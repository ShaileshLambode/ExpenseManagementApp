package com.example.expensemanagementapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.expensemanagementapp.data.dao.BudgetProfileDao
import com.example.expensemanagementapp.data.dao.ExpenseDao
import com.example.expensemanagementapp.data.dao.GoalDao
import com.example.expensemanagementapp.data.dao.RecurringTransactionDao
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.BudgetProfileEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.data.entity.GoalEntity
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import com.example.expensemanagementapp.data.entity.RecurringTransactionEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.data.entity.ProfileEntity

@Database(
    entities = [
        BalanceEntity::class,
        TransactionEntity::class,
        PendingPlanEntity::class,
        CategoryEntity::class,
        ProfileEntity::class,
        BudgetProfileEntity::class,
        GoalEntity::class,
        RecurringTransactionEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetProfileDao(): BudgetProfileDao
    abstract fun goalDao(): GoalDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {}
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
             override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {}
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_plans ADD COLUMN category TEXT NOT NULL DEFAULT 'Others'")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `dob` INTEGER, `city` TEXT NOT NULL, `state` TEXT NOT NULL, `mobile` TEXT NOT NULL, `imageUri` TEXT, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE user_profile ADD COLUMN gender TEXT")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create budget_profiles table
                database.execSQL("CREATE TABLE IF NOT EXISTS `budget_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)")
                
                // 2. Create goals table
                database.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `budget_profile_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `target_amount` REAL NOT NULL, `current_amount` REAL NOT NULL, `deadline` INTEGER, `icon` TEXT NOT NULL, `color` TEXT NOT NULL, `is_completed` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`budget_profile_id`) REFERENCES `budget_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                
                // 3. Create recurring_transactions table
                database.execSQL("CREATE TABLE IF NOT EXISTS `recurring_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `budget_profile_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `amount` REAL NOT NULL, `mode` TEXT NOT NULL, `category` TEXT NOT NULL, `transaction_type` TEXT NOT NULL, `frequency` TEXT NOT NULL, `start_date` INTEGER NOT NULL, `end_date` INTEGER, `next_due_date` INTEGER NOT NULL, `message` TEXT, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`budget_profile_id`) REFERENCES `budget_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                
                // 4. Seed default profile
                database.execSQL("INSERT INTO budget_profiles (id, name, color, icon, is_active, created_at) VALUES (1, 'My Budget', '#3F51B5', '💰', 1, ${System.currentTimeMillis()})")
                
                // 5. Add budget_profile_id to existing tables
                database.execSQL("ALTER TABLE transactions ADD COLUMN budget_profile_id INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE balances ADD COLUMN budget_profile_id INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE pending_plans ADD COLUMN budget_profile_id INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Remove duplicates if any
                database.execSQL("DELETE FROM balances WHERE id NOT IN (SELECT MIN(id) FROM balances GROUP BY budget_profile_id, type)")
                // Add unique index
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_balances_budget_profile_id_type` ON `balances` (`budget_profile_id`, `type`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                     "expense_manager_db"
                )
                 .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                 .addCallback(object : Callback() {
                     override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                         super.onCreate(db)
                         db.execSQL("INSERT INTO budget_profiles (id, name, color, icon, is_active, created_at) VALUES (1, 'Primary Budget', '#3F51B5', '💰', 1, ${System.currentTimeMillis()})")
                     }
                 })
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
