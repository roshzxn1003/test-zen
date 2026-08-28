package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.CashFlowDatabase
import com.example.data.familyledger.*
import com.example.data.models.FamilyRole
import com.example.data.models.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FamilyLedgerRedesignTest {

    private lateinit var db: CashFlowDatabase
    private lateinit var repository: FamilyLedgerRepository
    private lateinit var ledgerDao: FamilyLedgerDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CashFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ledgerDao = db.familyLedgerDao()
        repository = FamilyLedgerRepository(
            ledgerDao = ledgerDao,
            legacyTransactionDao = db.transactionDao(),
            legacyFamilyDao = db.familyDao(),
            legacyMemberDao = db.familyMemberDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testCreateAndObserveTransaction() = runBlocking {
        val familyId = "fam_test_1"
        val tx = repository.createTransaction(
            familyId = familyId,
            title = "Groceries",
            description = "Vegetables and fruits",
            amount = 650.0,
            category = "Food & Dining",
            type = TransactionType.EXPENSE,
            paymentMethod = "UPI",
            paidByMemberId = "mem_arun",
            paidByName = "Arun",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_arun"
        )

        assertNotNull(tx.transactionId)
        assertTrue(tx.transactionId.isNotBlank())
        assertEquals("Groceries", tx.title)
        assertEquals(650.0, tx.amount, 0.001)

        val list = repository.observeTransactions(familyId).first()
        assertEquals(1, list.size)
        assertEquals(tx.transactionId, list[0].transactionId)
        assertEquals("Arun", list[0].paidByName)
    }

    @Test
    fun testEditTransactionPreservesIdAndNoDuplicates() = runBlocking {
        val familyId = "fam_test_2"
        val original = repository.createTransaction(
            familyId = familyId,
            title = "Electricity",
            description = "July bill",
            amount = 1200.0,
            category = "Bills & Utilities",
            type = TransactionType.EXPENSE,
            paymentMethod = "UPI",
            paidByMemberId = "mem_1",
            paidByName = "Karthik",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_karthik"
        )

        val updated = repository.updateTransaction(
            existing = original,
            title = "Electricity Bill - Paid",
            description = "July bill with surcharge",
            amount = 1250.0,
            category = "Bills & Utilities",
            type = TransactionType.EXPENSE,
            paymentMethod = "UPI",
            paidByMemberId = "mem_1",
            paidByName = "Karthik",
            dateMillis = original.dateMillis,
            currentUserId = "user_karthik"
        )

        // Verify transactionId is preserved
        assertEquals(original.transactionId, updated.transactionId)
        assertEquals(1250.0, updated.amount, 0.001)
        assertEquals("Electricity Bill - Paid", updated.title)

        // Verify no duplicate records created in Room
        val list = repository.observeTransactions(familyId).first()
        assertEquals(1, list.size)
        assertEquals(1250.0, list[0].amount, 0.001)
    }

    @Test
    fun testDeleteTransactionSoftDelete() = runBlocking {
        val familyId = "fam_test_3"
        val tx = repository.createTransaction(
            familyId = familyId,
            title = "Dinner",
            description = "",
            amount = 450.0,
            category = "Food & Dining",
            type = TransactionType.EXPENSE,
            paymentMethod = "Cash",
            paidByMemberId = "mem_1",
            paidByName = "Arun",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_arun"
        )

        val listBefore = repository.observeTransactions(familyId).first()
        assertEquals(1, listBefore.size)

        repository.deleteTransaction(tx.transactionId, "user_arun")

        val listAfter = repository.observeTransactions(familyId).first()
        assertEquals(0, listAfter.size)
    }

    @Test
    fun testMemberSpendingAggregation() = runBlocking {
        val familyId = "fam_test_4"

        // Insert two members
        val member1 = FamilyVaultMember(
            memberId = "mem_1",
            familyId = familyId,
            userId = "user_1",
            name = "Arun",
            role = FamilyRole.ADMIN
        )
        val member2 = FamilyVaultMember(
            memberId = "mem_2",
            familyId = familyId,
            userId = "user_2",
            name = "Karthik",
            role = FamilyRole.MEMBER
        )
        ledgerDao.upsertMembers(listOf(member1, member2))

        // Create transactions paid by member 1 and member 2
        repository.createTransaction(
            familyId = familyId,
            title = "Rent",
            amount = 15000.0,
            category = "Housing & Rent",
            type = TransactionType.EXPENSE,
            paymentMethod = "Bank Transfer",
            paidByMemberId = "user_1",
            paidByName = "Arun",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_1"
        )

        repository.createTransaction(
            familyId = familyId,
            title = "Groceries",
            amount = 2500.0,
            category = "Food & Dining",
            type = TransactionType.EXPENSE,
            paymentMethod = "UPI",
            paidByMemberId = "user_1",
            paidByName = "Arun",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_1"
        )

        repository.createTransaction(
            familyId = familyId,
            title = "Wifi",
            amount = 1000.0,
            category = "Bills & Utilities",
            type = TransactionType.EXPENSE,
            paymentMethod = "UPI",
            paidByMemberId = "user_2",
            paidByName = "Karthik",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_2"
        )

        val membersWithStats = repository.observeMembers(familyId).first()
        assertEquals(2, membersWithStats.size)

        val arun = membersWithStats.find { it.name == "Arun" }!!
        val karthik = membersWithStats.find { it.name == "Karthik" }!!

        assertEquals(17500.0, arun.totalPaid, 0.001)
        assertEquals(2, arun.transactionCount)

        assertEquals(1000.0, karthik.totalPaid, 0.001)
        assertEquals(1, karthik.transactionCount)
    }

    @Test
    fun testFamilyIsolation() = runBlocking {
        val familyA = "fam_A"
        val familyB = "fam_B"

        repository.createTransaction(
            familyId = familyA,
            title = "Family A Expense",
            amount = 100.0,
            category = "General",
            type = TransactionType.EXPENSE,
            paymentMethod = "UPI",
            paidByMemberId = "mem_a",
            paidByName = "Member A",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_a"
        )

        repository.createTransaction(
            familyId = familyB,
            title = "Family B Expense",
            amount = 200.0,
            category = "General",
            type = TransactionType.EXPENSE,
            paymentMethod = "UPI",
            paidByMemberId = "mem_b",
            paidByName = "Member B",
            dateMillis = System.currentTimeMillis(),
            currentUserId = "user_b"
        )

        val listA = repository.observeTransactions(familyA).first()
        val listB = repository.observeTransactions(familyB).first()

        assertEquals(1, listA.size)
        assertEquals("Family A Expense", listA[0].title)

        assertEquals(1, listB.size)
        assertEquals("Family B Expense", listB[0].title)
    }

    @Test
    fun testDuplicatePreventionOnUpsert() = runBlocking {
        val familyId = "fam_dup_test"
        val fixedId = UUID.randomUUID().toString()

        val tx1 = LedgerTransaction(
            transactionId = fixedId,
            familyId = familyId,
            title = "Groceries",
            amount = 500.0,
            paidByMemberId = "u1",
            paidByName = "Arun"
        )

        val tx2 = LedgerTransaction(
            transactionId = fixedId,
            familyId = familyId,
            title = "Groceries - Updated",
            amount = 550.0,
            paidByMemberId = "u1",
            paidByName = "Arun"
        )

        ledgerDao.upsertTransaction(tx1)
        ledgerDao.upsertTransaction(tx2)

        val list = repository.observeTransactions(familyId).first()
        assertEquals(1, list.size)
        assertEquals(550.0, list[0].amount, 0.001)
        assertEquals("Groceries - Updated", list[0].title)
    }
}
