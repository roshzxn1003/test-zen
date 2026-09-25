package com.example.data.dao

import androidx.room.*
import com.example.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreates(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdates(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeletes(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE serverId = :serverId LIMIT 1")
    suspend fun getTransactionByServerId(serverId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY dateMillis DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE financeScope = :scope AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getTransactionsByScope(scope: FinanceScope): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE financeScope = :scope AND type = :type AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getTransactionsByScopeAndType(scope: FinanceScope, type: TransactionType): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE familyId = :familyId AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getFamilyTransactions(familyId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE familyId = :familyId AND createdByUserId = :userId AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getFamilyTransactionsByMember(familyId: String, userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE familyId = :familyId AND type = 'INCOME' AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getFamilyIncome(familyId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE familyId = :familyId AND type = 'EXPENSE' AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getFamilyExpenses(familyId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE category = :category AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE financeScope = :scope AND type = 'INCOME' AND isDeleted = 0")
    fun observeTotalIncome(scope: FinanceScope): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE financeScope = :scope AND type = 'EXPENSE' AND isDeleted = 0")
    fun observeTotalExpense(scope: FinanceScope): Flow<Double>

    @Query("UPDATE transactions SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markTransactionDeleted(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET createdByUserId = :newUserId, syncStatus = 'PENDING_CREATE', updatedAt = :updatedAt WHERE (createdByUserId = 'local_user_1' OR createdByUserId IS NULL OR createdByUserId != :newUserId) AND financeScope = 'PERSONAL' AND isDeleted = 0")
    suspend fun reassignPersonalTransactionsToUser(newUserId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE isDeleted = 0 ORDER BY id DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE financeScope = :scope AND isDeleted = 0 ORDER BY id DESC")
    fun getBudgetsByScope(scope: FinanceScope): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE familyId = :familyId AND isDeleted = 0 ORDER BY id DESC")
    fun getBudgetsByFamilyId(familyId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear AND isDeleted = 0")
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreates(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdates(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeletes(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE serverId = :serverId LIMIT 1")
    suspend fun getBudgetByServerId(serverId: String): BudgetEntity?

    @Query("UPDATE budgets SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markBudgetDeleted(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE isDeleted = 0 ORDER BY targetDateMillis ASC")
    fun getAllGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE financeScope = :scope AND isDeleted = 0 ORDER BY targetDateMillis ASC")
    fun getGoalsByScope(scope: FinanceScope): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE familyId = :familyId AND isDeleted = 0 ORDER BY targetDateMillis ASC")
    fun getGoalsByFamilyId(familyId: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreates(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdates(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeletes(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE serverId = :serverId LIMIT 1")
    suspend fun getGoalByServerId(serverId: String): SavingsGoalEntity?

    @Query("UPDATE savings_goals SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markGoalDeleted(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGoal(goal: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoalById(id: Long)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoals()
}

@Dao
interface ScannedItemDao {
    @Query("SELECT * FROM scanned_items ORDER BY addedDateMillis DESC")
    fun getAllScannedItems(): Flow<List<ScannedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedItem(item: ScannedItemEntity): Long

    @Delete
    suspend fun deleteScannedItem(item: ScannedItemEntity)

    @Query("DELETE FROM scanned_items")
    suspend fun deleteAllScannedItems()
}

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId LIMIT 1")
    fun getReceiptForTransaction(transactionId: Long): Flow<ReceiptEntity?>

    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getReceiptByTransactionIdDirect(transactionId: Long): ReceiptEntity?

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId")
    fun getItemsForReceipt(receiptId: Long): Flow<List<ReceiptItemEntity>>

    @Query("SELECT * FROM receipt_items WHERE transactionId = :transactionId")
    fun getItemsForTransaction(transactionId: Long): Flow<List<ReceiptItemEntity>>

    @Query("SELECT * FROM receipt_items WHERE transactionId = :transactionId")
    suspend fun getItemsForTransactionDirect(transactionId: Long): List<ReceiptItemEntity>

    @Query("SELECT * FROM receipts")
    fun getAllReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipt_items")
    fun getAllReceiptItems(): Flow<List<ReceiptItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Query("DELETE FROM receipt_items WHERE transactionId = :transactionId")
    suspend fun deleteReceiptItemsByTransactionId(transactionId: Long)

    @Query("DELETE FROM receipts WHERE transactionId = :transactionId")
    suspend fun deleteReceiptByTransactionId(transactionId: Long)

    @Query("DELETE FROM receipts")
    suspend fun deleteAllReceipts()

    @Query("DELETE FROM receipt_items")
    suspend fun deleteAllReceiptItems()
}

