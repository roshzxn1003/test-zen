package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.CashFlowDatabase
import com.example.data.models.*
import com.example.data.network.LocalAuthService
import com.example.data.network.SharedPreferencesSessionManager
import com.example.data.network.SyncEngine
import com.example.data.repository.CashFlowRepository
import io.github.jan.supabase.auth.user.UserSession
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
class BackendSyncAndGuestMigrationTest {

    private lateinit var context: Context
    private lateinit var db: CashFlowDatabase
    private lateinit var repo: CashFlowRepository
    private lateinit var syncEngine: SyncEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
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
        syncEngine = SyncEngine(
            transactionDao = db.transactionDao(),
            familyDao = db.familyDao(),
            authService = LocalAuthService(),
            familyMemberDao = db.familyMemberDao(),
            budgetDao = db.budgetDao(),
            savingsGoalDao = db.savingsGoalDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSyncEngineUuidValidation() {
        val validUuid = UUID.randomUUID().toString()
        assertTrue("Expected valid UUID string to pass validation", syncEngine.isValidUuid(validUuid))

        assertFalse("local_user_1 must not be treated as a valid UUID", syncEngine.isValidUuid("local_user_1"))
        assertFalse("Email must not be treated as a valid UUID", syncEngine.isValidUuid("user@example.com"))
        assertFalse("Blank string must not be treated as a valid UUID", syncEngine.isValidUuid(""))
        assertFalse("Null string must not be treated as a valid UUID", syncEngine.isValidUuid(null))
        assertFalse("Arbitrary text must not be treated as a valid UUID", syncEngine.isValidUuid("not-a-uuid"))
    }

    @Test
    fun testGuestToAccountDataMigration() = runBlocking {
        // Step 1: User creates transactions in guest mode
        val guestTx1 = TransactionEntity(
            title = "Morning Coffee",
            amount = 120.0,
            type = TransactionType.EXPENSE,
            category = "Food & Dining",
            financeScope = FinanceScope.PERSONAL,
            createdByUserId = "local_user_1",
            syncStatus = "LOCAL_ONLY"
        )
        val guestTx2 = TransactionEntity(
            title = "Metro Card Recharge",
            amount = 500.0,
            type = TransactionType.EXPENSE,
            category = "Transport",
            financeScope = FinanceScope.PERSONAL,
            createdByUserId = null,
            syncStatus = "LOCAL_ONLY"
        )
        repo.addTransaction(guestTx1)
        repo.addTransaction(guestTx2)

        // Step 2: User authenticates with a Supabase UUID
        val authenticatedUserId = UUID.randomUUID().toString()
        val count = repo.reassignPersonalTransactionsToUser(authenticatedUserId)
        assertEquals(2, count)

        // Step 3: Verify records are claimed by authenticated UUID and marked PENDING_CREATE
        val transactions = repo.getTransactionsByScope(FinanceScope.PERSONAL).first()
        assertEquals(2, transactions.size)
        for (tx in transactions) {
            assertEquals(authenticatedUserId, tx.createdByUserId)
            assertEquals("PENDING_CREATE", tx.syncStatus)
        }
    }

    @Test
    fun testSharedPreferencesSessionManagerPersistence() = runBlocking {
        val sessionManager = SharedPreferencesSessionManager(context)

        val dummySession = UserSession(
            accessToken = "test_access_jwt_token_12345",
            refreshToken = "test_refresh_jwt_token_67890",
            expiresIn = 3600L,
            tokenType = "bearer",
            user = null,
            type = "bearer"
        )

        // Save session
        sessionManager.saveSession(dummySession)

        // Load session and verify tokens match
        val restoredSession = sessionManager.loadSession()
        assertNotNull("Session must be restored from SharedPreferences", restoredSession)
        assertEquals(dummySession.accessToken, restoredSession?.accessToken)
        assertEquals(dummySession.refreshToken, restoredSession?.refreshToken)

        // Delete session and verify it is cleared
        sessionManager.deleteSession()
        val clearedSession = sessionManager.loadSession()
        assertNull("Session must be null after deleteSession", clearedSession)
    }
}
