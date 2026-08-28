package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_transactions` (`transactionId` TEXT NOT NULL, `familyId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `categoryIcon` TEXT NOT NULL, `type` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, `paidByMemberId` TEXT NOT NULL, `paidByName` TEXT NOT NULL, `dateMillis` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `lastModifiedBy` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED', `syncVersion` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`transactionId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_vaults` (`familyId` TEXT NOT NULL, `familyName` TEXT NOT NULL, `inviteCode` TEXT NOT NULL, `createdBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED', PRIMARY KEY(`familyId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_vault_members` (`memberId` TEXT NOT NULL, `familyId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `totalPaid` REAL NOT NULL DEFAULT 0.0, `transactionCount` INTEGER NOT NULL DEFAULT 0, `joinedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED', PRIMARY KEY(`memberId`))"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `upiId` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `upiTransactionId` TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `merchantName` TEXT NOT NULL, `receiptNumber` TEXT, `receiptDate` TEXT NOT NULL, `receiptTime` TEXT, `subtotal` REAL NOT NULL, `discount` REAL NOT NULL, `tax` REAL NOT NULL, `total` REAL NOT NULL, `currency` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, `imageUri` TEXT, `rawText` TEXT, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipt_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptId` INTEGER NOT NULL, `transactionId` INTEGER NOT NULL, `name` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `totalPrice` REAL NOT NULL)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_profiles` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `email` TEXT NOT NULL, `avatarUrl` TEXT, `serverId` TEXT, `syncStatus` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf("transactions", "categories", "budgets", "savings_goals", "families", "family_members")
        for (table in tables) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `serverId` TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PENDING_CREATE'")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add families table
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `families` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        // Add family_members table
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_members` (`id` TEXT NOT NULL, `familyId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        
        // Alter transactions
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `financeScope` TEXT NOT NULL DEFAULT 'PERSONAL'")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `familyId` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `createdByUserId` TEXT")
        
        // Alter budgets
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `financeScope` TEXT NOT NULL DEFAULT 'PERSONAL'")
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `familyId` TEXT")
        
        // Alter savings_goals
        db.execSQL("ALTER TABLE `savings_goals` ADD COLUMN `financeScope` TEXT NOT NULL DEFAULT 'PERSONAL'")
        db.execSQL("ALTER TABLE `savings_goals` ADD COLUMN `familyId` TEXT")
    }
}

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        ScannedItemEntity::class,
        FamilyEntity::class,
        FamilyMemberEntity::class,
        UserProfileEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        com.example.data.familyledger.LedgerTransaction::class,
        com.example.data.familyledger.FamilyVault::class,
        com.example.data.familyledger.FamilyVaultMember::class
    ],
    version = 9,
    exportSchema = false
)
abstract class CashFlowDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun scannedItemDao(): ScannedItemDao
    abstract fun familyDao(): FamilyDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun familyLedgerDao(): com.example.data.familyledger.FamilyLedgerDao

    companion object {
        @Volatile
        private var INSTANCE: CashFlowDatabase? = null

        fun getDatabase(context: Context): CashFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CashFlowDatabase::class.java,
                    "cashflow_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration(true)
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDefaultData(database)
                    }
                }
            }

            private suspend fun seedDefaultData(database: CashFlowDatabase) {
                // Default Categories
                val categories = listOf(
                    CategoryEntity(name = "Food & Dining", iconName = "Restaurant", colorHex = "#EF4444", type = TransactionType.EXPENSE, isDefault = true),
                    CategoryEntity(name = "Shopping", iconName = "ShoppingBag", colorHex = "#EC4899", type = TransactionType.EXPENSE, isDefault = true),
                    CategoryEntity(name = "Housing & Rent", iconName = "Home", colorHex = "#8B5CF6", type = TransactionType.EXPENSE, isDefault = true),
                    CategoryEntity(name = "Transportation", iconName = "DirectionsCar", colorHex = "#3B82F6", type = TransactionType.EXPENSE, isDefault = true),
                    CategoryEntity(name = "Bills & Utilities", iconName = "Receipt", colorHex = "#06B6D4", type = TransactionType.EXPENSE, isDefault = true),
                    CategoryEntity(name = "Entertainment", iconName = "Movie", colorHex = "#F59E0B", type = TransactionType.EXPENSE, isDefault = true),
                    CategoryEntity(name = "Healthcare", iconName = "MedicalServices", colorHex = "#10B981", type = TransactionType.EXPENSE, isDefault = true),
                    CategoryEntity(name = "Salary & Income", iconName = "Payments", colorHex = "#059669", type = TransactionType.INCOME, isDefault = true),
                    CategoryEntity(name = "Freelance / Business", iconName = "Work", colorHex = "#D97706", type = TransactionType.INCOME, isDefault = true),
                    CategoryEntity(name = "Investments", iconName = "TrendingUp", colorHex = "#6366F1", type = TransactionType.INCOME, isDefault = true)
                )
                database.categoryDao().insertCategories(categories)

                
            }
        }
    }
}
