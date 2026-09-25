package com.example.data.familyledger

import android.util.Log
import com.example.data.models.FamilyRole
import com.example.data.models.TransactionType
import com.example.data.network.SupabaseClientConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant
import java.util.UUID

class SupabaseFamilyLedgerDataSource : FamilyLedgerCloudDataSource {
    private val tag = "SupabaseFamilyDS"
    private val json = Json { ignoreUnknownKeys = true }

    override val isAvailable: Boolean
        get() = SupabaseClientConfig.isConfigured

    override fun observeRealtimeTransactions(familyId: String): Flow<RealtimeLedgerEvent> {
        if (!isAvailable) return emptyFlow()

        return callbackFlow {
            try {
                val channelName = "family-ledger-$familyId"
                val channel = SupabaseClientConfig.supabase.realtime.channel(channelName)

                val txFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "transactions"
                }

                val memberFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "family_members"
                }

                channel.subscribe()
                Log.d(tag, "Subscribed to realtime channel: $channelName")

                launch {
                    txFlow.collect { action ->
                        when (action) {
                            is PostgresAction.Insert -> {
                                val record = action.record
                                try {
                                    val dto = json.decodeFromJsonElement<CloudLedgerTransactionDto>(record)
                                    val domainTx = mapDtoToDomain(dto)
                                    trySend(RealtimeLedgerEvent(RealtimeEventType.INSERT, transaction = domainTx))
                                } catch (e: Exception) {
                                    Log.w(tag, "Failed to decode realtime INSERT: ${e.message}")
                                }
                            }
                            is PostgresAction.Update -> {
                                val record = action.record
                                try {
                                    val dto = json.decodeFromJsonElement<CloudLedgerTransactionDto>(record)
                                    val domainTx = mapDtoToDomain(dto)
                                    trySend(RealtimeLedgerEvent(RealtimeEventType.UPDATE, transaction = domainTx))
                                } catch (e: Exception) {
                                    Log.w(tag, "Failed to decode realtime UPDATE: ${e.message}")
                                }
                            }
                            is PostgresAction.Delete -> {
                                val oldRecord = action.oldRecord
                                val txId = oldRecord["id"]?.toString()?.replace("\"", "") ?: ""
                                if (txId.isNotBlank()) {
                                    val stubTx = LedgerTransaction(
                                        transactionId = txId,
                                        familyId = familyId,
                                        title = "",
                                        amount = 0.0,
                                        isDeleted = true
                                    )
                                    trySend(RealtimeLedgerEvent(RealtimeEventType.DELETE, transaction = stubTx))
                                }
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    memberFlow.collect { action ->
                        when (action) {
                            is PostgresAction.Insert, is PostgresAction.Update -> {
                                val record = action.record
                                try {
                                    val dto = json.decodeFromJsonElement<CloudFamilyMemberDto>(record)
                                    if (dto.familyId == familyId) {
                                        val domainMember = FamilyVaultMember(
                                            memberId = dto.id,
                                            familyId = dto.familyId,
                                            userId = dto.userId,
                                            name = dto.name ?: dto.displayName ?: "Member",
                                            role = try { FamilyRole.valueOf(dto.role) } catch (e: Exception) { FamilyRole.MEMBER },
                                            joinedAt = parseIsoTimestamp(dto.joinedAt),
                                            updatedAt = parseIsoTimestamp(dto.updatedAt),
                                            isDeleted = !dto.isActive,
                                            syncStatus = "SYNCED"
                                        )
                                        val eventType = if (action is PostgresAction.Insert) RealtimeEventType.INSERT else RealtimeEventType.UPDATE
                                        trySend(RealtimeLedgerEvent(eventType, member = domainMember))
                                    }
                                } catch (e: Exception) {
                                    Log.w(tag, "Failed to decode realtime member update: ${e.message}")
                                }
                            }
                            is PostgresAction.Delete -> {
                                val oldRecord = action.oldRecord
                                val memId = oldRecord["id"]?.toString()?.replace("\"", "") ?: ""
                                if (memId.isNotBlank()) {
                                    val stubMember = FamilyVaultMember(
                                        memberId = memId,
                                        familyId = familyId,
                                        userId = "",
                                        name = "",
                                        isDeleted = true
                                    )
                                    trySend(RealtimeLedgerEvent(RealtimeEventType.DELETE, member = stubMember))
                                }
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in realtime subscription: ${e.message}", e)
            }

            awaitClose {
                Log.d(tag, "Closing realtime subscription for family: $familyId")
            }
        }
    }

    override suspend fun fetchFamilyVault(familyId: String): FamilyVault? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        try {
            val dtoList = SupabaseClientConfig.supabase.postgrest["families"]
                .select(columns = Columns.ALL) {
                    filter { eq("id", familyId) }
                }
                .decodeList<CloudFamilyVaultDto>()

            dtoList.firstOrNull()?.let { dto ->
                FamilyVault(
                    familyId = dto.id,
                    familyName = dto.name,
                    inviteCode = dto.inviteCode ?: "",
                    createdBy = dto.createdBy ?: "",
                    createdAt = parseIsoTimestamp(dto.createdAt),
                    updatedAt = parseIsoTimestamp(dto.updatedAt),
                    isDeleted = false,
                    syncStatus = "SYNCED"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchFamilyVault error: ${e.message}", e)
            null
        }
    }

    override suspend fun fetchFamilyByInviteCode(inviteCode: String): FamilyVault? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        val clean = FamilyInviteCodeUtils.extractInviteCode(inviteCode).ifBlank { inviteCode.trim().uppercase() }
        if (clean.isBlank()) return@withContext null

        val candidateCodes = listOf(
            clean,
            if (clean.startsWith("FAM-")) clean.removePrefix("FAM-") else "FAM-$clean",
            clean.lowercase(),
            clean.replace("-", "")
        ).distinct()

        try {
            for (code in candidateCodes) {
                var list = SupabaseClientConfig.supabase.postgrest["families"]
                    .select(columns = Columns.ALL) {
                        filter { eq("invite_code", code) }
                    }
                    .decodeList<CloudFamilyVaultDto>()

                if (list.isEmpty()) {
                    list = SupabaseClientConfig.supabase.postgrest["families"]
                        .select(columns = Columns.ALL) {
                            filter { ilike("invite_code", code) }
                        }
                        .decodeList<CloudFamilyVaultDto>()
                }

                val dto = list.firstOrNull()
                if (dto != null) {
                    return@withContext FamilyVault(
                        familyId = dto.id,
                        familyName = dto.name,
                        inviteCode = dto.inviteCode ?: clean,
                        createdBy = dto.createdBy ?: "",
                        createdAt = parseIsoTimestamp(dto.createdAt),
                        updatedAt = parseIsoTimestamp(dto.updatedAt),
                        isDeleted = false,
                        syncStatus = "SYNCED"
                    )
                }
            }

            val candidateUuids = mutableListOf<String>()
            if (isValidUuid(clean)) candidateUuids.add(clean)
            candidateUuids.add(UUID.nameUUIDFromBytes(clean.toByteArray()).toString())

            for (targetId in candidateUuids.distinct()) {
                val listById = SupabaseClientConfig.supabase.postgrest["families"]
                    .select(columns = Columns.ALL) {
                        filter { eq("id", targetId) }
                    }
                    .decodeList<CloudFamilyVaultDto>()

                val dto = listById.firstOrNull()
                if (dto != null) {
                    return@withContext FamilyVault(
                        familyId = dto.id,
                        familyName = dto.name,
                        inviteCode = dto.inviteCode ?: clean,
                        createdBy = dto.createdBy ?: "",
                        createdAt = parseIsoTimestamp(dto.createdAt),
                        updatedAt = parseIsoTimestamp(dto.updatedAt),
                        isDeleted = false,
                        syncStatus = "SYNCED"
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(tag, "fetchFamilyByInviteCode error: ${e.message}", e)
            null
        }
    }

    override suspend fun fetchFamilyMembers(familyId: String): List<FamilyVaultMember> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext emptyList()
        try {
            val dtoList = SupabaseClientConfig.supabase.postgrest["family_members"]
                .select(columns = Columns.ALL) {
                    filter { eq("family_id", familyId) }
                }
                .decodeList<CloudFamilyMemberDto>()

            dtoList.map { dto ->
                FamilyVaultMember(
                    memberId = dto.id,
                    familyId = dto.familyId,
                    userId = dto.userId,
                    name = dto.name ?: dto.displayName ?: "Member",
                    role = try { FamilyRole.valueOf(dto.role) } catch (e: Exception) { FamilyRole.MEMBER },
                    joinedAt = parseIsoTimestamp(dto.joinedAt),
                    updatedAt = parseIsoTimestamp(dto.updatedAt),
                    isDeleted = false,
                    syncStatus = "SYNCED"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchFamilyMembers error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun fetchFamilyTransactions(familyId: String): List<LedgerTransaction> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext emptyList()
        try {
            val dtoList = SupabaseClientConfig.supabase.postgrest["transactions"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("family_id", familyId)
                        eq("finance_scope", "FAMILY")
                        eq("is_deleted", false)
                    }
                }
                .decodeList<CloudLedgerTransactionDto>()

            dtoList.map { dto -> mapDtoToDomain(dto) }
        } catch (e: Exception) {
            Log.e(tag, "fetchFamilyTransactions error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun upsertFamilyVault(vault: FamilyVault): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        val canonicalFamilyId = if (isValidUuid(vault.familyId)) {
            vault.familyId
        } else {
            UUID.nameUUIDFromBytes(vault.familyId.toByteArray()).toString()
        }
        val cleanInviteCode = vault.inviteCode.ifBlank { "FAM-" + canonicalFamilyId.take(6).uppercase() }

        val candidateCreatedBy = vault.createdBy.takeIf { it.isNotBlank() && isValidUuid(it) }
        val dto = CloudFamilyVaultDto(
            id = canonicalFamilyId,
            name = vault.familyName.ifBlank { "Family Vault" },
            inviteCode = cleanInviteCode,
            createdBy = candidateCreatedBy,
            createdAt = Instant.ofEpochMilli(vault.createdAt).toString(),
            updatedAt = Instant.ofEpochMilli(vault.updatedAt).toString()
        )

        try {
            SupabaseClientConfig.supabase.postgrest["families"].upsert(dto)
            true
        } catch (e: Exception) {
            // If foreign key constraint on profiles fails (or any FK issue), retry with createdBy = null
            Log.w(tag, "upsertFamilyVault failed with createdBy, retrying with createdBy=null: ${e.message}")
            try {
                val nullCreatedByDto = dto.copy(createdBy = null)
                SupabaseClientConfig.supabase.postgrest["families"].upsert(nullCreatedByDto)
                true
            } catch (e2: Exception) {
                Log.e(tag, "upsertFamilyVault fallback error: ${e2.message}", e2)
                false
            }
        }
    }

    override suspend fun upsertFamilyMember(member: FamilyVaultMember): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        val canonicalFamilyId = if (isValidUuid(member.familyId)) {
            member.familyId
        } else {
            UUID.nameUUIDFromBytes(member.familyId.toByteArray()).toString()
        }
        val validMemberId = if (isValidUuid(member.memberId)) member.memberId else UUID.randomUUID().toString()
        val validUserId = member.userId.takeIf { isValidUuid(it) } ?: UUID.randomUUID().toString()
        try {
            val dto = CloudFamilyMemberDto(
                id = validMemberId,
                familyId = canonicalFamilyId,
                userId = validUserId,
                name = member.name.ifBlank { "Family Member" },
                displayName = member.name.ifBlank { "Family Member" },
                role = member.role.name,
                isActive = true,
                joinedAt = Instant.ofEpochMilli(member.joinedAt).toString(),
                updatedAt = Instant.ofEpochMilli(member.updatedAt).toString()
            )
            SupabaseClientConfig.supabase.postgrest["family_members"].upsert(dto)
            true
        } catch (e: Exception) {
            Log.e(tag, "upsertFamilyMember error: ${e.message}", e)
            false
        }
    }

    override suspend fun deleteFamilyMember(memberId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            SupabaseClientConfig.supabase.postgrest["family_members"].delete {
                filter { eq("id", memberId) }
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "deleteFamilyMember error: ${e.message}", e)
            false
        }
    }

    override suspend fun upsertTransaction(tx: LedgerTransaction): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            val currentUser = SupabaseClientConfig.supabase.auth.currentUserOrNull()
            val authUserId = currentUser?.id ?: tx.createdBy.takeIf { isValidUuid(it) } ?: UUID.randomUUID().toString()

            val dto = CloudLedgerTransactionDto(
                id = tx.transactionId,
                familyId = tx.familyId,
                financeScope = "FAMILY",
                title = tx.title.ifBlank { "Transaction" },
                description = tx.description,
                amount = tx.amount,
                transactionType = tx.type.name,
                category = tx.category.ifBlank { "Other" },
                categoryId = tx.category.takeIf { isValidUuid(it) } ?: "Other",
                categoryName = tx.category.ifBlank { "Other" },
                paymentMethod = tx.paymentMethod,
                userId = authUserId,
                paidByMemberId = tx.paidByMemberId.takeIf { it.isNotBlank() && isValidUuid(it) },
                paidByName = tx.paidByName,
                syncVersion = tx.syncVersion,
                transactionDate = Instant.ofEpochMilli(tx.dateMillis).toString(),
                createdAt = Instant.ofEpochMilli(tx.createdAt).toString(),
                updatedAt = Instant.ofEpochMilli(tx.updatedAt).toString(),
                isDeleted = tx.isDeleted
            )
            SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
            true
        } catch (e: Exception) {
            Log.e(tag, "upsertTransaction error: ${e.message}", e)
            false
        }
    }

    override suspend fun softDeleteTransaction(transactionId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            SupabaseClientConfig.supabase.postgrest["transactions"].update({
                set("is_deleted", true)
                set("updated_at", Instant.now().toString())
            }) {
                filter { eq("id", transactionId) }
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "softDeleteTransaction error: ${e.message}", e)
            false
        }
    }

    private fun mapDtoToDomain(dto: CloudLedgerTransactionDto): LedgerTransaction {
        val effectiveType = try {
            TransactionType.valueOf(dto.transactionType.ifBlank { dto.type ?: "EXPENSE" })
        } catch (e: Exception) {
            try {
                TransactionType.valueOf(dto.type ?: "EXPENSE")
            } catch (e2: Exception) {
                TransactionType.EXPENSE
            }
        }
        val effectiveCategory = dto.category ?: dto.categoryName ?: dto.categoryId ?: "General"
        val effectiveTitle = dto.title?.takeIf { it.isNotBlank() }
            ?: dto.description.takeIf { it.isNotBlank() }
            ?: "Transaction"

        return LedgerTransaction(
            transactionId = dto.id,
            familyId = dto.familyId,
            title = effectiveTitle,
            description = dto.description,
            amount = dto.amount,
            category = effectiveCategory,
            categoryIcon = getIconForCategory(effectiveCategory),
            type = effectiveType,
            paymentMethod = dto.paymentMethod,
            paidByMemberId = dto.paidByMemberId ?: "",
            paidByName = dto.paidByName ?: "Member",
            dateMillis = parseIsoTimestamp(dto.transactionDate),
            createdAt = parseIsoTimestamp(dto.createdAt),
            updatedAt = parseIsoTimestamp(dto.updatedAt),
            createdBy = dto.userId,
            lastModifiedBy = dto.userId,
            isDeleted = dto.isDeleted,
            syncStatus = "SYNCED",
            syncVersion = dto.syncVersion
        )
    }

    private fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun isValidUuid(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        return try {
            java.util.UUID.fromString(str)
            true
        } catch (e: Exception) {
            false
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
}
