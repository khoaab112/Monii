package com.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Baseline migration for version 1 to 2
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN eventId INTEGER")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `name` TEXT NOT NULL, 
                `description` TEXT NOT NULL, 
                `startDate` INTEGER NOT NULL, 
                `endDate` INTEGER, 
                `limitAmount` REAL
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN colorHex TEXT NOT NULL DEFAULT '#FF9800'")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN destinationWalletId INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `debts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `personName` TEXT NOT NULL, 
                `type` TEXT NOT NULL, 
                `totalAmount` REAL NOT NULL, 
                `remainingAmount` REAL NOT NULL, 
                `walletId` INTEGER NOT NULL, 
                `creationDate` INTEGER NOT NULL, 
                `dueDate` INTEGER, 
                `note` TEXT NOT NULL, 
                `status` TEXT NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE debts ADD COLUMN repaymentType TEXT NOT NULL DEFAULT 'FLEXIBLE'")
        db.execSQL("ALTER TABLE debts ADD COLUMN periodicAmount REAL")
        db.execSQL("ALTER TABLE debts ADD COLUMN periodType TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallets ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallets ADD COLUMN isClosed INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallets ADD COLUMN targetAmount REAL")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN debtId INTEGER")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN notificationKey TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_notificationKey ON transactions(notificationKey)")
    }
}

@Database(
    entities = [
        Wallet::class,
        Transaction::class,
        Budget::class,
        SavingsGoal::class,
        AppSetting::class,
        Event::class,
        Debt::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
