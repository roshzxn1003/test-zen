package com.example.data.repository

import com.example.data.dao.*
import com.example.data.models.*
import kotlinx.coroutines.flow.Flow

class CashFlowRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val scannedItemDao: ScannedItemDao,
    private val familyDao: FamilyDao,
    private val familyMemberDao: FamilyMemberDao,
    private val receiptDao: ReceiptDao
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allSavingsGoals: Flow<List<SavingsGoalEntity>> = savingsGoalDao.getAllGoals()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    val allScannedItems: Flow<List<ScannedItemEntity>> = scannedItemDao.getAllScannedItems()
    val allReceipts: Flow<List<ReceiptEntity>> = receiptDao.getAllReceipts()
    val allReceiptItems: Flow<List<ReceiptItemEntity>> = receiptDao.getAllReceiptItems()

    // Receipt scoping & operations
    fun getReceiptForTransaction(transactionId: Long): Flow<ReceiptEntity?> = receiptDao.getReceiptForTransaction(transactionId)
    suspend fun getReceiptByTransactionIdDirect(transactionId: Long): ReceiptEntity? = receiptDao.getReceiptByTransactionIdDirect(transactionId)
    fun getItemsForTransaction(transactionId: Long): Flow<List<ReceiptItemEntity>> = receiptDao.getItemsForTransaction(transactionId)
    suspend fun getItemsForTransactionDirect(transactionId: Long): List<ReceiptItemEntity> = receiptDao.getItemsForTransactionDirect(transactionId)

    suspend fun saveReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Long {
        val receiptId = receiptDao.insertReceipt(receipt)
        val itemsWithId = items.map { it.copy(receiptId = receiptId, transactionId = receipt.transactionId) }
        receiptDao.insertReceiptItems(itemsWithId)
        return receiptId
    }

    suspend fun updateReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>) {
        receiptDao.updateReceipt(receipt)
        receiptDao.deleteReceiptItemsByTransactionId(receipt.transactionId)
        val itemsWithId = items.map { it.copy(receiptId = receipt.id, transactionId = receipt.transactionId) }
        receiptDao.insertReceiptItems(itemsWithId)
    }

    suspend fun deleteReceiptByTransactionId(transactionId: Long) {
        receiptDao.deleteReceiptItemsByTransactionId(transactionId)
        receiptDao.deleteReceiptByTransactionId(transactionId)
    }

    // Transaction scoping
    fun getTransactionsByScope(scope: FinanceScope): Flow<List<TransactionEntity>> = transactionDao.getTransactionsByScope(scope)
    fun getTransactionsByScopeAndType(scope: FinanceScope, type: TransactionType): Flow<List<TransactionEntity>> = transactionDao.getTransactionsByScopeAndType(scope, type)
    fun getFamilyTransactions(familyId: String): Flow<List<TransactionEntity>> = transactionDao.getFamilyTransactions(familyId)
    fun getFamilyTransactionsByMember(familyId: String, userId: String): Flow<List<TransactionEntity>> = transactionDao.getFamilyTransactionsByMember(familyId, userId)
    fun getFamilyIncome(familyId: String): Flow<List<TransactionEntity>> = transactionDao.getFamilyIncome(familyId)
    fun getFamilyExpenses(familyId: String): Flow<List<TransactionEntity>> = transactionDao.getFamilyExpenses(familyId)

    // Budget scoping
    fun getBudgetsByScope(scope: FinanceScope): Flow<List<BudgetEntity>> = budgetDao.getBudgetsByScope(scope)
    fun getBudgetsByFamilyId(familyId: String): Flow<List<BudgetEntity>> = budgetDao.getBudgetsByFamilyId(familyId)

    // Savings Goals scoping
    fun getGoalsByScope(scope: FinanceScope): Flow<List<SavingsGoalEntity>> = savingsGoalDao.getGoalsByScope(scope)
    fun getGoalsByFamilyId(familyId: String): Flow<List<SavingsGoalEntity>> = savingsGoalDao.getGoalsByFamilyId(familyId)

    // Families
    suspend fun insertFamily(family: FamilyEntity) = familyDao.insertFamily(family)
    suspend fun updateFamily(family: FamilyEntity) = familyDao.updateFamily(family)
    suspend fun deleteFamily(family: FamilyEntity) = familyDao.deleteFamily(family)
    suspend fun getFamilyById(id: String): FamilyEntity? = familyDao.getFamilyById(id)
    suspend fun getFirstFamily(): FamilyEntity? = familyDao.getFirstFamily()
    fun getAllFamilies(): Flow<List<FamilyEntity>> = familyDao.getAllFamilies()
    fun getAllFamiliesForUser(userId: String): Flow<List<FamilyEntity>> = familyDao.getAllFamiliesForUser(userId)

    // Family Members
    suspend fun insertMember(member: FamilyMemberEntity) = familyMemberDao.insertMember(member)
    suspend fun updateMember(member: FamilyMemberEntity) = familyMemberDao.updateMember(member)
    suspend fun deleteMember(member: FamilyMemberEntity) = familyMemberDao.deleteMember(member)
    fun getMembersByFamilyId(familyId: String): Flow<List<FamilyMemberEntity>> = familyMemberDao.getMembersByFamilyId(familyId)
    fun getMemberByUserId(userId: String): Flow<List<FamilyMemberEntity>> = familyMemberDao.getMemberByUserId(userId)
    suspend fun getMemberByFamilyAndUser(familyId: String, userId: String): FamilyMemberEntity? = familyMemberDao.getMemberByFamilyAndUser(familyId, userId)

    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>> {
        return budgetDao.getBudgetsForMonth(monthYear)
    }

    suspend fun addTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun getTransactionByServerId(serverId: String): TransactionEntity? {
        return transactionDao.getTransactionByServerId(serverId)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction.copy(syncStatus = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction.copy(isDeleted = true, syncStatus = "PENDING_DELETE", updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun addCategory(category: CategoryEntity): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun saveBudget(budget: BudgetEntity): Long {
        return budgetDao.insertOrUpdateBudget(budget)
    }

    suspend fun deleteBudget(budget: BudgetEntity) {
        budgetDao.deleteBudget(budget)
    }

    suspend fun saveSavingsGoal(goal: SavingsGoalEntity): Long {
        return savingsGoalDao.insertOrUpdateGoal(goal)
    }

    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) {
        savingsGoalDao.deleteGoal(goal)
    }

    suspend fun addScannedItem(item: ScannedItemEntity): Long {
        return scannedItemDao.insertScannedItem(item)
    }

    suspend fun deleteScannedItem(item: ScannedItemEntity) {
        scannedItemDao.deleteScannedItem(item)
    }
}
