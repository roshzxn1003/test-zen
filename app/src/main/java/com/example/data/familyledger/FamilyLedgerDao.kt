package com.example.data.familyledger

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyLedgerDao {

    // --- TRANSACTIONS ---
    @Query("SELECT * FROM ledger_transactions WHERE familyId = :familyId AND isDeleted = 0 ORDER BY dateMillis DESC")
    fun getTransactionsForFamily(familyId: String): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM ledger_transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): LedgerTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: LedgerTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<LedgerTransaction>)

    @Query("UPDATE ledger_transactions SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE transactionId = :transactionId")
    suspend fun markTransactionDeleted(transactionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ledger_transactions WHERE transactionId = :transactionId")
    suspend fun deleteTransactionPermanently(transactionId: String)

    @Query("SELECT * FROM ledger_transactions WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreateTransactions(): List<LedgerTransaction>

    @Query("SELECT * FROM ledger_transactions WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdateTransactions(): List<LedgerTransaction>

    @Query("SELECT * FROM ledger_transactions WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeleteTransactions(): List<LedgerTransaction>

    // --- FAMILY VAULTS ---
    @Query("SELECT * FROM family_vaults WHERE familyId = :familyId LIMIT 1")
    fun observeFamilyVault(familyId: String): Flow<FamilyVault?>

    @Query("SELECT * FROM family_vaults WHERE familyId = :familyId LIMIT 1")
    suspend fun getFamilyVault(familyId: String): FamilyVault?

    @Query("SELECT * FROM family_vaults WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getFamilyVaultByInviteCode(inviteCode: String): FamilyVault?

    @Query("SELECT * FROM family_vaults ORDER BY createdAt DESC")
    fun getAllFamilyVaults(): Flow<List<FamilyVault>>

    @Query("SELECT * FROM family_vaults ORDER BY createdAt DESC")
    suspend fun getAllFamilyVaultsOnce(): List<FamilyVault>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyVault(vault: FamilyVault)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyVaults(vaults: List<FamilyVault>)

    // --- MEMBERS ---
    @Query("SELECT * FROM family_vault_members WHERE familyId = :familyId AND isDeleted = 0 ORDER BY joinedAt ASC")
    fun getMembersForFamily(familyId: String): Flow<List<FamilyVaultMember>>

    @Query("SELECT * FROM family_vault_members WHERE memberId = :memberId LIMIT 1")
    suspend fun getMemberById(memberId: String): FamilyVaultMember?

    @Query("SELECT * FROM family_vault_members WHERE familyId = :familyId AND userId = :userId LIMIT 1")
    suspend fun getMemberByFamilyAndUser(familyId: String, userId: String): FamilyVaultMember?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: FamilyVaultMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<FamilyVaultMember>)

    @Query("UPDATE family_vault_members SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE memberId = :memberId")
    suspend fun markMemberDeleted(memberId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM family_vault_members WHERE syncStatus = 'PENDING_CREATE' AND isDeleted = 0")
    suspend fun getPendingCreateMembers(): List<FamilyVaultMember>

    @Query("SELECT * FROM family_vault_members WHERE syncStatus = 'PENDING_UPDATE' AND isDeleted = 0")
    suspend fun getPendingUpdateMembers(): List<FamilyVaultMember>

    @Query("SELECT * FROM family_vault_members WHERE syncStatus = 'PENDING_DELETE' OR isDeleted = 1")
    suspend fun getPendingDeleteMembers(): List<FamilyVaultMember>
}
