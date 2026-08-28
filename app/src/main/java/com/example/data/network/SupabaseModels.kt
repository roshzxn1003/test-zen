package com.example.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class FamilyDto(
    val id: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class FamilyMemberDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    val name: String? = null,
    val role: String,
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("finance_scope") val financeScope: String,
    val amount: Double,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("category_id") val categoryId: String? = null,
    val description: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("upi_id") val upiId: String? = null,
    @SerialName("upi_transaction_id") val upiTransactionId: String? = null,
    @SerialName("transaction_date") val transactionDate: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class BudgetDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("finance_scope") val financeScope: String,
    val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    val amount: Double,
    @SerialName("period_type") val periodType: String,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class SavingsGoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("finance_scope") val financeScope: String,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)
