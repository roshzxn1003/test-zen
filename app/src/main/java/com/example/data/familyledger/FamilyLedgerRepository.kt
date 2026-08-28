package com.example.data.familyledger

import android.util.Log
import com.example.data.dao.FamilyDao
import com.example.data.dao.FamilyMemberDao
import com.example.data.dao.TransactionDao
import com.example.data.models.FamilyRole
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * Single Authoritative Repository for FamilyLedger module.
 * Integrates Room Local Cache with Supabase Cloud Data Source with deterministic
 * Last-Write-Wins conflict resolution, real-time sync, and offline resilience.
 */
class FamilyLedgerRepository(
    private val ledgerDao: FamilyLedgerDao,
    private val legacyTransactionDao: TransactionDao,
    private val legacyFamilyDao: FamilyDao,
    private val legacyMemberDao: FamilyMemberDao,
    private val cloudDataSource: SupabaseFamilyLedgerDataSource = SupabaseFamilyLedgerDataSource()
) {
    private val tag = "FamilyLedgerRepo"
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(FamilySyncStatus.SYNCED)
    val syncStatus: StateFlow<FamilySyncStatus> = _syncStatus.asStateFlow()

    private var activeSyncJob: Job? = null
    private var currentActiveFamilyId: String? = null

    // --- REACTIVE DATA OBSERVATION ---

    fun observeTransactions(familyId: String): Flow<List<LedgerTransaction>> {
        return ledgerDao.getTransactionsForFamily(familyId)
    }

    fun observeMembers(familyId: String): Flow<List<FamilyVaultMember>> {
        return combine(
            ledgerDao.getMembersForFamily(familyId),
            ledgerDao.getTransactionsForFamily(familyId)
        ) { members, transactions ->
            // Compute real-time financial stats per member
            members.map { member ->
                val memberTxs = transactions.filter {
                    it.paidByMemberId == member.userId || it.paidByMemberId == member.memberId || it.paidByName.equals(member.name, ignoreCase = true)
                }
                val totalPaid = memberTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                member.copy(
                    totalPaid = totalPaid,
                    transactionCount = memberTxs.size
                )
            }
        }
    }

    fun observeFamilyVault(familyId: String): Flow<FamilyVault?> {
        return ledgerDao.observeFamilyVault(familyId)
    }

    fun observeAllFamilyVaults(): Flow<List<FamilyVault>> {
        return ledgerDao.getAllFamilyVaults()
    }

    // --- REALTIME SYNC LIFECYCLE ---

    fun startSync(familyId: String, currentUserId: String) {
        if (currentActiveFamilyId == familyId && activeSyncJob?.isActive == true) return
        currentActiveFamilyId = familyId

        activeSyncJob?.cancel()
        activeSyncJob = repositoryScope.launch {
            Log.d(tag, "SYNC_START for family: $familyId")
            
            // 1. First migrate any legacy family records
            migrateLegacyFamilyData(familyId)

            // 2. Initial Full Cloud Sync
            syncWithCloud(familyId, currentUserId)

            // 3. High-frequency 8s live polling loop (reliable across all mobile networks)
            while (isActive) {
                delay(8000)
                try {
                    syncWithCloud(familyId, currentUserId)
                } catch (e: Exception) {
                    Log.w(tag, "Sync loop tick error: ${e.message}")
                }
            }
        }
    }

    fun stopSync() {
        activeSyncJob?.cancel()
        activeSyncJob = null
        currentActiveFamilyId = null
    }

    // --- CLOUD SYNCHRONIZATION ENGINE ---

    suspend fun syncWithCloud(familyId: String, currentUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (familyId.isBlank()) return@withContext true
        if (!cloudDataSource.isAvailable) {
            _syncStatus.value = FamilySyncStatus.OFFLINE
            return@withContext false
        }

        _syncStatus.value = FamilySyncStatus.SYNCING
        try {
            // A. Push Pending Local Transactions
            flushPendingTransactions(familyId)

            // B. Push Pending Local Members
            flushPendingMembers(familyId)

            // C. Pull Remote Family Vault
            val remoteVault = cloudDataSource.fetchFamilyVault(familyId)
            if (remoteVault != null) {
                val localVault = ledgerDao.getFamilyVault(familyId)
                if (localVault == null || remoteVault.updatedAt >= localVault.updatedAt) {
                    ledgerDao.upsertFamilyVault(remoteVault)
                }
            }

            // D. Pull Remote Members
            val remoteMembers = cloudDataSource.fetchFamilyMembers(familyId)
            for (remote in remoteMembers) {
                val local = ledgerDao.getMemberById(remote.memberId)
                    ?: ledgerDao.getMemberByFamilyAndUser(remote.familyId, remote.userId)

                if (local == null) {
                    ledgerDao.upsertMember(remote)
                } else if (local.syncStatus == "SYNCED" && remote.updatedAt >= local.updatedAt) {
                    ledgerDao.upsertMember(local.copy(
                        name = remote.name,
                        role = remote.role,
                        updatedAt = remote.updatedAt
                    ))
                }
            }

            // E. Pull Remote Transactions
            val remoteTransactions = cloudDataSource.fetchFamilyTransactions(familyId)
            for (remote in remoteTransactions) {
                val local = ledgerDao.getTransactionById(remote.transactionId)
                if (local == null) {
                    if (!remote.isDeleted) {
                        ledgerDao.upsertTransaction(remote)
                        Log.d(tag, "SYNC_UPDATE Pulled new remote transaction: ${remote.title} (₹${remote.amount})")
                    }
                } else {
                    if (remote.isDeleted) {
                        ledgerDao.deleteTransactionPermanently(remote.transactionId)
                        Log.d(tag, "SYNC_DELETE Applied remote delete: ${remote.transactionId}")
                    } else if (local.syncStatus == "SYNCED" && remote.updatedAt >= local.updatedAt) {
                        ledgerDao.upsertTransaction(remote)
                        Log.d(tag, "SYNC_EDIT Applied remote update: ${remote.title} (₹${remote.amount})")
                    }
                }
            }

            _syncStatus.value = FamilySyncStatus.SYNCED
            true
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR syncWithCloud: ${e.message}", e)
            _syncStatus.value = FamilySyncStatus.ERROR
            false
        }
    }

    private suspend fun flushPendingTransactions(familyId: String) {
        // Pending Creates
        val creates = ledgerDao.getPendingCreateTransactions()
        for (tx in creates) {
            val success = cloudDataSource.upsertTransaction(tx)
            if (success) {
                ledgerDao.upsertTransaction(tx.copy(syncStatus = "SYNCED"))
            }
        }

        // Pending Updates
        val updates = ledgerDao.getPendingUpdateTransactions()
        for (tx in updates) {
            val success = cloudDataSource.upsertTransaction(tx)
            if (success) {
                ledgerDao.upsertTransaction(tx.copy(syncStatus = "SYNCED"))
            }
        }

        // Pending Deletes
        val deletes = ledgerDao.getPendingDeleteTransactions()
        for (tx in deletes) {
            val success = cloudDataSource.softDeleteTransaction(tx.transactionId, tx.lastModifiedBy)
            if (success) {
                ledgerDao.deleteTransactionPermanently(tx.transactionId)
            }
        }
    }

    private suspend fun flushPendingMembers(familyId: String) {
        val creates = ledgerDao.getPendingCreateMembers()
        for (member in creates) {
            val success = cloudDataSource.upsertFamilyMember(member)
            if (success) {
                ledgerDao.upsertMember(member.copy(syncStatus = "SYNCED"))
            }
        }

        val updates = ledgerDao.getPendingUpdateMembers()
        for (member in updates) {
            val success = cloudDataSource.upsertFamilyMember(member)
            if (success) {
                ledgerDao.upsertMember(member.copy(syncStatus = "SYNCED"))
            }
        }

        val deletes = ledgerDao.getPendingDeleteMembers()
        for (member in deletes) {
            val success = cloudDataSource.deleteFamilyMember(member.memberId)
            if (success) {
                ledgerDao.markMemberDeleted(member.memberId)
            }
        }
    }

    // --- TRANSACTION MUTATION OPERATIONS ---

    suspend fun createTransaction(
        familyId: String,
        title: String,
        description: String = "",
        amount: Double,
        category: String,
        type: TransactionType,
        paymentMethod: String,
        paidByMemberId: String,
        paidByName: String,
        dateMillis: Long,
        currentUserId: String
    ): LedgerTransaction = withContext(Dispatchers.IO) {
        val txId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val transaction = LedgerTransaction(
            transactionId = txId,
            familyId = familyId,
            title = title.trim(),
            description = description.trim(),
            amount = amount,
            category = category,
            categoryIcon = getIconForCategory(category),
            type = type,
            paymentMethod = paymentMethod,
            paidByMemberId = paidByMemberId,
            paidByName = paidByName,
            dateMillis = dateMillis,
            createdAt = now,
            updatedAt = now,
            createdBy = currentUserId,
            lastModifiedBy = currentUserId,
            isDeleted = false,
            syncStatus = if (cloudDataSource.isAvailable) "PENDING_CREATE" else "OFFLINE"
        )

        // 1. Instant local write
        ledgerDao.upsertTransaction(transaction)
        Log.d(tag, "SYNC_CREATE Local write complete: $txId ($title)")

        // 2. Direct cloud write
        repositoryScope.launch {
            if (cloudDataSource.isAvailable) {
                val success = cloudDataSource.upsertTransaction(transaction)
                if (success) {
                    ledgerDao.upsertTransaction(transaction.copy(syncStatus = "SYNCED"))
                    _syncStatus.value = FamilySyncStatus.SYNCED
                } else {
                    _syncStatus.value = FamilySyncStatus.ERROR
                }
            } else {
                _syncStatus.value = FamilySyncStatus.OFFLINE
            }
        }

        transaction
    }

    suspend fun updateTransaction(
        existing: LedgerTransaction,
        title: String,
        description: String,
        amount: Double,
        category: String,
        type: TransactionType,
        paymentMethod: String,
        paidByMemberId: String,
        paidByName: String,
        dateMillis: Long,
        currentUserId: String
    ): LedgerTransaction = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            title = title.trim(),
            description = description.trim(),
            amount = amount,
            category = category,
            categoryIcon = getIconForCategory(category),
            type = type,
            paymentMethod = paymentMethod,
            paidByMemberId = paidByMemberId,
            paidByName = paidByName,
            dateMillis = dateMillis,
            updatedAt = now,
            lastModifiedBy = currentUserId,
            syncStatus = if (cloudDataSource.isAvailable) "PENDING_UPDATE" else "OFFLINE",
            syncVersion = existing.syncVersion + 1
        )

        // 1. Instant local write
        ledgerDao.upsertTransaction(updated)
        Log.d(tag, "SYNC_EDIT Local update complete: ${updated.transactionId}")

        // 2. Direct cloud write
        repositoryScope.launch {
            if (cloudDataSource.isAvailable) {
                val success = cloudDataSource.upsertTransaction(updated)
                if (success) {
                    ledgerDao.upsertTransaction(updated.copy(syncStatus = "SYNCED"))
                    _syncStatus.value = FamilySyncStatus.SYNCED
                } else {
                    _syncStatus.value = FamilySyncStatus.ERROR
                }
            } else {
                _syncStatus.value = FamilySyncStatus.OFFLINE
            }
        }

        updated
    }

    suspend fun deleteTransaction(transactionId: String, currentUserId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = ledgerDao.getTransactionById(transactionId) ?: return@withContext false
        val now = System.currentTimeMillis()
        
        // Mark locally as pending delete
        ledgerDao.markTransactionDeleted(transactionId, now)
        Log.d(tag, "SYNC_DELETE Local delete marked: $transactionId")

        repositoryScope.launch {
            if (cloudDataSource.isAvailable) {
                val success = cloudDataSource.softDeleteTransaction(transactionId, currentUserId)
                if (success) {
                    ledgerDao.deleteTransactionPermanently(transactionId)
                    _syncStatus.value = FamilySyncStatus.SYNCED
                } else {
                    _syncStatus.value = FamilySyncStatus.ERROR
                }
            } else {
                _syncStatus.value = FamilySyncStatus.OFFLINE
            }
        }

        true
    }

    // --- FAMILY & MEMBER MUTATION OPERATIONS ---

    fun generateCleanInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Crisp chars without ambiguous 0/O/1/I
        val code = (1..6).map { chars.random() }.joinToString("")
        return "FAM-$code"
    }

    suspend fun getOrCreateDefaultVault(
        currentUserId: String,
        currentUserName: String
    ): FamilyVault = withContext(Dispatchers.IO) {
        val existingVaults = ledgerDao.getAllFamilyVaultsOnce()
        val valid = existingVaults.firstOrNull { !it.isDeleted && isValidUuid(it.familyId) }
        if (valid != null) {
            return@withContext valid
        }

        val familyId = UUID.randomUUID().toString()
        val inviteCode = generateCleanInviteCode()
        val now = System.currentTimeMillis()
        val defaultName = if (currentUserName.isNotBlank() && currentUserName != "You") "$currentUserName's Family Vault" else "My Family Vault"

        val vault = FamilyVault(
            familyId = familyId,
            familyName = defaultName,
            inviteCode = inviteCode,
            createdBy = currentUserId,
            createdAt = now,
            updatedAt = now
        )
        ledgerDao.upsertFamilyVault(vault)

        val ownerMember = FamilyVaultMember(
            memberId = UUID.randomUUID().toString(),
            familyId = familyId,
            userId = currentUserId,
            name = currentUserName.ifBlank { "You" },
            role = FamilyRole.ADMIN,
            joinedAt = now,
            updatedAt = now
        )
        ledgerDao.upsertMember(ownerMember)

        repositoryScope.launch {
            cloudDataSource.upsertFamilyVault(vault)
            cloudDataSource.upsertFamilyMember(ownerMember)
        }

        vault
    }

    suspend fun createFamilyVault(
        familyName: String,
        currentUserId: String,
        currentUserName: String
    ): FamilyVault = withContext(Dispatchers.IO) {
        val familyId = UUID.randomUUID().toString()
        val inviteCode = generateCleanInviteCode()
        val now = System.currentTimeMillis()

        val vault = FamilyVault(
            familyId = familyId,
            familyName = familyName.trim(),
            inviteCode = inviteCode,
            createdBy = currentUserId,
            createdAt = now,
            updatedAt = now
        )
        ledgerDao.upsertFamilyVault(vault)

        val ownerMember = FamilyVaultMember(
            memberId = UUID.randomUUID().toString(),
            familyId = familyId,
            userId = currentUserId,
            name = currentUserName.ifBlank { "You" },
            role = FamilyRole.ADMIN,
            joinedAt = now,
            updatedAt = now
        )
        ledgerDao.upsertMember(ownerMember)

        repositoryScope.launch {
            cloudDataSource.upsertFamilyVault(vault)
            cloudDataSource.upsertFamilyMember(ownerMember)
        }

        vault
    }

    suspend fun addMember(
        familyId: String,
        name: String,
        role: FamilyRole,
        currentUserId: String
    ): FamilyVaultMember = withContext(Dispatchers.IO) {
        val memberId = UUID.randomUUID().toString()
        val generatedUserId = "user_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()

        val member = FamilyVaultMember(
            memberId = memberId,
            familyId = familyId,
            userId = generatedUserId,
            name = name.trim(),
            role = role,
            joinedAt = now,
            updatedAt = now
        )
        ledgerDao.upsertMember(member)

        repositoryScope.launch {
            cloudDataSource.upsertFamilyMember(member)
        }

        member
    }

    suspend fun removeMember(memberId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = ledgerDao.getMemberById(memberId) ?: return@withContext false
        val now = System.currentTimeMillis()
        ledgerDao.markMemberDeleted(memberId, now)

        repositoryScope.launch {
            cloudDataSource.deleteFamilyMember(memberId)
        }

        true
    }

    suspend fun joinFamilyByInviteCode(
        inviteCode: String,
        currentUserId: String,
        currentUserName: String
    ): Result<FamilyVault> = withContext(Dispatchers.IO) {
        val cleanCode = inviteCode.trim().uppercase()
        if (cleanCode.isBlank()) return@withContext Result.failure(Exception("Please enter a valid invite code"))

        val remoteVault = cloudDataSource.fetchFamilyByInviteCode(cleanCode)
            ?: return@withContext Result.failure(Exception("Family Vault with code '$cleanCode' was not found on cloud"))

        ledgerDao.upsertFamilyVault(remoteVault)

        // Check if member already exists in this family
        val existingMember = ledgerDao.getMemberByFamilyAndUser(remoteVault.familyId, currentUserId)
        if (existingMember == null) {
            val newMember = FamilyVaultMember(
                memberId = UUID.randomUUID().toString(),
                familyId = remoteVault.familyId,
                userId = currentUserId,
                name = currentUserName.ifBlank { "Family Member" },
                role = FamilyRole.MEMBER,
                joinedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            ledgerDao.upsertMember(newMember)
            repositoryScope.launch {
                cloudDataSource.upsertFamilyMember(newMember)
            }
        }

        // Trigger immediate sync for this family
        syncWithCloud(remoteVault.familyId, currentUserId)

        Result.success(remoteVault)
    }

    // --- DATA MIGRATION ---

    private suspend fun migrateLegacyFamilyData(familyId: String) {
        try {
            // Migrate legacy families
            val legacyFamilies = legacyFamilyDao.getFirstFamily()
            if (legacyFamilies != null && ledgerDao.getFamilyVault(legacyFamilies.id) == null) {
                ledgerDao.upsertFamilyVault(FamilyVault(
                    familyId = legacyFamilies.id,
                    familyName = legacyFamilies.name,
                    inviteCode = legacyFamilies.inviteCode,
                    createdBy = legacyFamilies.createdByUserId,
                    createdAt = legacyFamilies.createdAt,
                    updatedAt = legacyFamilies.updatedAt
                ))
            }

            // Migrate legacy members
            val legacyMembers = legacyMemberDao.getPendingCreates() + legacyMemberDao.getPendingUpdates()
            for (m in legacyMembers) {
                if (ledgerDao.getMemberById(m.id) == null) {
                    ledgerDao.upsertMember(FamilyVaultMember(
                        memberId = m.id,
                        familyId = m.familyId,
                        userId = m.userId,
                        name = m.name,
                        role = m.role,
                        joinedAt = m.joinedAt,
                        updatedAt = m.updatedAt
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Migration error (safe to ignore): ${e.message}")
        }
    }

    private fun getIconForCategory(category: String): String {
        return when (category) {
            "Food & Dining", "Food" -> "Restaurant"
            "Shopping" -> "ShoppingBag"
            "Housing & Rent", "Housing" -> "Home"
            "Transportation", "Travel" -> "DirectionsCar"
            "Bills & Utilities", "Bills" -> "Receipt"
            "Entertainment" -> "Movie"
            "Healthcare", "Health" -> "MedicalServices"
            "Salary & Income", "Income", "Salary" -> "Payments"
            "Freelance / Business" -> "Work"
            "Investments" -> "TrendingUp"
            else -> "Category"
        }
    }

    private fun isValidUuid(str: String): Boolean {
        return try {
            UUID.fromString(str)
            true
        } catch (e: Exception) {
            false
        }
    }
}
