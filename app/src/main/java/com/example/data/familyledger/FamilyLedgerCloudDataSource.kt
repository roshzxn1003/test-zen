package com.example.data.familyledger

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Cloud data source interface for Family Ledger.
 * Implement this interface when reconstructing the remote backend.
 */
interface FamilyLedgerCloudDataSource {
    val isAvailable: Boolean
    fun observeRealtimeTransactions(familyId: String): Flow<RealtimeLedgerEvent>
    suspend fun fetchFamilyVault(familyId: String): FamilyVault?
    suspend fun fetchFamilyMembers(familyId: String): List<FamilyVaultMember>
    suspend fun fetchFamilyTransactions(familyId: String): List<LedgerTransaction>
    suspend fun upsertFamilyVault(vault: FamilyVault): Boolean
    suspend fun upsertFamilyMember(member: FamilyVaultMember): Boolean
    suspend fun deleteFamilyMember(memberId: String): Boolean
    suspend fun upsertTransaction(tx: LedgerTransaction): Boolean
    suspend fun softDeleteTransaction(transactionId: String): Boolean
    suspend fun fetchFamilyByInviteCode(inviteCode: String): FamilyVault?
}

/**
 * Default offline / no-op implementation used when no backend is attached.
 */
class NoOpFamilyLedgerCloudDataSource : FamilyLedgerCloudDataSource {
    override val isAvailable: Boolean = false
    override fun observeRealtimeTransactions(familyId: String): Flow<RealtimeLedgerEvent> = emptyFlow()
    override suspend fun fetchFamilyVault(familyId: String): FamilyVault? = null
    override suspend fun fetchFamilyMembers(familyId: String): List<FamilyVaultMember> = emptyList()
    override suspend fun fetchFamilyTransactions(familyId: String): List<LedgerTransaction> = emptyList()
    override suspend fun upsertFamilyVault(vault: FamilyVault): Boolean = false
    override suspend fun upsertFamilyMember(member: FamilyVaultMember): Boolean = false
    override suspend fun deleteFamilyMember(memberId: String): Boolean = false
    override suspend fun upsertTransaction(tx: LedgerTransaction): Boolean = false
    override suspend fun softDeleteTransaction(transactionId: String): Boolean = false
    override suspend fun fetchFamilyByInviteCode(inviteCode: String): FamilyVault? = null
}
