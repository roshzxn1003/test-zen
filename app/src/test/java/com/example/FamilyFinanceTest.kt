package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.CashFlowDatabase
import com.example.data.models.*
import com.example.data.repository.CashFlowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FamilyFinanceTest {
    private lateinit var db: CashFlowDatabase
    private lateinit var repo: CashFlowRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CashFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CashFlowRepository(
            db.transactionDao(),
            db.categoryDao(),
            db.budgetDao(),
            db.savingsGoalDao(),
            db.scannedItemDao(),
            db.familyDao(),
            db.familyMemberDao(),
            db.receiptDao()
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testPersonalTransaction() = runBlocking {
        val tx = TransactionEntity(
            title = "Transport",
            amount = 500.0,
            type = TransactionType.EXPENSE,
            category = "Transport",
            financeScope = FinanceScope.PERSONAL
        )
        repo.addTransaction(tx)

        val personal = repo.getTransactionsByScope(FinanceScope.PERSONAL).first()
        assertEquals(1, personal.size)
        assertEquals(FinanceScope.PERSONAL, personal[0].financeScope)
        assertNull(personal[0].familyId)
    }

    @Test
    fun testFamilyTransaction() = runBlocking {
        val tx = TransactionEntity(
            title = "Groceries",
            amount = 850.0,
            type = TransactionType.EXPENSE,
            category = "Groceries",
            financeScope = FinanceScope.FAMILY,
            familyId = "family_123",
            createdByUserId = "user_456"
        )
        repo.addTransaction(tx)

        val family = repo.getFamilyTransactions("family_123").first()
        assertEquals(1, family.size)
        assertEquals(FinanceScope.FAMILY, family[0].financeScope)
        assertEquals("family_123", family[0].familyId)
        assertEquals("user_456", family[0].createdByUserId)
    }

    @Test
    fun testReceiptWithItemizedDetails() = runBlocking {
        val tx = TransactionEntity(
            title = "Whole Foods",
            amount = 14.98,
            type = TransactionType.EXPENSE,
            category = "Food & Dining",
            financeScope = FinanceScope.PERSONAL
        )
        val txId = repo.addTransaction(tx)

        val receipt = ReceiptEntity(
            transactionId = txId,
            merchantName = "Whole Foods Market",
            receiptNumber = "INV-9948",
            receiptDate = "2026-08-15",
            receiptTime = "14:30",
            subtotal = 13.98,
            discount = 0.0,
            tax = 1.00,
            total = 14.98,
            currency = "₹",
            paymentMethod = "UPI"
        )

        val items = listOf(
            ReceiptItemEntity(
                transactionId = txId,
                name = "Organic Whole Milk 1Gal",
                quantity = 1.0,
                unitPrice = 5.49,
                totalPrice = 5.49
            ),
            ReceiptItemEntity(
                transactionId = txId,
                name = "Artisan Sourdough Bread",
                quantity = 2.0,
                unitPrice = 4.245,
                totalPrice = 8.49
            )
        )

        val savedReceiptId = repo.saveReceiptWithItems(receipt, items)
        assertTrue(savedReceiptId > 0)

        val fetchedReceipt = repo.getReceiptByTransactionIdDirect(txId)
        assertNotNull(fetchedReceipt)
        assertEquals("Whole Foods Market", fetchedReceipt?.merchantName)
        assertEquals("INV-9948", fetchedReceipt?.receiptNumber)
        assertEquals(14.98, fetchedReceipt?.total ?: 0.0, 0.01)

        val fetchedItems = repo.getItemsForTransactionDirect(txId)
        assertEquals(2, fetchedItems.size)
        assertEquals("Organic Whole Milk 1Gal", fetchedItems[0].name)
        assertEquals(1.0, fetchedItems[0].quantity, 0.001)
        assertEquals(5.49, fetchedItems[0].unitPrice, 0.001)
        assertEquals(5.49, fetchedItems[0].totalPrice, 0.001)
        assertEquals("Artisan Sourdough Bread", fetchedItems[1].name)
        assertEquals(2.0, fetchedItems[1].quantity, 0.001)

        // Test deletion cascade
        repo.deleteReceiptByTransactionId(txId)
        assertNull(repo.getReceiptByTransactionIdDirect(txId))
        assertTrue(repo.getItemsForTransactionDirect(txId).isEmpty())
    }

    @Test
    fun testBudgetCreationAndQuery() = runBlocking {
        val budget = BudgetEntity(
            categoryName = "Food & Dining",
            monthlyLimit = 15000.0,
            monthYear = "2026-08",
            periodType = "MONTHLY"
        )
        val budgetId = repo.saveBudget(budget)
        assertTrue(budgetId > 0)

        val allBudgets = repo.getBudgetsForMonth("2026-08").first()
        assertEquals(1, allBudgets.size)
        assertEquals("Food & Dining", allBudgets[0].categoryName)
        assertEquals(15000.0, allBudgets[0].monthlyLimit, 0.01)
    }

    @Test
    fun testSavingsGoalAndDeposit() = runBlocking {
        val goal = SavingsGoalEntity(
            title = "Emergency Fund",
            targetAmount = 100000.0,
            currentAmount = 25000.0,
            colorHex = "#10B981"
        )
        val goalId = repo.saveSavingsGoal(goal)
        assertTrue(goalId > 0)

        val updatedGoal = goal.copy(id = goalId, currentAmount = 35000.0)
        repo.saveSavingsGoal(updatedGoal)

        val goals = repo.allSavingsGoals.first()
        assertEquals(1, goals.size)
        assertEquals("Emergency Fund", goals[0].title)
        assertEquals(35000.0, goals[0].currentAmount, 0.01)
        assertEquals(100000.0, goals[0].targetAmount, 0.01)
    }

    @Test
    fun testStrictIsolationPersonalVsFamily() = runBlocking {
        // Add Personal transaction
        val personalTx = TransactionEntity(
            title = "Personal Lunch",
            amount = 250.0,
            type = TransactionType.EXPENSE,
            category = "Food & Dining",
            financeScope = FinanceScope.PERSONAL,
            familyId = null
        )
        repo.addTransaction(personalTx)

        // Add Family transaction
        val familyTx = TransactionEntity(
            title = "Family Groceries",
            amount = 2500.0,
            type = TransactionType.EXPENSE,
            category = "Food & Dining",
            financeScope = FinanceScope.FAMILY,
            familyId = "fam_999",
            createdByUserId = "user_arun"
        )
        repo.addTransaction(familyTx)

        val personalList = repo.getTransactionsByScope(FinanceScope.PERSONAL).first()
        val familyList = repo.getFamilyTransactions("fam_999").first()

        assertEquals(1, personalList.size)
        assertEquals("Personal Lunch", personalList[0].title)
        assertNull(personalList[0].familyId)

        assertEquals(1, familyList.size)
        assertEquals("Family Groceries", familyList[0].title)
        assertEquals("fam_999", familyList[0].familyId)
    }

    @Test
    fun testFamilyVaultInviteAndJoin() = runBlocking {
        val family = FamilyEntity(
            id = "FAM-8F4A2B",
            name = "Arun's Family Vault",
            createdByUserId = "user_arun",
            createdAt = System.currentTimeMillis()
        )
        repo.insertFamily(family)

        val owner = FamilyMemberEntity(
            id = "mem_owner",
            familyId = "FAM-8F4A2B",
            userId = "user_arun",
            name = "Arun",
            role = FamilyRole.ADMIN,
            joinedAt = System.currentTimeMillis()
        )
        repo.insertMember(owner)

        // Member joins with Family ID
        val joinedMember = FamilyMemberEntity(
            id = "mem_joined",
            familyId = "FAM-8F4A2B",
            userId = "user_mom",
            name = "Mom",
            role = FamilyRole.MEMBER,
            joinedAt = System.currentTimeMillis()
        )
        repo.insertMember(joinedMember)

        val allMembers = repo.getMembersByFamilyId("FAM-8F4A2B").first()
        assertEquals(2, allMembers.size)
        assertEquals("Arun", allMembers[0].name)
        assertEquals(FamilyRole.ADMIN, allMembers[0].role)
        assertEquals("Mom", allMembers[1].name)
        assertEquals(FamilyRole.MEMBER, allMembers[1].role)
    }

    @Test
    fun testSyncEngineSafeOfflineExecution() = runBlocking {
        val authService = com.example.data.network.LocalAuthService()
        val syncEngine = com.example.data.network.SyncEngine(
            transactionDao = db.transactionDao(),
            familyDao = db.familyDao(),
            authService = authService,
            familyMemberDao = db.familyMemberDao(),
            budgetDao = db.budgetDao(),
            savingsGoalDao = db.savingsGoalDao()
        )
        // With unconfigured credentials, syncAll should safely report true (offline mode) with 0 exceptions
        val result = syncEngine.syncAll()
        assertTrue("SyncEngine should execute safely without crashing in offline mode", result)
    }

    @Test
    fun testTransactionSoftDeleteFiltering() = runBlocking {
        val tx = TransactionEntity(
            id = 100L,
            title = "Movie Tickets",
            amount = 450.0,
            type = TransactionType.EXPENSE,
            category = "Entertainment",
            financeScope = FinanceScope.PERSONAL,
            serverId = "srv_tx_100"
        )
        repo.addTransaction(tx)

        var activeTxs = repo.allTransactions.first()
        assertEquals(1, activeTxs.size)

        // Soft delete the transaction (synced record)
        repo.deleteTransaction(activeTxs[0])

        // Live flow should now filter it out immediately
        activeTxs = repo.allTransactions.first()
        assertEquals(0, activeTxs.size)

        // But pending deletes should still find it for cloud sync
        val pendingDeletes = db.transactionDao().getPendingDeletes()
        assertEquals(1, pendingDeletes.size)
        assertEquals("srv_tx_100", pendingDeletes[0].serverId)
        assertTrue(pendingDeletes[0].isDeleted)
    }

    @Test
    fun testLocalOnlyTransactionHardDelete() = runBlocking {
        val localTx = TransactionEntity(
            id = 200L,
            title = "Coffee",
            amount = 120.0,
            type = TransactionType.EXPENSE,
            category = "Food & Dining",
            financeScope = FinanceScope.PERSONAL,
            serverId = null // Purely local record
        )
        repo.addTransaction(localTx)

        var activeTxs = repo.allTransactions.first()
        assertEquals(1, activeTxs.size)

        // Delete purely local transaction
        repo.deleteTransaction(activeTxs[0])

        // It should be completely purged from SQLite
        activeTxs = repo.allTransactions.first()
        assertEquals(0, activeTxs.size)
        val pendingDeletes = db.transactionDao().getPendingDeletes()
        assertEquals(0, pendingDeletes.size)
    }

    @Test
    fun testDatabaseSumAggregations() = runBlocking {
        val income1 = TransactionEntity(
            title = "Salary",
            amount = 50000.0,
            type = TransactionType.INCOME,
            category = "Salary",
            financeScope = FinanceScope.PERSONAL
        )
        val income2 = TransactionEntity(
            title = "Dividend",
            amount = 2500.0,
            type = TransactionType.INCOME,
            category = "Investments",
            financeScope = FinanceScope.PERSONAL
        )
        val expense1 = TransactionEntity(
            title = "Rent",
            amount = 15000.0,
            type = TransactionType.EXPENSE,
            category = "Housing",
            financeScope = FinanceScope.PERSONAL
        )
        repo.addTransaction(income1)
        repo.addTransaction(income2)
        repo.addTransaction(expense1)

        val totalIncome = db.transactionDao().observeTotalIncome(FinanceScope.PERSONAL).first()
        val totalExpense = db.transactionDao().observeTotalExpense(FinanceScope.PERSONAL).first()

        assertEquals(52500.0, totalIncome, 0.001)
        assertEquals(15000.0, totalExpense, 0.001)
    }
}

