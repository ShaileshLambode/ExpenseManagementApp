package com.example.expensemanagementapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.expensemanagementapp.data.dao.ExpenseDao
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.data.entity.PendingPlanEntity
import com.example.expensemanagementapp.data.entity.TransactionEntity
import com.example.expensemanagementapp.data.entity.ProfileEntity

@Database(
    entities = [BalanceEntity::class, TransactionEntity::class, PendingPlanEntity::class, CategoryEntity::class, ProfileEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Initial schema creation usually handling automatically, but if upgrading from v1
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
             override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
             }
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                     "expense_manager_db"
                )
                 .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
