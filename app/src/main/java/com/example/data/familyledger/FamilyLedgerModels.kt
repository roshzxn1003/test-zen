package com.example.data.familyledger

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.models.FamilyRole
import com.example.data.models.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * High-reliability Domain & Room Entity for Family Ledger Transactions.
 * Uses a stable String UUID as PrimaryKey across local cache and cloud Supabase database.
 */
@Entity(tableName = "ledger_transactions")
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
    val syncStatus: String = "SYNCED",
    val syncVersion: Long = 1L
)

@Entity(tableName = "family_vaults")
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

@Entity(tableName = "family_vault_members")
data class FamilyVaultMember(
    @PrimaryKey
    val memberId: String,
    val familyId: String,
    val userId: String,
    val name: String,
    val role: FamilyRole = FamilyRole.MEMBER,
    val totalPaid: Double = 0.0,
    val transactionCount: Int = 0,
    val joinedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED"
)

enum class FamilySyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE,
    ERROR
}

/**
 * Cloud DTOs for Supabase PostgREST & Realtime Serialization
 */
@Serializable
data class CloudLedgerTransactionDto(
    @SerialName("id") val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("finance_scope") val financeScope: String = "FAMILY",
    @SerialName("description") val title: String,
    @SerialName("amount") val amount: Double,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("category_id") val category: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "UPI",
    @SerialName("user_id") val paidByMemberId: String,
    @SerialName("upi_id") val paidByName: String? = null,
    @SerialName("upi_transaction_id") val descriptionNote: String? = null,
    @SerialName("transaction_date") val transactionDate: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class CloudFamilyVaultDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CloudFamilyMemberDto(
    @SerialName("id") val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("role") val role: String = "MEMBER",
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
