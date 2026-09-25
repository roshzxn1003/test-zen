package com.example.data.familyledger

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the Family Ledger module.
 *
 * Provides:
 * - Reactive Flow queries for UI (always-fresh from Room)
 * - Suspend functions for one-shot reads used during sync
 * - Pending/offline state queries for the outbox flush engine
 */
@Dao
interface FamilyLedgerDao {

    // =========================================================================
    // LEDGER TRANSACTIONS
    // =========================================================================

    /** Live stream of all non-deleted transactions for a family, newest first. */
    @Query("SELECT * FROM ledger_transactions WHERE familyId = :familyId AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getTransactionsForFamily(familyId: String): Flow<List<LedgerTransaction>>

    /** One-shot read of all non-deleted transactions for a family. */
    @Query("SELECT * FROM ledger_transactions WHERE familyId = :familyId AND isDeleted = 0 ORDER BY dateMillis DESC")
    suspend fun getTransactionsForFamilyOnce(familyId: String): List<LedgerTransaction>

    /** One-shot lookup by primary key. */
    @Query("SELECT * FROM ledger_transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): LedgerTransaction?

    /** Upsert a single transaction (REPLACE conflict strategy). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: LedgerTransaction)

    /** Batch upsert for initial cloud pull. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<LedgerTransaction>)

    /** Marks a transaction as pending soft-delete (still visible until cloud confirms). */
    @Query("""
        UPDATE ledger_transactions
        SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt
        WHERE transactionId = :transactionId
    """)
    suspend fun markTransactionDeleted(
        transactionId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /** Hard-deletes from local Room (called after cloud soft-delete confirmed). */
    @Query("DELETE FROM ledger_transactions WHERE transactionId = :transactionId")
    suspend fun deleteTransactionPermanently(transactionId: String)

    /** Clears all family ledger transactions from local Room. */
    @Query("DELETE FROM ledger_transactions")
    suspend fun clearAllLedgerTransactions()

    // --- Outbox queries (pending states for flush engine) ---

    /** Created locally while online — need to push to remote backend. */
    @Query("SELECT * FROM ledger_transactions WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreateTransactions(): List<LedgerTransaction>

    /** Edited locally while online — need to push update to remote backend. */
    @Query("SELECT * FROM ledger_transactions WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdateTransactions(): List<LedgerTransaction>

    /** Deleted locally — need to soft-delete on remote backend. */
    @Query("SELECT * FROM ledger_transactions WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeleteTransactions(): List<LedgerTransaction>

    /**
     * Written while remote backend was not configured (OFFLINE state).
     * These need to be pushed as creates once connectivity is restored.
     */
    @Query("SELECT * FROM ledger_transactions WHERE syncStatus = 'OFFLINE' AND isDeleted = 0")
    suspend fun getOfflineTransactions(): List<LedgerTransaction>

    // =========================================================================
    // FAMILY VAULTS
    // =========================================================================

    /** Live stream of a specific vault (emits null if vault not in Room). */
    @Query("SELECT * FROM family_vaults WHERE familyId = :familyId LIMIT 1")
    fun observeFamilyVault(familyId: String): Flow<FamilyVault?>

    /** One-shot vault lookup. */
    @Query("SELECT * FROM family_vaults WHERE familyId = :familyId LIMIT 1")
    suspend fun getFamilyVault(familyId: String): FamilyVault?

    /** Look up a vault by its invite code (used in join flow). */
    @Query("SELECT * FROM family_vaults WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getFamilyVaultByInviteCode(inviteCode: String): FamilyVault?

    /** Live stream of all vaults stored in Room. */
    @Query("SELECT * FROM family_vaults ORDER BY createdAt DESC")
    fun getAllFamilyVaults(): Flow<List<FamilyVault>>

    /** One-shot read of all vaults (used during default vault resolution at startup). */
    @Query("SELECT * FROM family_vaults ORDER BY createdAt DESC")
    suspend fun getAllFamilyVaultsOnce(): List<FamilyVault>

    /** Upsert a single vault. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyVault(vault: FamilyVault)

    /** Batch upsert for pull operations. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyVaults(vaults: List<FamilyVault>)

    /** Clears all family vaults from local Room. */
    @Query("DELETE FROM family_vaults")
    suspend fun clearAllFamilyVaults()

    // =========================================================================
    // FAMILY VAULT MEMBERS
    // =========================================================================

    /** Live stream of non-deleted members, ordered by join date. */
    @Query("""
        SELECT * FROM family_vault_members
        WHERE familyId = :familyId AND isDeleted = 0
        ORDER BY joinedAt ASC
    """)
    fun getMembersForFamily(familyId: String): Flow<List<FamilyVaultMember>>

    /** One-shot read of non-deleted members for a family. */
    @Query("""
        SELECT * FROM family_vault_members
        WHERE familyId = :familyId AND isDeleted = 0
        ORDER BY joinedAt ASC
    """)
    suspend fun getMembersForFamilyOnce(familyId: String): List<FamilyVaultMember>

    /** One-shot lookup by member primary key. */
    @Query("SELECT * FROM family_vault_members WHERE memberId = :memberId LIMIT 1")
    suspend fun getMemberById(memberId: String): FamilyVaultMember?

    /** Look up a member by family + user (for join/duplicate detection). */
    @Query("""
        SELECT * FROM family_vault_members
        WHERE familyId = :familyId AND userId = :userId
        LIMIT 1
    """)
    suspend fun getMemberByFamilyAndUser(familyId: String, userId: String): FamilyVaultMember?

    /** Upsert a single member. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: FamilyVaultMember)

    /** Batch upsert for pull operations. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<FamilyVaultMember>)

    /** Soft-marks a member as deleted and queues for cloud removal. */
    @Query("""
        UPDATE family_vault_members
        SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt
        WHERE memberId = :memberId
    """)
    suspend fun markMemberDeleted(
        memberId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    // --- Member outbox queries ---

    @Query("SELECT * FROM family_vault_members WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreateMembers(): List<FamilyVaultMember>

    @Query("SELECT * FROM family_vault_members WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdateMembers(): List<FamilyVaultMember>

    @Query("SELECT * FROM family_vault_members WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeleteMembers(): List<FamilyVaultMember>

    /** Clears all family vault members from local Room. */
    @Query("DELETE FROM family_vault_members")
    suspend fun clearAllFamilyVaultMembers()
}
