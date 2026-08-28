package com.example.data.familyledger

import android.util.Log
import com.example.data.models.FamilyRole
import com.example.data.models.TransactionType
import com.example.data.network.SupabaseClientConfig
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

/**
 * Dedicated, production-grade cloud data source for Family Ledger.
 * Provides PostgREST cloud operations and realtime streaming directly from Supabase.
 */
class SupabaseFamilyLedgerDataSource {

    private val tag = "FamilyLedgerCloud"

    val isAvailable: Boolean
        get() = SupabaseClientConfig.isConfigured

    // --- FAMILY VAULT OPERATIONS ---

    suspend fun fetchFamilyVault(familyId: String): FamilyVault? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        try {
            val list = SupabaseClientConfig.supabase.postgrest["families"]
                .select(columns = Columns.ALL) {
                    filter { eq("id", familyId) }
                }
                .decodeList<CloudFamilyVaultDto>()

            val dto = list.firstOrNull() ?: return@withContext null
            val createdMillis = parseIsoTimestamp(dto.createdAt)
            val updatedMillis = parseIsoTimestamp(dto.updatedAt)

            FamilyVault(
                familyId = dto.id,
                familyName = dto.name,
                inviteCode = dto.inviteCode ?: "",
                createdBy = dto.createdBy,
                createdAt = createdMillis,
                updatedAt = updatedMillis
            )
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR fetchFamilyVault: ${e.message}", e)
            null
        }
    }

    suspend fun fetchFamilyByInviteCode(inviteCode: String): FamilyVault? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        val cleanCode = inviteCode.trim().uppercase()
        if (cleanCode.isBlank()) return@withContext null

        val candidateCodes = listOf(
            cleanCode,
            if (cleanCode.startsWith("FAM-")) cleanCode.removePrefix("FAM-") else "FAM-$cleanCode"
        ).distinct()

        try {
            // 1. Search by invite_code column
            for (code in candidateCodes) {
                val list = SupabaseClientConfig.supabase.postgrest["families"]
                    .select(columns = Columns.ALL) {
                        filter { eq("invite_code", code) }
                    }
                    .decodeList<CloudFamilyVaultDto>()

                val dto = list.firstOrNull()
                if (dto != null) {
                    return@withContext FamilyVault(
                        familyId = dto.id,
                        familyName = dto.name,
                        inviteCode = dto.inviteCode ?: ("FAM-" + dto.id.take(6).uppercase()),
                        createdBy = dto.createdBy,
                        createdAt = parseIsoTimestamp(dto.createdAt),
                        updatedAt = parseIsoTimestamp(dto.updatedAt)
                    )
                }
            }

            // 2. Search by id ONLY if cleanCode is a valid UUID format
            if (isValidUuid(cleanCode)) {
                val list = SupabaseClientConfig.supabase.postgrest["families"]
                    .select(columns = Columns.ALL) {
                        filter { eq("id", cleanCode) }
                    }
                    .decodeList<CloudFamilyVaultDto>()

                val dto = list.firstOrNull()
                if (dto != null) {
                    return@withContext FamilyVault(
                        familyId = dto.id,
                        familyName = dto.name,
                        inviteCode = dto.inviteCode ?: ("FAM-" + dto.id.take(6).uppercase()),
                        createdBy = dto.createdBy,
                        createdAt = parseIsoTimestamp(dto.createdAt),
                        updatedAt = parseIsoTimestamp(dto.updatedAt)
                    )
                }
            }

            null
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR fetchFamilyByInviteCode: ${e.message}", e)
            null
        }
    }

    suspend fun upsertFamilyVault(vault: FamilyVault): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            val dto = CloudFamilyVaultDto(
                id = vault.familyId,
                name = vault.familyName,
                inviteCode = vault.inviteCode,
                createdBy = vault.createdBy,
                createdAt = Instant.ofEpochMilli(vault.createdAt).toString(),
                updatedAt = Instant.ofEpochMilli(vault.updatedAt).toString()
            )
            SupabaseClientConfig.supabase.postgrest["families"].upsert(dto)
            Log.d(tag, "SYNC_CREATE/UPDATE FamilyVault: ${vault.familyId}")
            true
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR upsertFamilyVault: ${e.message}", e)
            false
        }
    }

    // --- MEMBER OPERATIONS ---

    suspend fun fetchFamilyMembers(familyId: String): List<FamilyVaultMember> = withContext(Dispatchers.IO) {
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
                    name = dto.name ?: "Member",
                    role = try { FamilyRole.valueOf(dto.role) } catch (e: Exception) { FamilyRole.MEMBER },
                    joinedAt = parseIsoTimestamp(dto.joinedAt),
                    updatedAt = parseIsoTimestamp(dto.updatedAt),
                    isDeleted = false
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR fetchFamilyMembers: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun upsertFamilyMember(member: FamilyVaultMember): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            val dto = CloudFamilyMemberDto(
                id = member.memberId,
                familyId = member.familyId,
                userId = member.userId,
                name = member.name,
                role = member.role.name,
                joinedAt = Instant.ofEpochMilli(member.joinedAt).toString(),
                updatedAt = Instant.ofEpochMilli(member.updatedAt).toString()
            )
            SupabaseClientConfig.supabase.postgrest["family_members"].upsert(dto)
            Log.d(tag, "SYNC_UPDATE Member: ${member.memberId} (${member.name})")
            true
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR upsertFamilyMember: ${e.message}", e)
            false
        }
    }

    suspend fun deleteFamilyMember(memberId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            SupabaseClientConfig.supabase.postgrest["family_members"].delete {
                filter { eq("id", memberId) }
            }
            Log.d(tag, "SYNC_DELETE Member: $memberId")
            true
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR deleteFamilyMember: ${e.message}", e)
            false
        }
    }

    // --- TRANSACTION OPERATIONS ---

    suspend fun fetchFamilyTransactions(familyId: String): List<LedgerTransaction> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext emptyList()
        try {
            val dtoList = SupabaseClientConfig.supabase.postgrest["transactions"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("family_id", familyId)
                    }
                }
                .decodeList<CloudLedgerTransactionDto>()

            dtoList.map { dto ->
                LedgerTransaction(
                    transactionId = dto.id,
                    familyId = dto.familyId,
                    title = dto.title,
                    description = dto.descriptionNote ?: "",
                    amount = dto.amount,
                    category = dto.category ?: "General",
                    categoryIcon = getIconForCategory(dto.category ?: "General"),
                    type = try { TransactionType.valueOf(dto.transactionType) } catch (e: Exception) { TransactionType.EXPENSE },
                    paymentMethod = dto.paymentMethod,
                    paidByMemberId = dto.paidByMemberId,
                    paidByName = dto.paidByName ?: "Member",
                    dateMillis = parseIsoTimestamp(dto.transactionDate),
                    createdAt = parseIsoTimestamp(dto.createdAt),
                    updatedAt = parseIsoTimestamp(dto.updatedAt),
                    createdBy = dto.paidByMemberId,
                    lastModifiedBy = dto.paidByMemberId,
                    isDeleted = dto.isDeleted,
                    syncStatus = "SYNCED"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR fetchFamilyTransactions: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun upsertTransaction(tx: LedgerTransaction): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            val dto = CloudLedgerTransactionDto(
                id = tx.transactionId,
                familyId = tx.familyId,
                financeScope = "FAMILY",
                title = tx.title,
                amount = tx.amount,
                transactionType = tx.type.name,
                category = tx.category,
                paymentMethod = tx.paymentMethod,
                paidByMemberId = tx.paidByMemberId,
                paidByName = tx.paidByName,
                descriptionNote = tx.description,
                transactionDate = Instant.ofEpochMilli(tx.dateMillis).toString(),
                createdAt = Instant.ofEpochMilli(tx.createdAt).toString(),
                updatedAt = Instant.ofEpochMilli(tx.updatedAt).toString(),
                isDeleted = tx.isDeleted
            )
            SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
            Log.d(tag, "SYNC_CREATE/EDIT Transaction: ${tx.transactionId} (${tx.title} - ₹${tx.amount})")
            true
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR upsertTransaction: ${e.message}", e)
            false
        }
    }

    suspend fun softDeleteTransaction(transactionId: String, lastModifiedBy: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        try {
            SupabaseClientConfig.supabase.postgrest["transactions"].update({
                set("is_deleted", true)
                set("updated_at", Instant.now().toString())
            }) {
                filter { eq("id", transactionId) }
            }
            Log.d(tag, "SYNC_DELETE Transaction: $transactionId")
            true
        } catch (e: Exception) {
            Log.e(tag, "SYNC_ERROR softDeleteTransaction: ${e.message}", e)
            false
        }
    }

    private fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
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
            java.util.UUID.fromString(str)
            true
        } catch (e: Exception) {
            false
        }
    }
}
