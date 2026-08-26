package com.example.data.network

import android.util.Log
import com.example.data.dao.*
import com.example.data.models.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*

import kotlinx.coroutines.flow.firstOrNull

class SyncEngine(
    private val transactionDao: TransactionDao,
    private val familyDao: FamilyDao,
    private val authService: SupabaseAuthService,
    private val familyMemberDao: FamilyMemberDao? = null,
    private val budgetDao: BudgetDao? = null,
    private val savingsGoalDao: SavingsGoalDao? = null
) {
    private val isConfigured: Boolean
        get() = SupabaseClientConfig.isConfigured

    suspend fun syncAll(explicitUserId: String? = null): Boolean {
        if (!isConfigured) {
            Log.i("SyncEngine", "Supabase is not configured with remote credentials. Operating in Local-First Vault mode.")
            return true
        }

        val resolvedUserId = authService.currentUser.value?.id 
            ?: explicitUserId?.takeIf { it.isNotBlank() } 
            ?: "zenith_member"
        
        return withContext(Dispatchers.IO) {
            try {
                // 1. Synchronize Families & Membership
                pushPendingFamilies(resolvedUserId)
                pushPendingMembers(resolvedUserId)
                pullRemoteMembers(resolvedUserId)
                pullRemoteFamilies(resolvedUserId)

                // 2. Synchronize Transactions (Personal + Shared Family Vaults)
                pushPendingTransactions(resolvedUserId)
                pullRemoteTransactions(resolvedUserId)

                // 3. Synchronize Budgets & Savings Goals
                pushPendingBudgets(resolvedUserId)
                pullRemoteBudgets(resolvedUserId)
                pushPendingSavingsGoals(resolvedUserId)
                pullRemoteSavingsGoals(resolvedUserId)

                Log.i("SyncEngine", "Full bidirectional sync completed successfully for user: $resolvedUserId")
                true
            } catch (e: Exception) {
                Log.e("SyncEngine", "Error during cloud sync execution", e)
                false
            }
        }
    }

    suspend fun fetchRemoteFamilyByInviteCode(inviteCode: String): Pair<FamilyDto?, List<TransactionDto>> {
        if (!isConfigured) return Pair(null, emptyList())
        return withContext(Dispatchers.IO) {
            try {
                val clean = inviteCode.trim()
                val matchedFamilies = SupabaseClientConfig.supabase.postgrest["families"]
                    .select(columns = Columns.ALL) {
                        filter { eq("invite_code", clean) }
                    }
                    .decodeList<FamilyDto>()
                
                val matched = matchedFamilies.firstOrNull() ?: return@withContext Pair(null, emptyList())

                val txs = SupabaseClientConfig.supabase.postgrest["transactions"]
                    .select(columns = Columns.ALL) {
                        filter { eq("family_id", matched.id) }
                    }
                    .decodeList<TransactionDto>()

                Pair(matched, txs)
            } catch (e: Exception) {
                Log.e("SyncEngine", "Failed to fetch remote family for code $inviteCode", e)
                Pair(null, emptyList())
            }
        }
    }

    suspend fun syncTransactions(explicitUserId: String? = null) {
        syncAll(explicitUserId)
    }

    private suspend fun pushPendingFamilies(userId: String) {
        try {
            val pendingCreates = familyDao.getPendingCreates()
            for (fam in pendingCreates) {
                val dto = FamilyDto(
                    id = fam.id,
                    name = fam.name,
                    createdBy = fam.createdByUserId.ifBlank { userId },
                    inviteCode = fam.inviteCode,
                    createdAt = Instant.ofEpochMilli(fam.createdAt).toString()
                )
                SupabaseClientConfig.supabase.postgrest["families"].upsert(dto)
                familyDao.updateFamily(fam.copy(serverId = fam.id, syncStatus = "SYNCED"))
            }

            val pendingDeletes = familyDao.getPendingDeletes()
            for (fam in pendingDeletes) {
                fam.serverId?.let { sId ->
                    SupabaseClientConfig.supabase.postgrest["families"].delete {
                        filter { eq("id", sId) }
                    }
                }
                familyDao.deleteFamilyById(fam.id)
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pushing families", e)
        }
    }

    private suspend fun pullRemoteFamilies(userId: String) {
        try {
            val memberDao = familyMemberDao ?: return
            
            // Get family IDs the user belongs to locally
            val localFamiliesFlow = familyDao.getAllFamiliesForUser(userId)
            val localFamilies = localFamiliesFlow.firstOrNull() ?: emptyList()
            
            val familyIdsToFetch = localFamilies.map { it.id }.toMutableList()
            
            // Add any families from the local members we just pulled
            val localMembers = memberDao.getMemberByUserId(userId).firstOrNull() ?: emptyList()
            familyIdsToFetch.addAll(localMembers.map { it.familyId })
            
            val distinctIds = familyIdsToFetch.distinct()
            
            if (distinctIds.isEmpty()) return

            val remoteFamilies = mutableListOf<FamilyDto>()
            distinctIds.forEach { fId ->
                val fetched = SupabaseClientConfig.supabase.postgrest["families"]
                    .select(columns = Columns.ALL) {
                        filter { eq("id", fId) }
                    }
                    .decodeList<FamilyDto>()
                remoteFamilies.addAll(fetched)
            }

            for (remote in remoteFamilies) {
                val existing = familyDao.getFamilyById(remote.id)
                val createdAtMillis = try {
                    remote.createdAt?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                if (existing == null) {
                    val newFam = FamilyEntity(
                        id = remote.id,
                        name = remote.name,
                        createdByUserId = remote.createdBy,
                        createdAt = createdAtMillis,
                        inviteCode = remote.inviteCode ?: "",
                        serverId = remote.id,
                        syncStatus = "SYNCED"
                    )
                    familyDao.insertFamily(newFam)
                } else {
                    familyDao.updateFamily(existing.copy(
                        name = remote.name,
                        inviteCode = remote.inviteCode ?: existing.inviteCode,
                        syncStatus = "SYNCED"
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pulling families", e)
        }
    }

    private suspend fun pushPendingMembers(userId: String) {
        val memberDao = familyMemberDao ?: return
        try {
            val creates = memberDao.getPendingCreates()
            for (mem in creates) {
                val dto = FamilyMemberDto(
                    id = mem.serverId ?: mem.id,
                    familyId = mem.familyId,
                    userId = mem.userId.ifBlank { userId },
                    role = mem.role.name,
                    joinedAt = Instant.ofEpochMilli(mem.joinedAt).toString()
                )
                SupabaseClientConfig.supabase.postgrest["family_members"].upsert(dto)
                memberDao.updateMember(mem.copy(serverId = dto.id, syncStatus = "SYNCED"))
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pushing family members", e)
        }
    }

    private suspend fun pullRemoteMembers(userId: String) {
        val memberDao = familyMemberDao ?: return
        try {
            val remoteMembers = SupabaseClientConfig.supabase.postgrest["family_members"]
                .select(columns = Columns.ALL)
                .decodeList<FamilyMemberDto>()

            for (remote in remoteMembers) {
                val existing = memberDao.getMemberByServerId(remote.id)
                val joinedAtMillis = try {
                    remote.joinedAt?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                if (existing == null) {
                    val newMember = FamilyMemberEntity(
                        id = remote.id,
                        familyId = remote.familyId,
                        userId = remote.userId,
                        name = "Family Member",
                        role = try { FamilyRole.valueOf(remote.role) } catch (e: Exception) { FamilyRole.MEMBER },
                        joinedAt = joinedAtMillis,
                        serverId = remote.id,
                        syncStatus = "SYNCED"
                    )
                    memberDao.insertMember(newMember)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pulling family members", e)
        }
    }

    private suspend fun pushPendingTransactions(userId: String) {
        // Pending Creates
        val creates = transactionDao.getPendingCreates()
        for (localTx in creates) {
            val dto = TransactionDto(
                id = localTx.serverId ?: UUID.randomUUID().toString(),
                userId = localTx.createdByUserId ?: userId,
                familyId = localTx.familyId,
                financeScope = localTx.financeScope.name,
                amount = localTx.amount,
                transactionType = localTx.type.name,
                categoryId = null,
                description = localTx.title,
                paymentMethod = localTx.paymentMethod,
                upiId = localTx.upiId,
                upiTransactionId = localTx.upiTransactionId,
                transactionDate = Instant.ofEpochMilli(localTx.dateMillis).toString(),
                isDeleted = false
            )
            
            try {
                SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
                transactionDao.updateTransaction(localTx.copy(
                    serverId = dto.id,
                    syncStatus = "SYNCED"
                ))
            } catch (e: Exception) {
                Log.e("SyncEngine", "Error pushing transaction create", e)
            }
        }

        // Pending Updates
        val updates = transactionDao.getPendingUpdates()
        for (localTx in updates) {
            val serverId = localTx.serverId ?: continue
            val dto = TransactionDto(
                id = serverId,
                userId = localTx.createdByUserId ?: userId,
                familyId = localTx.familyId,
                financeScope = localTx.financeScope.name,
                amount = localTx.amount,
                transactionType = localTx.type.name,
                categoryId = null,
                description = localTx.title,
                paymentMethod = localTx.paymentMethod,
                upiId = localTx.upiId,
                upiTransactionId = localTx.upiTransactionId,
                transactionDate = Instant.ofEpochMilli(localTx.dateMillis).toString(),
                isDeleted = localTx.isDeleted
            )
            
            try {
                SupabaseClientConfig.supabase.postgrest["transactions"].upsert(dto)
                transactionDao.updateTransaction(localTx.copy(
                    syncStatus = "SYNCED"
                ))
            } catch (e: Exception) {
                Log.e("SyncEngine", "Error pushing transaction update", e)
            }
        }

        // Pending Deletes
        val deletes = transactionDao.getPendingDeletes()
        for (localTx in deletes) {
            val serverId = localTx.serverId
            if (serverId != null) {
                try {
                    SupabaseClientConfig.supabase.postgrest["transactions"].update({
                        set("is_deleted", true)
                    }) {
                        filter { eq("id", serverId) }
                    }
                    transactionDao.deleteTransactionById(localTx.id)
                } catch (e: Exception) {
                    Log.e("SyncEngine", "Error pushing transaction delete", e)
                }
            } else {
                transactionDao.deleteTransactionById(localTx.id)
            }
        }
    }

    private suspend fun pullRemoteTransactions(userId: String) {
        try {
            val remoteTxs = SupabaseClientConfig.supabase.postgrest["transactions"]
                .select(columns = Columns.ALL)
                .decodeList<TransactionDto>()

            for (remoteTx in remoteTxs) {
                val localTx = transactionDao.getTransactionByServerId(remoteTx.id)
                val txDateMillis = try {
                    Instant.parse(remoteTx.transactionDate).toEpochMilli()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                if (localTx == null) {
                    if (!remoteTx.isDeleted) {
                        val newTx = TransactionEntity(
                            title = remoteTx.description,
                            amount = remoteTx.amount,
                            type = try { TransactionType.valueOf(remoteTx.transactionType) } catch (e: Exception) { TransactionType.EXPENSE },
                            category = "General",
                            paymentMethod = remoteTx.paymentMethod,
                            dateMillis = txDateMillis,
                            upiId = remoteTx.upiId,
                            upiTransactionId = remoteTx.upiTransactionId,
                            financeScope = try { FinanceScope.valueOf(remoteTx.financeScope) } catch (e: Exception) { FinanceScope.PERSONAL },
                            familyId = remoteTx.familyId,
                            createdByUserId = remoteTx.userId,
                            serverId = remoteTx.id,
                            syncStatus = "SYNCED",
                            updatedAt = System.currentTimeMillis(),
                            isDeleted = false
                        )
                        transactionDao.insertTransaction(newTx)
                    }
                } else {
                    if (remoteTx.isDeleted) {
                        transactionDao.deleteTransactionById(localTx.id)
                    } else if (localTx.syncStatus == "SYNCED") {
                        val updatedTx = localTx.copy(
                            title = remoteTx.description,
                            amount = remoteTx.amount,
                            type = try { TransactionType.valueOf(remoteTx.transactionType) } catch (e: Exception) { TransactionType.EXPENSE },
                            paymentMethod = remoteTx.paymentMethod,
                            dateMillis = txDateMillis,
                            upiId = remoteTx.upiId,
                            upiTransactionId = remoteTx.upiTransactionId,
                            financeScope = try { FinanceScope.valueOf(remoteTx.financeScope) } catch (e: Exception) { FinanceScope.PERSONAL },
                            familyId = remoteTx.familyId
                        )
                        transactionDao.updateTransaction(updatedTx)
                    }
                }
            }
        } catch (e: Exception) {
             Log.e("SyncEngine", "Error pulling remote transactions", e)
        }
    }

    private suspend fun pushPendingBudgets(userId: String) {
        val bDao = budgetDao ?: return
        try {
            val creates = bDao.getPendingCreates()
            for (budget in creates) {
                val dto = BudgetDto(
                    id = budget.serverId ?: UUID.randomUUID().toString(),
                    userId = userId,
                    familyId = budget.familyId,
                    financeScope = budget.financeScope.name,
                    name = budget.categoryName,
                    amount = budget.monthlyLimit,
                    periodType = budget.periodType
                )
                SupabaseClientConfig.supabase.postgrest["budgets"].upsert(dto)
                bDao.updateBudget(budget.copy(serverId = dto.id, syncStatus = "SYNCED"))
            }

            val deletes = bDao.getPendingDeletes()
            for (budget in deletes) {
                budget.serverId?.let { sId ->
                    SupabaseClientConfig.supabase.postgrest["budgets"].delete {
                        filter { eq("id", sId) }
                    }
                }
                bDao.deleteBudgetById(budget.id)
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pushing budgets", e)
        }
    }

    private suspend fun pullRemoteBudgets(userId: String) {
        val bDao = budgetDao ?: return
        try {
            val remoteBudgets = SupabaseClientConfig.supabase.postgrest["budgets"]
                .select(columns = Columns.ALL)
                .decodeList<BudgetDto>()

            for (remote in remoteBudgets) {
                val existing = bDao.getBudgetByServerId(remote.id)
                if (existing == null && !remote.isDeleted) {
                    val newBudget = BudgetEntity(
                        categoryName = remote.name,
                        monthlyLimit = remote.amount,
                        periodType = remote.periodType,
                        financeScope = try { FinanceScope.valueOf(remote.financeScope) } catch (e: Exception) { FinanceScope.PERSONAL },
                        familyId = remote.familyId,
                        serverId = remote.id,
                        syncStatus = "SYNCED"
                    )
                    bDao.insertOrUpdateBudget(newBudget)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pulling remote budgets", e)
        }
    }

    private suspend fun pushPendingSavingsGoals(userId: String) {
        val gDao = savingsGoalDao ?: return
        try {
            val creates = gDao.getPendingCreates()
            for (goal in creates) {
                val dto = SavingsGoalDto(
                    id = goal.serverId ?: UUID.randomUUID().toString(),
                    userId = userId,
                    familyId = goal.familyId,
                    financeScope = goal.financeScope.name,
                    name = goal.title,
                    targetAmount = goal.targetAmount,
                    currentAmount = goal.currentAmount
                )
                SupabaseClientConfig.supabase.postgrest["savings_goals"].upsert(dto)
                gDao.updateGoal(goal.copy(serverId = dto.id, syncStatus = "SYNCED"))
            }

            val deletes = gDao.getPendingDeletes()
            for (goal in deletes) {
                goal.serverId?.let { sId ->
                    SupabaseClientConfig.supabase.postgrest["savings_goals"].delete {
                        filter { eq("id", sId) }
                    }
                }
                gDao.deleteGoalById(goal.id)
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pushing savings goals", e)
        }
    }

    private suspend fun pullRemoteSavingsGoals(userId: String) {
        val gDao = savingsGoalDao ?: return
        try {
            val remoteGoals = SupabaseClientConfig.supabase.postgrest["savings_goals"]
                .select(columns = Columns.ALL)
                .decodeList<SavingsGoalDto>()

            for (remote in remoteGoals) {
                val existing = gDao.getGoalByServerId(remote.id)
                if (existing == null && !remote.isDeleted) {
                    val newGoal = SavingsGoalEntity(
                        title = remote.name,
                        targetAmount = remote.targetAmount,
                        currentAmount = remote.currentAmount,
                        financeScope = try { FinanceScope.valueOf(remote.financeScope) } catch (e: Exception) { FinanceScope.PERSONAL },
                        familyId = remote.familyId,
                        serverId = remote.id,
                        syncStatus = "SYNCED"
                    )
                    gDao.insertOrUpdateGoal(newGoal)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error pulling remote savings goals", e)
        }
    }
}
