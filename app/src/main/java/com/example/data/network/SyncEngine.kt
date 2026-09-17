package com.example.data.network

import android.util.Log
import com.example.data.dao.*
import com.example.data.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Production-Grade Bidirectional Sync Engine for Personal Vault & Family Finance.
 *
 * Guarantees:
 * 1. Offline-first: Instant UI mutations with Room SQLite local caching.
 * 2. Zero-leakage privacy: Personal finance records sync with `finance_scope = 'PERSONAL'`
 *    and are cryptographically isolated at the database kernel level via PostgreSQL RLS.
 * 3. Multi-device recovery: Restores user transactions, budgets, and savings goals upon login.
 */
class SyncEngine(
    private val transactionDao: TransactionDao,
    private val familyDao: FamilyDao,
    private val authService: AuthService,
    private val familyMemberDao: FamilyMemberDao? = null,
    private val budgetDao: BudgetDao? = null,
    private val savingsGoalDao: SavingsGoalDao? = null
) {
    private val tag = "SyncEngine"

    fun isValidUuid(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return try {
            UUID.fromString(id)
            true
        } catch (e: Exception) {
            false
        }
    }

    val isConfigured: Boolean
        get() = SupabaseClientConfig.isConfigured

    suspend fun syncAll(explicitUserId: String? = null, activeFamilyId: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            Log.d(tag, "Operating in Local-First Vault mode (Supabase unconfigured or offline).")
            return@withContext true
        }

        val userId = explicitUserId
            ?: authService.currentUser.value?.id
            ?: try { SupabaseClientConfig.supabase.auth.currentUserOrNull()?.id } catch (e: Exception) { null }

        if (userId.isNullOrBlank() || userId == "local_user_1") {
            Log.d(tag, "No active cloud user session — operating in private local guest vault.")
            return@withContext true
        }

        if (!isValidUuid(userId)) {
            Log.w(tag, "User ID '$userId' is not a valid UUID. Skipping Supabase cloud sync to prevent PostgreSQL UUID syntax error.")
            return@withContext false
        }

        val effectiveFamId = activeFamilyId ?: try { familyDao.getFirstFamily()?.id } catch (e: Exception) { null }

        try {
            val txSuccess = syncPersonalTransactions(userId)
            val famSuccess = if (!effectiveFamId.isNullOrBlank() && isValidUuid(effectiveFamId)) {
                syncFamilyTransactions(effectiveFamId, userId)
            } else true
            val budgetSuccess = syncPersonalBudgets(userId)
            val goalsSuccess = syncPersonalSavingsGoals(userId)
            txSuccess && famSuccess && budgetSuccess && goalsSuccess
        } catch (e: Exception) {
            Log.e(tag, "syncAll error: ${e.message}", e)
            false
        }
    }

    suspend fun syncTransactions(explicitUserId: String? = null, activeFamilyId: String? = null) {
        syncAll(explicitUserId, activeFamilyId)
    }

    // =============================================================================
    // PERSONAL TRANSACTIONS SYNC
    // =============================================================================
    private suspend fun syncPersonalTransactions(userId: String): Boolean {
        var success = true
        // 1. Push Pending Creates
        val pendingCreates = transactionDao.getPendingCreates().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localTx in pendingCreates) {
            val serverId = localTx.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = TransactionDto(
                id = serverId,
                userId = userId,
                familyId = null,
                financeScope = "PERSONAL",
                amount = localTx.amount,
                title = localTx.title,
                transactionType = localTx.type.name,
                type = localTx.type.name,
                categoryId = localTx.category,
                category = localTx.category,
                description = localTx.note.ifBlank { localTx.title },
                paymentMethod = localTx.paymentMethod,
                upiId = localTx.upiId,
                upiTransactionId = localTx.upiTransactionId,
                transactionDate = Instant.ofEpochMilli(localTx.dateMillis).toString(),
                createdAt = Instant.ofEpochMilli(localTx.updatedAt).toString(),
                updatedAt = Instant.ofEpochMilli(localTx.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
                transactionDao.updateTransaction(
                    localTx.copy(
                        serverId = serverId,
                        syncStatus = "SYNCED",
                        createdByUserId = userId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push personal transaction create: ${e.message}")
                success = false
            }
        }

        // 2. Push Pending Updates
        val pendingUpdates = transactionDao.getPendingUpdates().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localTx in pendingUpdates) {
            val serverId = localTx.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = TransactionDto(
                id = serverId,
                userId = userId,
                familyId = null,
                financeScope = "PERSONAL",
                amount = localTx.amount,
                title = localTx.title,
                transactionType = localTx.type.name,
                type = localTx.type.name,
                categoryId = localTx.category,
                category = localTx.category,
                description = localTx.note.ifBlank { localTx.title },
                paymentMethod = localTx.paymentMethod,
                upiId = localTx.upiId,
                upiTransactionId = localTx.upiTransactionId,
                transactionDate = Instant.ofEpochMilli(localTx.dateMillis).toString(),
                updatedAt = Instant.ofEpochMilli(localTx.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
                transactionDao.updateTransaction(
                    localTx.copy(serverId = serverId, syncStatus = "SYNCED", updatedAt = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push personal transaction update: ${e.message}")
                success = false
            }
        }

        // 3. Push Pending Deletes
        val pendingDeletes = transactionDao.getPendingDeletes().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localTx in pendingDeletes) {
            val serverId = localTx.serverId
            if (serverId != null && isValidUuid(serverId)) {
                try {
                    SupabaseClientConfig.supabase.postgrest["transactions"].update({
                        set("is_deleted", true)
                        set("updated_at", Instant.now().toString())
                    }) {
                        filter { eq("id", serverId) }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to soft-delete personal transaction on cloud: ${e.message}")
                    success = false
                }
            }
            transactionDao.deleteTransaction(localTx)
        }

        // 4. Pull Remote Personal Transactions
        try {
            val remoteTxs = SupabaseClientConfig.supabase.postgrest["transactions"]
                .select {
                    filter {
                        eq("finance_scope", "PERSONAL")
                        eq("user_id", userId)
                    }
                }
                .decodeList<TransactionDto>()

            for (dto in remoteTxs) {
                val existing = transactionDao.getTransactionByServerId(dto.id)
                if (dto.isDeleted) {
                    if (existing != null) {
                        transactionDao.deleteTransaction(existing)
                    }
                } else {
                    val txDateMillis = parseIsoTimestamp(dto.transactionDate)
                    val txUpdatedMillis = parseIsoTimestamp(dto.updatedAt ?: dto.createdAt)
                    val txTitle = dto.title?.takeIf { it.isNotBlank() }
                        ?: dto.description.takeIf { it.isNotBlank() }
                        ?: "Transaction"
                    val txCategory = dto.category ?: dto.categoryId ?: "General"
                    val txType = try {
                        TransactionType.valueOf(dto.transactionType.ifBlank { dto.type ?: "EXPENSE" })
                    } catch (e: Exception) {
                        TransactionType.EXPENSE
                    }

                    if (existing == null) {
                        val newEntity = TransactionEntity(
                            title = txTitle,
                            amount = dto.amount,
                            type = txType,
                            category = txCategory,
                            note = dto.description,
                            paymentMethod = dto.paymentMethod,
                            dateMillis = txDateMillis,
                            upiId = dto.upiId,
                            upiTransactionId = dto.upiTransactionId,
                            financeScope = FinanceScope.PERSONAL,
                            familyId = null,
                            createdByUserId = userId,
                            serverId = dto.id,
                            syncStatus = "SYNCED",
                            updatedAt = txUpdatedMillis,
                            isDeleted = false
                        )
                        transactionDao.insertTransaction(newEntity)
                    } else if (existing.syncStatus == "SYNCED" && txUpdatedMillis > existing.updatedAt) {
                        val updatedEntity = existing.copy(
                            title = txTitle,
                            amount = dto.amount,
                            type = txType,
                            category = txCategory,
                            note = dto.description,
                            paymentMethod = dto.paymentMethod,
                            dateMillis = txDateMillis,
                            upiId = dto.upiId,
                            upiTransactionId = dto.upiTransactionId,
                            syncStatus = "SYNCED",
                            updatedAt = txUpdatedMillis,
                            isDeleted = false
                        )
                        transactionDao.updateTransaction(updatedEntity)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to pull remote personal transactions: ${e.message}")
            success = false
        }
        return success
    }

    // =============================================================================
    // FAMILY TRANSACTIONS SYNC
    // =============================================================================
    suspend fun syncFamilyTransactions(familyId: String, userId: String): Boolean {
        if (!isConfigured || familyId.isBlank()) return true
        var success = true

        // 1. Push Pending Creates
        val pendingCreates = transactionDao.getPendingCreates().filter { it.financeScope == FinanceScope.FAMILY && it.familyId == familyId }
        for (localTx in pendingCreates) {
            val serverId = localTx.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = TransactionDto(
                id = serverId,
                userId = userId,
                familyId = familyId,
                financeScope = "FAMILY",
                amount = localTx.amount,
                title = localTx.title,
                transactionType = localTx.type.name,
                type = localTx.type.name,
                categoryId = localTx.category,
                category = localTx.category,
                description = localTx.note.ifBlank { localTx.title },
                paymentMethod = localTx.paymentMethod,
                upiId = localTx.upiId,
                upiTransactionId = localTx.upiTransactionId,
                transactionDate = Instant.ofEpochMilli(localTx.dateMillis).toString(),
                createdAt = Instant.ofEpochMilli(localTx.updatedAt).toString(),
                updatedAt = Instant.ofEpochMilli(localTx.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
                transactionDao.updateTransaction(
                    localTx.copy(
                        serverId = serverId,
                        syncStatus = "SYNCED",
                        createdByUserId = localTx.createdByUserId ?: userId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push family transaction create: ${e.message}")
                success = false
            }
        }

        // 2. Push Pending Updates
        val pendingUpdates = transactionDao.getPendingUpdates().filter { it.financeScope == FinanceScope.FAMILY && it.familyId == familyId }
        for (localTx in pendingUpdates) {
            val serverId = localTx.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = TransactionDto(
                id = serverId,
                userId = userId,
                familyId = familyId,
                financeScope = "FAMILY",
                amount = localTx.amount,
                title = localTx.title,
                transactionType = localTx.type.name,
                type = localTx.type.name,
                categoryId = localTx.category,
                category = localTx.category,
                description = localTx.note.ifBlank { localTx.title },
                paymentMethod = localTx.paymentMethod,
                upiId = localTx.upiId,
                upiTransactionId = localTx.upiTransactionId,
                transactionDate = Instant.ofEpochMilli(localTx.dateMillis).toString(),
                updatedAt = Instant.ofEpochMilli(localTx.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
                transactionDao.updateTransaction(
                    localTx.copy(serverId = serverId, syncStatus = "SYNCED", updatedAt = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push family transaction update: ${e.message}")
                success = false
            }
        }

        // 3. Push Pending Deletes
        val pendingDeletes = transactionDao.getPendingDeletes().filter { it.financeScope == FinanceScope.FAMILY && it.familyId == familyId }
        for (localTx in pendingDeletes) {
            val serverId = localTx.serverId
            if (serverId != null && isValidUuid(serverId)) {
                try {
                    SupabaseClientConfig.supabase.postgrest["transactions"].update({
                        set("is_deleted", true)
                        set("updated_at", Instant.now().toString())
                    }) {
                        filter { eq("id", serverId) }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to soft-delete family transaction on cloud: ${e.message}")
                    success = false
                }
            }
            transactionDao.deleteTransaction(localTx)
        }

        // 4. Pull Remote Family Transactions
        try {
            val remoteTxs = SupabaseClientConfig.supabase.postgrest["transactions"]
                .select {
                    filter {
                        eq("finance_scope", "FAMILY")
                        eq("family_id", familyId)
                    }
                }
                .decodeList<TransactionDto>()

            for (dto in remoteTxs) {
                val existing = transactionDao.getTransactionByServerId(dto.id)
                if (dto.isDeleted) {
                    if (existing != null) {
                        transactionDao.deleteTransaction(existing)
                    }
                } else {
                    val txDateMillis = parseIsoTimestamp(dto.transactionDate)
                    val txUpdatedMillis = parseIsoTimestamp(dto.updatedAt ?: dto.createdAt)
                    val txTitle = dto.title?.takeIf { it.isNotBlank() }
                        ?: dto.description.takeIf { it.isNotBlank() }
                        ?: "Family Transaction"
                    val txCategory = dto.category ?: dto.categoryId ?: "General"
                    val txType = try {
                        TransactionType.valueOf(dto.transactionType.ifBlank { dto.type ?: "EXPENSE" })
                    } catch (e: Exception) {
                        TransactionType.EXPENSE
                    }

                    if (existing == null) {
                        val newEntity = TransactionEntity(
                            title = txTitle,
                            amount = dto.amount,
                            type = txType,
                            category = txCategory,
                            note = dto.description,
                            paymentMethod = dto.paymentMethod,
                            dateMillis = txDateMillis,
                            upiId = dto.upiId,
                            upiTransactionId = dto.upiTransactionId,
                            financeScope = FinanceScope.FAMILY,
                            familyId = familyId,
                            createdByUserId = dto.userId,
                            serverId = dto.id,
                            syncStatus = "SYNCED",
                            updatedAt = txUpdatedMillis,
                            isDeleted = false
                        )
                        transactionDao.insertTransaction(newEntity)
                    } else if (existing.syncStatus == "SYNCED" && txUpdatedMillis > existing.updatedAt) {
                        val updatedEntity = existing.copy(
                            title = txTitle,
                            amount = dto.amount,
                            type = txType,
                            category = txCategory,
                            note = dto.description,
                            paymentMethod = dto.paymentMethod,
                            dateMillis = txDateMillis,
                            upiId = dto.upiId,
                            upiTransactionId = dto.upiTransactionId,
                            syncStatus = "SYNCED",
                            updatedAt = txUpdatedMillis,
                            isDeleted = false
                        )
                        transactionDao.updateTransaction(updatedEntity)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to pull remote family transactions: ${e.message}")
            success = false
        }
        return success
    }

    // =============================================================================
    // PERSONAL BUDGETS SYNC
    // =============================================================================
    private suspend fun syncPersonalBudgets(userId: String): Boolean {
        val bDao = budgetDao ?: return true
        var success = true

        // Push Pending Creates
        val pendingCreates = bDao.getPendingCreates().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localBudget in pendingCreates) {
            val serverId = localBudget.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = BudgetDto(
                id = serverId,
                userId = userId,
                familyId = null,
                financeScope = "PERSONAL",
                name = localBudget.categoryName,
                categoryId = localBudget.categoryName,
                categoryName = localBudget.categoryName,
                amount = localBudget.monthlyLimit,
                monthlyLimit = localBudget.monthlyLimit,
                monthYear = localBudget.monthYear,
                periodType = localBudget.periodType,
                updatedAt = Instant.ofEpochMilli(localBudget.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["budgets"].upsert(dto)
                bDao.updateBudget(
                    localBudget.copy(serverId = serverId, syncStatus = "SYNCED", updatedAt = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push budget create: ${e.message}")
                success = false
            }
        }

        // Push Pending Updates
        val pendingUpdates = bDao.getPendingUpdates().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localBudget in pendingUpdates) {
            val serverId = localBudget.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = BudgetDto(
                id = serverId,
                userId = userId,
                familyId = null,
                financeScope = "PERSONAL",
                name = localBudget.categoryName,
                categoryId = localBudget.categoryName,
                categoryName = localBudget.categoryName,
                amount = localBudget.monthlyLimit,
                monthlyLimit = localBudget.monthlyLimit,
                monthYear = localBudget.monthYear,
                periodType = localBudget.periodType,
                updatedAt = Instant.ofEpochMilli(localBudget.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["budgets"].upsert(dto)
                bDao.updateBudget(
                    localBudget.copy(serverId = serverId, syncStatus = "SYNCED", updatedAt = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push budget update: ${e.message}")
                success = false
            }
        }

        // Push Pending Deletes
        val pendingDeletes = bDao.getPendingDeletes().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localBudget in pendingDeletes) {
            val serverId = localBudget.serverId
            if (serverId != null && isValidUuid(serverId)) {
                try {
                    SupabaseClientConfig.supabase.postgrest["budgets"].update({
                        set("is_deleted", true)
                        set("updated_at", Instant.now().toString())
                    }) {
                        filter { eq("id", serverId) }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to soft-delete budget: ${e.message}")
                    success = false
                }
            }
            bDao.deleteBudget(localBudget)
        }

        // Pull Remote Budgets
        try {
            val remoteBudgets = SupabaseClientConfig.supabase.postgrest["budgets"]
                .select {
                    filter {
                        eq("finance_scope", "PERSONAL")
                        eq("user_id", userId)
                    }
                }
                .decodeList<BudgetDto>()

            for (dto in remoteBudgets) {
                val existing = bDao.getBudgetByServerId(dto.id)
                if (dto.isDeleted) {
                    if (existing != null) bDao.deleteBudget(existing)
                } else {
                    val limit = if (dto.amount > 0) dto.amount else (dto.monthlyLimit ?: 0.0)
                    val catName = dto.categoryName ?: dto.categoryId ?: dto.name
                    val mYear = dto.monthYear ?: "2026-07"
                    val updatedMillis = parseIsoTimestamp(dto.updatedAt ?: dto.createdAt)

                    if (existing == null) {
                        val newEntity = BudgetEntity(
                            categoryName = catName,
                            monthlyLimit = limit,
                            monthYear = mYear,
                            periodType = dto.periodType,
                            financeScope = FinanceScope.PERSONAL,
                            familyId = null,
                            serverId = dto.id,
                            syncStatus = "SYNCED",
                            updatedAt = updatedMillis,
                            isDeleted = false
                        )
                        bDao.insertOrUpdateBudget(newEntity)
                    } else if (existing.syncStatus == "SYNCED" && updatedMillis > existing.updatedAt) {
                        val updated = existing.copy(
                            categoryName = catName,
                            monthlyLimit = limit,
                            monthYear = mYear,
                            periodType = dto.periodType,
                            syncStatus = "SYNCED",
                            updatedAt = updatedMillis
                        )
                        bDao.updateBudget(updated)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to pull remote budgets: ${e.message}")
            success = false
        }
        return success
    }

    // =============================================================================
    // PERSONAL SAVINGS GOALS SYNC
    // =============================================================================
    private suspend fun syncPersonalSavingsGoals(userId: String): Boolean {
        val sDao = savingsGoalDao ?: return true
        var success = true

        // Push Pending Creates
        val pendingCreates = sDao.getPendingCreates().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localGoal in pendingCreates) {
            val serverId = localGoal.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = SavingsGoalDto(
                id = serverId,
                userId = userId,
                familyId = null,
                financeScope = "PERSONAL",
                name = localGoal.title,
                title = localGoal.title,
                targetAmount = localGoal.targetAmount,
                currentAmount = localGoal.currentAmount,
                targetDate = Instant.ofEpochMilli(localGoal.targetDateMillis).toString(),
                iconName = localGoal.iconName,
                colorHex = localGoal.colorHex,
                updatedAt = Instant.ofEpochMilli(localGoal.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["savings_goals"].upsert(dto)
                sDao.updateGoal(
                    localGoal.copy(serverId = serverId, syncStatus = "SYNCED", updatedAt = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push savings goal create: ${e.message}")
                success = false
            }
        }

        // Push Pending Updates
        val pendingUpdates = sDao.getPendingUpdates().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localGoal in pendingUpdates) {
            val serverId = localGoal.serverId?.takeIf { it.isNotBlank() && isValidUuid(it) } ?: UUID.randomUUID().toString()
            val dto = SavingsGoalDto(
                id = serverId,
                userId = userId,
                familyId = null,
                financeScope = "PERSONAL",
                name = localGoal.title,
                title = localGoal.title,
                targetAmount = localGoal.targetAmount,
                currentAmount = localGoal.currentAmount,
                targetDate = Instant.ofEpochMilli(localGoal.targetDateMillis).toString(),
                iconName = localGoal.iconName,
                colorHex = localGoal.colorHex,
                updatedAt = Instant.ofEpochMilli(localGoal.updatedAt).toString(),
                isDeleted = false
            )
            try {
                SupabaseClientConfig.supabase.postgrest["savings_goals"].upsert(dto)
                sDao.updateGoal(
                    localGoal.copy(serverId = serverId, syncStatus = "SYNCED", updatedAt = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to push savings goal update: ${e.message}")
                success = false
            }
        }

        // Push Pending Deletes
        val pendingDeletes = sDao.getPendingDeletes().filter { it.financeScope == FinanceScope.PERSONAL }
        for (localGoal in pendingDeletes) {
            val serverId = localGoal.serverId
            if (serverId != null && isValidUuid(serverId)) {
                try {
                    SupabaseClientConfig.supabase.postgrest["savings_goals"].update({
                        set("is_deleted", true)
                        set("updated_at", Instant.now().toString())
                    }) {
                        filter { eq("id", serverId) }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to soft-delete savings goal: ${e.message}")
                    success = false
                }
            }
            sDao.deleteGoal(localGoal)
        }

        // Pull Remote Savings Goals
        try {
            val remoteGoals = SupabaseClientConfig.supabase.postgrest["savings_goals"]
                .select {
                    filter {
                        eq("finance_scope", "PERSONAL")
                        eq("user_id", userId)
                    }
                }
                .decodeList<SavingsGoalDto>()

            for (dto in remoteGoals) {
                val existing = sDao.getGoalByServerId(dto.id)
                if (dto.isDeleted) {
                    if (existing != null) sDao.deleteGoal(existing)
                } else {
                    val goalTitle = dto.title ?: dto.name
                    val targetDate = dto.targetDate?.let { parseIsoTimestamp(it) } ?: (System.currentTimeMillis() + 30L * 86400000)
                    val updatedMillis = parseIsoTimestamp(dto.updatedAt ?: dto.createdAt)

                    if (existing == null) {
                        val newEntity = SavingsGoalEntity(
                            title = goalTitle,
                            targetAmount = dto.targetAmount,
                            currentAmount = dto.currentAmount,
                            targetDateMillis = targetDate,
                            iconName = dto.iconName ?: "Savings",
                            colorHex = dto.colorHex ?: "#059669",
                            financeScope = FinanceScope.PERSONAL,
                            familyId = null,
                            serverId = dto.id,
                            syncStatus = "SYNCED",
                            updatedAt = updatedMillis,
                            isDeleted = false
                        )
                        sDao.insertOrUpdateGoal(newEntity)
                    } else if (existing.syncStatus == "SYNCED" && updatedMillis > existing.updatedAt) {
                        val updated = existing.copy(
                            title = goalTitle,
                            targetAmount = dto.targetAmount,
                            currentAmount = dto.currentAmount,
                            targetDateMillis = targetDate,
                            iconName = dto.iconName ?: existing.iconName,
                            colorHex = dto.colorHex ?: existing.colorHex,
                            syncStatus = "SYNCED",
                            updatedAt = updatedMillis
                        )
                        sDao.updateGoal(updated)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to pull remote savings goals: ${e.message}")
            success = false
        }
        return success
    }

    suspend fun fetchRemoteFamilyByInviteCode(inviteCode: String): Pair<FamilyDto?, List<TransactionDto>> {
        val cleanCode = inviteCode.trim().uppercase()
        return withContext(Dispatchers.IO) {
            // 1. Try fetching from Supabase cloud first if configured
            if (isConfigured) {
                try {
                    val candidateCodes = listOf(
                        cleanCode,
                        if (cleanCode.startsWith("FAM-")) cleanCode.removePrefix("FAM-") else "FAM-$cleanCode"
                    ).distinct()

                    for (code in candidateCodes) {
                        val famList = SupabaseClientConfig.supabase.postgrest["families"]
                            .select {
                                filter { eq("invite_code", code) }
                            }
                            .decodeList<FamilyDto>()
                        val remoteFam = famList.firstOrNull()
                        if (remoteFam != null) {
                            val remoteTxs = try {
                                SupabaseClientConfig.supabase.postgrest["transactions"]
                                    .select {
                                        filter {
                                            eq("family_id", remoteFam.id)
                                            eq("finance_scope", "FAMILY")
                                            eq("is_deleted", false)
                                        }
                                    }
                                    .decodeList<TransactionDto>()
                            } catch (te: Exception) {
                                Log.w(tag, "Failed to fetch remote transactions for family: ${te.message}")
                                emptyList()
                            }
                            return@withContext Pair(remoteFam, remoteTxs)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "fetchRemoteFamilyByInviteCode cloud error: ${e.message}")
                }
            }

            // 2. Fallback to local Room DB
            val localFamily = familyDao.getFamilyById(cleanCode)
            if (localFamily != null) {
                Pair(
                    FamilyDto(
                        id = localFamily.id,
                        name = localFamily.name,
                        createdBy = localFamily.createdByUserId,
                        inviteCode = localFamily.inviteCode
                    ),
                    emptyList()
                )
            } else {
                Pair(null, emptyList())
            }
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
}
