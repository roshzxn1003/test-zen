package com.example.data.familyledger

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.models.FamilyRole
import com.example.data.models.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * High-reliability Domain & Room Entity for Family Ledger Transactions.
 * Uses a stable String UUID as PrimaryKey across local Room cache and remote backend.
 */
@Entity(
    tableName = "ledger_transactions",
    indices = [
        Index(value = ["familyId", "dateMillis"]),
        Index(value = ["syncStatus", "isDeleted"])
    ]
)
data class LedgerTransaction(
    @PrimaryKey
    val transactionId: String,
    val familyId: String,
    val title: String,
    val description: String = "",
    val amount: Double,
    val category: String = "General",
    val categoryIcon: String = "Category",
    val type: TransactionType = TransactionType.EXPENSE,
    val paymentMethod: String = "UPI",
    val paidByMemberId: String = "",
    val paidByName: String = "Member",
    val dateMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val lastModifiedBy: String = "",
    val isDeleted: Boolean = false,
    /**
     * Local sync state machine:
     * PENDING_CREATE  — created offline, not yet pushed to backend
     * PENDING_UPDATE  — edited offline, not yet pushed
     * PENDING_DELETE  — deleted offline, soft-delete not yet pushed
     * OFFLINE         — written while no cloud config available
     * SYNCED          — in sync with backend
     */
    val syncStatus: String = "SYNCED",
    /** Incremented on every local mutation; used for optimistic concurrency on the server. */
    val syncVersion: Long = 1L
)

@Entity(
    tableName = "family_vaults",
    indices = [
        Index(value = ["inviteCode"])
    ]
)
data class FamilyVault(
    @PrimaryKey
    val familyId: String,
    val familyName: String,
    val inviteCode: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED"
)

@Entity(
    tableName = "family_vault_members",
    indices = [
        Index(value = ["familyId", "userId"])
    ]
)
data class FamilyVaultMember(
    @PrimaryKey
    val memberId: String,
    val familyId: String,
    val userId: String,
    val name: String,
    val role: FamilyRole = FamilyRole.MEMBER,
    /** Computed field: sum of expense transaction amounts paid by this member. Not persisted from cloud. */
    val totalPaid: Double = 0.0,
    /** Computed field: count of transactions linked to this member. Not persisted from cloud. */
    val transactionCount: Int = 0,
    val joinedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED"
)

enum class FamilySyncStatus {
    /** All local changes are reflected on Supabase. */
    SYNCED,
    /** A sync operation is in-flight. */
    SYNCING,
    /** No cloud configuration — operating in local-only mode. */
    OFFLINE,
    /** Last sync attempt failed; will retry on next trigger. */
    ERROR
}

// =============================================================================
// CLOUD DTOs — Remote Backend DTOs & Serialization
// =============================================================================

/**
 * Mirrors the remote `transactions` table.
 *
 * IMPORTANT: Fields map 1-to-1 to dedicated database columns.
 */
@Serializable
data class CloudLedgerTransactionDto(
    @SerialName("id") val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("finance_scope") val financeScope: String = "FAMILY",
    /** Dedicated title column — short description of what the transaction was for. */
    @SerialName("title") val title: String? = null,
    /** Free-form notes / extended description. */
    @SerialName("description") val description: String = "",
    @SerialName("amount") val amount: Double,
    @SerialName("transaction_type") val transactionType: String = "EXPENSE",
    @SerialName("type") val type: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "UPI",
    /** The auth user ID who owns this record. */
    @SerialName("user_id") val userId: String,
    /** family_members.id of the person who physically paid — may differ from userId. */
    @SerialName("paid_by_member_id") val paidByMemberId: String? = null,
    /** Display name of the payer (denormalised for fast rendering). */
    @SerialName("paid_by_name") val paidByName: String? = null,
    /** Optimistic concurrency counter — incremented on each update. */
    @SerialName("sync_version") val syncVersion: Long = 1,
    @SerialName("transaction_date") val transactionDate: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

/**
 * Mirrors the remote `families` table.
 * Note: `createdBy` is nullable.
 */
@Serializable
data class CloudFamilyVaultDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Mirrors the remote `family_members` table.
 */
@Serializable
data class CloudFamilyMemberDto(
    @SerialName("id") val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("role") val role: String = "MEMBER",
    /** Hex colour used for the member's avatar bubble in the UI. */
    @SerialName("avatar_color") val avatarColor: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// =============================================================================
// REALTIME EVENT TYPES
// =============================================================================

/**
 * Wraps a remote realtime event into a strongly-typed domain event
 * that the repository can apply to the local Room cache without re-fetching.
 */
data class RealtimeLedgerEvent(
    val type: RealtimeEventType,
    val transaction: LedgerTransaction? = null,
    val member: FamilyVaultMember? = null,
    val vault: FamilyVault? = null
)

enum class RealtimeEventType { INSERT, UPDATE, DELETE }

object FamilyInviteCodeUtils {
    private val INVITE_CODE_REGEX = Regex("""\bFAM-[A-Z0-9]{4,10}\b""", RegexOption.IGNORE_CASE)
    private val SHORT_CODE_REGEX = Regex("""\b[A-Z0-9]{6}\b""", RegexOption.IGNORE_CASE)
    private val UUID_REGEX = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")

    /**
     * Extracts and normalizes a family invite code or family ID from raw input,
     * including full shared messages, deep link URLs, QR string content, or plain user input.
     */
    fun extractInviteCode(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""

        // 1. Check for standard FAM-XXXXXX in text (handles WhatsApp/SMS shared text)
        val famMatch = INVITE_CODE_REGEX.find(trimmed)
        if (famMatch != null) {
            return famMatch.value.uppercase()
        }

        // 2. Check for standard UUID
        val uuidMatch = UUID_REGEX.find(trimmed)
        if (uuidMatch != null) {
            return uuidMatch.value.lowercase()
        }

        // 3. Check for 6-character alphanumeric code
        val shortMatch = SHORT_CODE_REGEX.find(trimmed)
        if (shortMatch != null && !trimmed.contains(" ") && trimmed.length <= 10) {
            return "FAM-" + shortMatch.value.uppercase()
        }

        // 4. Default clean fallback
        val clean = trimmed.substringBefore(" ").substringBefore("\n").trim().uppercase()
        return if (clean.length == 6 && !clean.startsWith("FAM-")) {
            "FAM-$clean"
        } else {
            clean
        }
    }
}
