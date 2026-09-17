package com.example.data.familyledger

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import com.example.data.dao.FamilyDao
import com.example.data.dao.FamilyMemberDao
import com.example.data.dao.TransactionDao
import com.example.data.models.FamilyRole
import com.example.data.models.TransactionType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * Single authoritative repository for the Family Ledger module.
 *
 * Architecture — Three sync layers:
 *
 * 1. **Realtime (WebSocket)** — Cloud realtime events deliver
 *    INSERT / UPDATE / DELETE events instantly across all connected devices.
 *    The repository applies these directly to the local Room cache so the
 *    UI reacts without a round-trip.
 *
 * 2. **On-write Cloud Push** — Every local mutation (create / edit / delete)
 *    is immediately attempted against the remote backend. If the device is
 *    offline, the record is marked PENDING_* and retried by the flush engine.
 *
 * 3. **Full Reconciliation Pull** — On `startSync()` (and after joining a
 *    vault) we fetch the full remote state to catch any changes that arrived
 *    while Realtime was disconnected (e.g. app was in background).
 */
class FamilyLedgerRepository(
    private val ledgerDao: FamilyLedgerDao,
    private val legacyTransactionDao: TransactionDao,
    private val legacyFamilyDao: FamilyDao,
    private val legacyMemberDao: FamilyMemberDao,
    private val cloudDataSource: FamilyLedgerCloudDataSource = SupabaseFamilyLedgerDataSource()
) {
    private val tag = "FamilyLedgerRepo"
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(FamilySyncStatus.SYNCED)
    val syncStatus: StateFlow<FamilySyncStatus> = _syncStatus.asStateFlow()

    val vaultSyncEngine: FamilyVaultSyncEngine by lazy {
        FamilyVaultSyncEngine(ledgerDao, legacyTransactionDao, legacyFamilyDao, legacyMemberDao)
    }

    /** Active sync lifecycle jobs — cancelled on family switch or ViewModel clear. */
    private var realtimeJob: Job? = null
    private var reconciliationJob: Job? = null
    private var currentActiveFamilyId: String? = null

    // =========================================================================
    // REACTIVE DATA OBSERVATION (Room Flow — always fresh from local DB)
    // =========================================================================

    fun observeTransactions(familyId: String): Flow<List<LedgerTransaction>> =
        ledgerDao.getTransactionsForFamily(familyId)

    /**
     * Combines member roster with live transaction data to produce real-time
     * per-member financial stats (totalPaid, transactionCount).
     * The stats are derived reactively — no stale cache values.
     */
    fun observeMembers(familyId: String): Flow<List<FamilyVaultMember>> =
        combine(
            ledgerDao.getMembersForFamily(familyId),
            ledgerDao.getTransactionsForFamily(familyId)
        ) { members, transactions ->
            members.map { member ->
                val memberTxs = transactions.filter { tx ->
                    tx.paidByMemberId == member.userId ||
                    tx.paidByMemberId == member.memberId ||
                    tx.paidByName.equals(member.name, ignoreCase = true)
                }
                val totalPaid = memberTxs
                    .filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount }
                member.copy(
                    totalPaid = totalPaid,
                    transactionCount = memberTxs.size
                )
            }
        }

    fun observeFamilyVault(familyId: String): Flow<FamilyVault?> =
        ledgerDao.observeFamilyVault(familyId)

    fun observeAllFamilyVaults(): Flow<List<FamilyVault>> =
        ledgerDao.getAllFamilyVaults()

    // =========================================================================
    // SYNC LIFECYCLE MANAGEMENT
    // =========================================================================

    /**
     * Starts the full sync lifecycle for a family:
     * 1. Migrates any legacy Room data
     * 2. Does an initial full reconciliation pull from remote backend
     * 3. Subscribes to Realtime for live updates
     * 4. Flushes any pending offline mutations
     */
    fun startSync(familyId: String, currentUserId: String) {
        if (currentActiveFamilyId == familyId &&
            realtimeJob?.isActive == true) return

        currentActiveFamilyId = familyId
        realtimeJob?.cancel()
        reconciliationJob?.cancel()

        Log.d(tag, "SYNC_LIFECYCLE: Starting for family $familyId")

        // 1. Initial reconciliation (full pull + pending flush)
        reconciliationJob = repositoryScope.launch {
            migrateLegacyFamilyData(familyId)
            syncWithCloud(familyId, currentUserId)
        }

        // 2. Realtime subscription — runs for the lifetime of this family context
        if (cloudDataSource.isAvailable) {
            realtimeJob = repositoryScope.launch {
                cloudDataSource
                    .observeRealtimeTransactions(familyId)
                    .catch { e ->
                        Log.e(tag, "REALTIME_ERROR in collection: ${e.message}", e)
                        _syncStatus.value = FamilySyncStatus.ERROR
                    }
                    .collect { event ->
                        applyRealtimeEvent(event)
                    }
            }
            Log.d(tag, "REALTIME_SUBSCRIBED: Listening for family $familyId")
        } else {
            _syncStatus.value = FamilySyncStatus.OFFLINE
            Log.i(tag, "OFFLINE_MODE: Cloud backend not configured — local-only vault")
        }
    }

    fun stopSync() {
        realtimeJob?.cancel()
        realtimeJob = null
        reconciliationJob?.cancel()
        reconciliationJob = null
        currentActiveFamilyId = null
        Log.d(tag, "SYNC_LIFECYCLE: Stopped")
    }

    // =========================================================================
    // =========================================================================
    // REALTIME EVENT HANDLER
    // =========================================================================

    /**
     * Applies a remote Realtime event directly to the local Room cache.
     * This is the hot path for live multi-device sync — no network call needed.
     */
    private suspend fun applyRealtimeEvent(event: RealtimeLedgerEvent) {
        try {
            when (event.type) {
                RealtimeEventType.INSERT -> {
                    event.transaction?.let { tx ->
                        val local = ledgerDao.getTransactionById(tx.transactionId)
                        if (local == null || local.syncStatus == "SYNCED") {
                            ledgerDao.upsertTransaction(tx)
                            mirrorTransactionToLegacy(tx)
                            Log.d(tag, "REALTIME_APPLIED INSERT tx: ${tx.transactionId}")
                        }
                    }
                    event.member?.let { member ->
                        val local = ledgerDao.getMemberById(member.memberId)
                        if (local == null) {
                            ledgerDao.upsertMember(member)
                            mirrorMemberToLegacy(member)
                            Log.d(tag, "REALTIME_APPLIED INSERT member: ${member.memberId}")
                        }
                    }
                }
                RealtimeEventType.UPDATE -> {
                    event.transaction?.let { remote ->
                        val local = ledgerDao.getTransactionById(remote.transactionId)
                        when {
                            local == null -> {
                                ledgerDao.upsertTransaction(remote)
                                mirrorTransactionToLegacy(remote)
                            }
                            remote.syncVersion >= local.syncVersion && local.syncStatus == "SYNCED" -> {
                                ledgerDao.upsertTransaction(remote)
                                mirrorTransactionToLegacy(remote)
                                Log.d(tag, "REALTIME_APPLIED UPDATE tx: ${remote.transactionId} v${remote.syncVersion}")
                            }
                            else -> Log.d(tag, "REALTIME_SKIP local win: ${remote.transactionId}")
                        }
                    }
                    event.member?.let { remote ->
                        val local = ledgerDao.getMemberById(remote.memberId)
                        if (local == null || (local.syncStatus == "SYNCED" && remote.updatedAt >= local.updatedAt)) {
                            ledgerDao.upsertMember(remote)
                            mirrorMemberToLegacy(remote)
                        }
                    }
                }
                RealtimeEventType.DELETE -> {
                    event.transaction?.let { tx ->
                        ledgerDao.deleteTransactionPermanently(tx.transactionId)
                        mirrorDeleteToLegacy(tx.transactionId)
                        Log.d(tag, "REALTIME_APPLIED DELETE tx: ${tx.transactionId}")
                    }
                    event.member?.let { member ->
                        ledgerDao.markMemberDeleted(member.memberId)
                        mirrorDeleteMemberToLegacy(member.memberId)
                        Log.d(tag, "REALTIME_APPLIED DELETE member: ${member.memberId}")
                    }
                }
            }
            _syncStatus.value = FamilySyncStatus.SYNCED
        } catch (e: Exception) {
            Log.e(tag, "REALTIME_APPLY_ERROR: ${e.message}", e)
        }
    }

    // =========================================================================
    // FULL RECONCILIATION SYNC (PostgREST)
    // =========================================================================

    /**
     * Full bidirectional reconciliation:
     * A. Flush pending local mutations to cloud backend
     * B. Pull the authoritative remote state and merge with Last-Write-Wins
     *
     * Called once on startSync() and on demand via syncNow().
     */
    suspend fun syncWithCloud(familyId: String, currentUserId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (familyId.isBlank()) return@withContext true
            if (!cloudDataSource.isAvailable) {
                _syncStatus.value = FamilySyncStatus.OFFLINE
                return@withContext false
            }

            _syncStatus.value = FamilySyncStatus.SYNCING
            try {
                // Ensure local vault is registered on cloud first
                val localVault = ledgerDao.getFamilyVault(familyId)
                if (localVault != null) {
                    cloudDataSource.upsertFamilyVault(localVault)
                }

                // A. Push outbox (pending local mutations)
                flushPendingTransactions()
                flushPendingMembers()

                // B. Pull remote family vault
                cloudDataSource.fetchFamilyVault(familyId)?.let { remoteVault ->
                    val curLocalVault = ledgerDao.getFamilyVault(familyId)
                    if (curLocalVault == null || remoteVault.updatedAt >= curLocalVault.updatedAt) {
                        ledgerDao.upsertFamilyVault(remoteVault)
                        mirrorVaultToLegacy(remoteVault)
                    }
                }

                // C. Pull remote members (LWW merge)
                cloudDataSource.fetchFamilyMembers(familyId).forEach { remote ->
                    val local = ledgerDao.getMemberById(remote.memberId)
                        ?: ledgerDao.getMemberByFamilyAndUser(remote.familyId, remote.userId)
                    when {
                        local == null -> {
                            ledgerDao.upsertMember(remote)
                            mirrorMemberToLegacy(remote)
                        }
                        local.syncStatus == "SYNCED" && remote.updatedAt >= local.updatedAt -> {
                            val merged = local.copy(name = remote.name, role = remote.role, updatedAt = remote.updatedAt)
                            ledgerDao.upsertMember(merged)
                            mirrorMemberToLegacy(merged)
                        }
                    }
                }

                // D. Pull remote transactions (LWW merge using syncVersion)
                cloudDataSource.fetchFamilyTransactions(familyId).forEach { remote ->
                    val local = ledgerDao.getTransactionById(remote.transactionId)
                    when {
                        local == null -> {
                            if (!remote.isDeleted) {
                                ledgerDao.upsertTransaction(remote)
                                mirrorTransactionToLegacy(remote)
                            }
                        }
                        remote.isDeleted -> {
                            ledgerDao.deleteTransactionPermanently(remote.transactionId)
                            mirrorDeleteToLegacy(remote.transactionId)
                        }
                        local.syncStatus == "SYNCED" && remote.syncVersion >= local.syncVersion -> {
                            ledgerDao.upsertTransaction(remote)
                            mirrorTransactionToLegacy(remote)
                        }
                    }
                }

                _syncStatus.value = FamilySyncStatus.SYNCED
                Log.d(tag, "FULL_SYNC completed for family $familyId")
                true
            } catch (e: Exception) {
                Log.e(tag, "SYNC_ERROR syncWithCloud: ${e.message}", e)
                _syncStatus.value = FamilySyncStatus.ERROR
                false
            }
        }

    // =========================================================================
    // OUTBOX FLUSH (pending offline mutations → cloud backend)
    // =========================================================================

    private suspend fun flushPendingTransactions() {
        // Pending creates
        ledgerDao.getPendingCreateTransactions().forEach { tx ->
            if (cloudDataSource.upsertTransaction(tx)) {
                ledgerDao.upsertTransaction(tx.copy(syncStatus = "SYNCED"))
                Log.d(tag, "FLUSH_CREATE tx: ${tx.transactionId}")
            }
        }
        // Pending updates
        ledgerDao.getPendingUpdateTransactions().forEach { tx ->
            if (cloudDataSource.upsertTransaction(tx)) {
                ledgerDao.upsertTransaction(tx.copy(syncStatus = "SYNCED"))
                Log.d(tag, "FLUSH_UPDATE tx: ${tx.transactionId}")
            }
        }
        // Pending deletes (soft-delete on cloud, hard-delete locally)
        ledgerDao.getPendingDeleteTransactions().forEach { tx ->
            if (cloudDataSource.softDeleteTransaction(tx.transactionId)) {
                ledgerDao.deleteTransactionPermanently(tx.transactionId)
                Log.d(tag, "FLUSH_DELETE tx: ${tx.transactionId}")
            }
        }
        // Offline queue (written without cloud) — treat as creates
        ledgerDao.getOfflineTransactions().forEach { tx ->
            if (cloudDataSource.upsertTransaction(tx)) {
                ledgerDao.upsertTransaction(tx.copy(syncStatus = "SYNCED"))
                Log.d(tag, "FLUSH_OFFLINE tx: ${tx.transactionId}")
            }
        }
    }

    private suspend fun flushPendingMembers() {
        ledgerDao.getPendingCreateMembers().forEach { member ->
            if (cloudDataSource.upsertFamilyMember(member)) {
                ledgerDao.upsertMember(member.copy(syncStatus = "SYNCED"))
            }
        }
        ledgerDao.getPendingUpdateMembers().forEach { member ->
            if (cloudDataSource.upsertFamilyMember(member)) {
                ledgerDao.upsertMember(member.copy(syncStatus = "SYNCED"))
            }
        }
        ledgerDao.getPendingDeleteMembers().forEach { member ->
            if (cloudDataSource.deleteFamilyMember(member.memberId)) {
                ledgerDao.markMemberDeleted(member.memberId)
            }
        }
    }

    // =========================================================================
    // TRANSACTION WRITE OPERATIONS (Optimistic UI)
    // =========================================================================

    /**
     * Creates a transaction with optimistic local write:
     * 1. Writes to Room immediately (UI updates instantly)
     * 2. Pushes to cloud backend in the background
     * 3. If push succeeds → marks SYNCED; if fails → stays PENDING_CREATE for flush
     */
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
        val pendingStatus = if (cloudDataSource.isAvailable) "PENDING_CREATE" else "OFFLINE"

        val transaction = LedgerTransaction(
            transactionId = txId,
            familyId = familyId,
            title = title.trim(),
            description = description.trim(),
            amount = amount,
            category = category,
            categoryIcon = categoryToIcon(category),
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
            syncStatus = pendingStatus,
            syncVersion = 1L
        )

        // Instant local write
        ledgerDao.upsertTransaction(transaction)
        mirrorTransactionToLegacy(transaction)
        Log.d(tag, "LOCAL_WRITE CREATE: $txId ($title ₹$amount)")

        // Background cloud push
        repositoryScope.launch {
            if (cloudDataSource.isAvailable) {
                val success = cloudDataSource.upsertTransaction(transaction)
                if (success) {
                    val synced = transaction.copy(syncStatus = "SYNCED")
                    ledgerDao.upsertTransaction(synced)
                    mirrorTransactionToLegacy(synced)
                    _syncStatus.value = FamilySyncStatus.SYNCED
                    Log.d(tag, "CLOUD_PUSH CREATE success: $txId")
                } else {
                    _syncStatus.value = FamilySyncStatus.ERROR
                }
            }
        }

        transaction
    }

    /**
     * Edits a transaction with optimistic local write.
     * syncVersion is incremented to signal newer state to conflict resolution.
     */
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
        val pendingStatus = if (cloudDataSource.isAvailable) "PENDING_UPDATE" else "OFFLINE"

        val updated = existing.copy(
            title = title.trim(),
            description = description.trim(),
            amount = amount,
            category = category,
            categoryIcon = categoryToIcon(category),
            type = type,
            paymentMethod = paymentMethod,
            paidByMemberId = paidByMemberId,
            paidByName = paidByName,
            dateMillis = dateMillis,
            updatedAt = now,
            lastModifiedBy = currentUserId,
            syncStatus = pendingStatus,
            syncVersion = existing.syncVersion + 1L
        )

        ledgerDao.upsertTransaction(updated)
        mirrorTransactionToLegacy(updated)
        Log.d(tag, "LOCAL_WRITE UPDATE: ${updated.transactionId} v${updated.syncVersion}")

        repositoryScope.launch {
            if (cloudDataSource.isAvailable) {
                val success = cloudDataSource.upsertTransaction(updated)
                if (success) {
                    val synced = updated.copy(syncStatus = "SYNCED")
                    ledgerDao.upsertTransaction(synced)
                    mirrorTransactionToLegacy(synced)
                    _syncStatus.value = FamilySyncStatus.SYNCED
                } else {
                    _syncStatus.value = FamilySyncStatus.ERROR
                }
            }
        }

        updated
    }

    /**
     * Soft-deletes a transaction: marks locally as deleted, then attempts
     * cloud soft-delete. On success, removes from local Room permanently.
     */
    suspend fun deleteTransaction(transactionId: String, currentUserId: String): Boolean =
        withContext(Dispatchers.IO) {
            ledgerDao.getTransactionById(transactionId) ?: return@withContext false
            val now = System.currentTimeMillis()

            ledgerDao.markTransactionDeleted(transactionId, now)
            mirrorDeleteToLegacy(transactionId)
            Log.d(tag, "LOCAL_DELETE: $transactionId")

            repositoryScope.launch {
                if (cloudDataSource.isAvailable) {
                    val success = cloudDataSource.softDeleteTransaction(transactionId)
                    if (success) {
                        ledgerDao.deleteTransactionPermanently(transactionId)
                        mirrorDeleteToLegacy(transactionId)
                        _syncStatus.value = FamilySyncStatus.SYNCED
                    } else {
                        _syncStatus.value = FamilySyncStatus.ERROR
                    }
                }
            }
            true
        }

    // =========================================================================
    // FAMILY VAULT OPERATIONS
    // =========================================================================

    fun generateInviteCode(): String {
        // Unambiguous charset (no 0/O, 1/I)
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "FAM-" + (1..6).map { chars.random() }.joinToString("")
    }

    /**
     * Returns the first valid existing vault or creates a default one.
     * Called once on ViewModel init.
     */
    suspend fun getOrCreateDefaultVault(
        currentUserId: String,
        currentUserName: String
    ): FamilyVault = withContext(Dispatchers.IO) {
        // If there's already a valid vault in Room, return it and ensure it's mirrored & pushed
        val existing = ledgerDao.getAllFamilyVaultsOnce()
            .firstOrNull { !it.isDeleted && isValidUuid(it.familyId) }
        if (existing != null) {
            mirrorVaultToLegacy(existing)
            val members = ledgerDao.getMembersForFamilyOnce(existing.familyId)
            for (m in members) {
                mirrorMemberToLegacy(m)
            }
            repositoryScope.launch {
                cloudDataSource.upsertFamilyVault(existing)
            }
            return@withContext existing
        }

        // Create a fresh default vault
        createFamilyVault(
            familyName = if (currentUserName.isNotBlank() && currentUserName != "You")
                "$currentUserName's Family Vault" else "My Family Vault",
            currentUserId = currentUserId,
            currentUserName = currentUserName
        )
    }

    suspend fun createFamilyVault(
        familyName: String,
        currentUserId: String,
        currentUserName: String
    ): FamilyVault = withContext(Dispatchers.IO) {
        val familyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val vault = FamilyVault(
            familyId = familyId,
            familyName = familyName.trim(),
            inviteCode = generateInviteCode(),
            createdBy = currentUserId,
            createdAt = now,
            updatedAt = now
        )
        ledgerDao.upsertFamilyVault(vault)
        mirrorVaultToLegacy(vault)

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
        mirrorMemberToLegacy(ownerMember)

        // Asynchronously push to cloud backend
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
        val now = System.currentTimeMillis()
        val member = FamilyVaultMember(
            memberId = UUID.randomUUID().toString(),
            familyId = familyId,
            userId = UUID.randomUUID().toString(),
            name = name.trim(),
            role = role,
            joinedAt = now,
            updatedAt = now,
            syncStatus = if (cloudDataSource.isAvailable) "PENDING_CREATE" else "OFFLINE"
        )
        ledgerDao.upsertMember(member)
        mirrorMemberToLegacy(member)

        repositoryScope.launch { cloudDataSource.upsertFamilyMember(member) }
        Log.d(tag, "MEMBER_ADDED: ${member.memberId} ($name)")
        member
    }

    suspend fun removeMember(memberId: String): Boolean = withContext(Dispatchers.IO) {
        ledgerDao.getMemberById(memberId) ?: return@withContext false
        ledgerDao.markMemberDeleted(memberId)
        mirrorDeleteMemberToLegacy(memberId)
        repositoryScope.launch { cloudDataSource.deleteFamilyMember(memberId) }
        true
    }

    /**
     * Directly links and joins a Family Vault using scanned QR metadata.
     * Guaranteed to work immediately offline or online with zero configuration.
     */
    suspend fun joinFamilyVaultDirect(
        familyId: String,
        familyName: String,
        inviteCode: String,
        currentUserId: String,
        currentUserName: String
    ): Result<FamilyVault> = withContext(Dispatchers.IO) {
        val canonicalFamilyId = if (isValidUuid(familyId)) familyId else UUID.nameUUIDFromBytes(familyId.toByteArray()).toString()
        val now = System.currentTimeMillis()
        val vault = FamilyVault(
            familyId = canonicalFamilyId,
            familyName = familyName.ifBlank { "Family Vault" },
            inviteCode = inviteCode.ifBlank { "FAM-" + canonicalFamilyId.take(6).uppercase() },
            createdBy = "",
            createdAt = now,
            updatedAt = now,
            syncStatus = "SYNCED"
        )
        ledgerDao.upsertFamilyVault(vault)
        mirrorVaultToLegacy(vault)

        var member = ledgerDao.getMemberByFamilyAndUser(canonicalFamilyId, currentUserId)
        if (member == null) {
            val newMember = FamilyVaultMember(
                memberId = UUID.randomUUID().toString(),
                familyId = canonicalFamilyId,
                userId = currentUserId,
                name = currentUserName.ifBlank { "Family Member" },
                role = FamilyRole.MEMBER,
                joinedAt = now,
                updatedAt = now
            )
            ledgerDao.upsertMember(newMember)
            mirrorMemberToLegacy(newMember)
            repositoryScope.launch {
                cloudDataSource.upsertFamilyVault(vault)
                cloudDataSource.upsertFamilyMember(newMember)
            }
        } else {
            mirrorMemberToLegacy(member)
        }

        syncWithCloud(canonicalFamilyId, currentUserId)
        Result.success(vault)
    }

    /**
     * Looks up a family by invite code on cloud backend, joins it locally, and
     * performs an immediate full sync for that family.
     */
    suspend fun joinFamilyByInviteCode(
        inviteCode: String,
        currentUserId: String,
        currentUserName: String
    ): Result<FamilyVault> = withContext(Dispatchers.IO) {
        val cleanCode = inviteCode.trim().uppercase()
        if (cleanCode.isBlank()) return@withContext Result.failure(
            Exception("Please enter a valid invite code")
        )

        // 1. Try cloud lookup
        var targetVault = cloudDataSource.fetchFamilyByInviteCode(cleanCode)

        // 2. Fallback to local database if offline or previously stored
        if (targetVault == null) {
            targetVault = ledgerDao.getAllFamilyVaultsOnce().firstOrNull {
                it.inviteCode.equals(cleanCode, ignoreCase = true) ||
                it.inviteCode.equals("FAM-$cleanCode", ignoreCase = true) ||
                it.familyId.equals(cleanCode, ignoreCase = true)
            }
        }

        if (targetVault == null) {
            return@withContext Result.failure(
                Exception("Family Vault with code '$cleanCode' was not found. Please ensure the other device is online or scan its QR code.")
            )
        }

        ledgerDao.upsertFamilyVault(targetVault)
        mirrorVaultToLegacy(targetVault)

        // Add joining user as member if not already present
        var member = ledgerDao.getMemberByFamilyAndUser(targetVault.familyId, currentUserId)
        if (member == null) {
            val now = System.currentTimeMillis()
            val newMember = FamilyVaultMember(
                memberId = UUID.randomUUID().toString(),
                familyId = targetVault.familyId,
                userId = currentUserId,
                name = currentUserName.ifBlank { "Family Member" },
                role = FamilyRole.MEMBER,
                joinedAt = now,
                updatedAt = now
            )
            ledgerDao.upsertMember(newMember)
            mirrorMemberToLegacy(newMember)
            repositoryScope.launch { cloudDataSource.upsertFamilyMember(newMember) }
        } else {
            mirrorMemberToLegacy(member)
        }

        // Immediate reconciliation pull
        syncWithCloud(targetVault.familyId, currentUserId)
        Log.d(tag, "JOIN_VAULT: ${targetVault.familyId} (${targetVault.familyName})")
        Result.success(targetVault)
    }

    // =========================================================================
    // LEGACY MIGRATION
    // =========================================================================

    /**
     * One-time migration of legacy FamilyEntity / FamilyMemberEntity data into
     * the new family_vaults / family_vault_members tables.
     * Safe to call on every launch — uses null checks to avoid duplicates.
     */
    private suspend fun migrateLegacyFamilyData(familyId: String) {
        try {
            val legacyFamily = legacyFamilyDao.getFirstFamily()
            if (legacyFamily != null && ledgerDao.getFamilyVault(legacyFamily.id) == null) {
                ledgerDao.upsertFamilyVault(
                    FamilyVault(
                        familyId = legacyFamily.id,
                        familyName = legacyFamily.name,
                        inviteCode = legacyFamily.inviteCode,
                        createdBy = legacyFamily.createdByUserId,
                        createdAt = legacyFamily.createdAt,
                        updatedAt = legacyFamily.updatedAt
                    )
                )
                Log.d(tag, "MIGRATION: Family ${legacyFamily.id} migrated to vault")
            }

            val legacyMembers = legacyMemberDao.getPendingCreates() + legacyMemberDao.getPendingUpdates()
            legacyMembers.forEach { m ->
                if (ledgerDao.getMemberById(m.id) == null) {
                    ledgerDao.upsertMember(
                        FamilyVaultMember(
                            memberId = m.id,
                            familyId = m.familyId,
                            userId = m.userId,
                            name = m.name,
                            role = m.role,
                            joinedAt = m.joinedAt,
                            updatedAt = m.updatedAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "MIGRATION_WARN: ${e.message}")
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun categoryToIcon(category: String): String = when (category) {
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

    // =========================================================================
    // LEGACY DAO MIRRORING HELPERS
    // =========================================================================

    private suspend fun mirrorTransactionToLegacy(tx: LedgerTransaction) {
        try {
            val existing = legacyTransactionDao.getTransactionByServerId(tx.transactionId)
            val entity = com.example.data.models.TransactionEntity(
                id = existing?.id ?: 0L,
                title = tx.title,
                amount = tx.amount,
                type = tx.type,
                category = tx.category,
                categoryIconName = categoryToIcon(tx.category),
                paymentMethod = tx.paymentMethod,
                note = tx.description,
                financeScope = com.example.data.models.FinanceScope.FAMILY,
                familyId = tx.familyId,
                createdByUserId = tx.paidByMemberId.ifBlank { tx.createdBy },
                dateMillis = tx.dateMillis,
                serverId = tx.transactionId,
                syncStatus = "SYNCED",
                updatedAt = tx.updatedAt,
                isDeleted = tx.isDeleted
            )
            if (existing == null) {
                legacyTransactionDao.insertTransaction(entity)
            } else {
                legacyTransactionDao.updateTransaction(entity)
            }
        } catch (e: Exception) {
            Log.w(tag, "mirrorTransactionToLegacy error: ${e.message}")
        }
    }

    private suspend fun mirrorDeleteToLegacy(txId: String) {
        try {
            val existing = legacyTransactionDao.getTransactionByServerId(txId)
            if (existing != null) {
                legacyTransactionDao.deleteTransaction(existing)
            }
        } catch (e: Exception) {
            Log.w(tag, "mirrorDeleteToLegacy error: ${e.message}")
        }
    }

    private suspend fun mirrorMemberToLegacy(member: FamilyVaultMember) {
        try {
            val existing = legacyMemberDao.getMemberById(member.memberId)
            val entity = com.example.data.models.FamilyMemberEntity(
                id = member.memberId,
                familyId = member.familyId,
                userId = member.userId,
                name = member.name,
                role = member.role,
                joinedAt = member.joinedAt,
                syncStatus = "SYNCED",
                isDeleted = member.isDeleted
            )
            if (existing == null) {
                legacyMemberDao.insertMember(entity)
            } else {
                legacyMemberDao.updateMember(entity)
            }
        } catch (e: Exception) {
            Log.w(tag, "mirrorMemberToLegacy error: ${e.message}")
        }
    }

    private suspend fun mirrorDeleteMemberToLegacy(memberId: String) {
        try {
            val existing = legacyMemberDao.getMemberById(memberId)
            if (existing != null) {
                legacyMemberDao.updateMember(existing.copy(isDeleted = true, syncStatus = "PENDING_DELETE"))
            }
        } catch (e: Exception) {
            Log.w(tag, "mirrorDeleteMemberToLegacy error: ${e.message}")
        }
    }

    private suspend fun mirrorVaultToLegacy(vault: FamilyVault) {
        try {
            val existing = legacyFamilyDao.getFamilyById(vault.familyId)
            val entity = com.example.data.models.FamilyEntity(
                id = vault.familyId,
                name = vault.familyName,
                createdByUserId = vault.createdBy,
                createdAt = vault.createdAt,
                updatedAt = vault.updatedAt,
                inviteCode = vault.inviteCode,
                serverId = vault.familyId,
                syncStatus = "SYNCED"
            )
            if (existing == null) {
                legacyFamilyDao.insertFamily(entity)
            } else {
                legacyFamilyDao.updateFamily(entity)
            }
        } catch (e: Exception) {
            Log.w(tag, "mirrorVaultToLegacy error: ${e.message}")
        }
    }

    suspend fun getFamilyVault(familyId: String): FamilyVault? = withContext(Dispatchers.IO) {
        ledgerDao.getFamilyVault(familyId)
    }

    suspend fun getMembersForFamilyOnce(familyId: String): List<FamilyVaultMember> = withContext(Dispatchers.IO) {
        ledgerDao.getMembersForFamilyOnce(familyId)
    }

    suspend fun generateVaultQrBitmap(familyId: String, sizePx: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        val vault = ledgerDao.getFamilyVault(familyId) ?: return@withContext null
        val memberCount = ledgerDao.getMembersForFamilyOnce(familyId).size.coerceAtLeast(1)
        val payload = vaultSyncEngine.createQrSyncPayload(vault, memberCount)
        vaultSyncEngine.generateQrBitmap(payload, sizePx)
    }

    fun parseScannedVaultQr(rawText: String): VaultSyncQrData? =
        vaultSyncEngine.parseScannedPayload(rawText)

    suspend fun exportVaultFileIntent(context: Context, familyId: String): Intent? =
        vaultSyncEngine.createShareVaultIntent(context, familyId)

    suspend fun importVaultSyncPayload(jsonString: String, currentUserId: String, currentUserName: String): Result<VaultImportSummary> =
        vaultSyncEngine.importVaultPayload(jsonString, currentUserId, currentUserName)

    private fun isValidUuid(str: String): Boolean = try {
        UUID.fromString(str); true
    } catch (e: Exception) { false }
}
