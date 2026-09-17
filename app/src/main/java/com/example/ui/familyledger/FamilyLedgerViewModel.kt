package com.example.ui.familyledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.CashFlowDatabase
import com.example.data.familyledger.*
import com.example.data.models.FamilyRole
import com.example.data.models.TransactionType
import com.example.data.network.AuthService
import com.example.data.network.SupabaseAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class TransactionSortOption(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount")
}

data class FamilyLedgerUiState(
    val isLoading: Boolean = false,
    val activeVault: FamilyVault? = null,
    val allVaults: List<FamilyVault> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val members: List<FamilyVaultMember> = emptyList(),
    val syncStatus: FamilySyncStatus = FamilySyncStatus.SYNCED,
    /** 0: Overview / 1: Activity / 2: Members / 3: Insights */
    val selectedSubTab: Int = 0,
    val searchQuery: String = "",
    val filterType: TransactionType? = null,
    val filterCategory: String? = null,
    val filterMemberId: String? = null,
    val sortBy: TransactionSortOption = TransactionSortOption.NEWEST_FIRST,
    /** "yyyy-MM" — used for month-scoped stats in the dashboard. */
    val selectedMonthYear: String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()),
    /** Non-null when a recoverable user-facing error is to be shown. */
    val userErrorMessage: String? = null,
    val isSavingTransaction: Boolean = false,
    val transactionSaveResult: String? = null
)

class FamilyLedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("zenith_family_ledger", android.content.Context.MODE_PRIVATE)
    private val authService: AuthService = SupabaseAuthService()

    val repository: FamilyLedgerRepository

    // =========================================================================
    // IDENTITY RESOLUTION
    // =========================================================================

    /**
     * Resolves the current user ID from authentication, falling back to a
     * stable locally-generated UUID (persisted in SharedPreferences).
     */
    val currentUserId: String
        get() = authService.currentUser.value?.id
            ?: prefs.getString("local_user_id", null)?.takeIf { isValidUuid(it) }
            ?: run {
                val generated = UUID.randomUUID().toString()
                prefs.edit().putString("local_user_id", generated).apply()
                generated
            }

    val currentUserName: String
        get() = prefs.getString("user_name", "You") ?: "You"

    // =========================================================================
    // STATE FLOWS
    // =========================================================================

    private val _activeFamilyId = MutableStateFlow(
        prefs.getString("active_family_id", null)?.takeIf { isValidUuid(it) }
            ?: "00000000-0000-0000-0000-000000000001"
    )
    val activeFamilyId: StateFlow<String> = _activeFamilyId.asStateFlow()

    private val _selectedSubTab = MutableStateFlow(0)
    private val _searchQuery = MutableStateFlow("")
    private val _filterType = MutableStateFlow<TransactionType?>(null)
    private val _filterCategory = MutableStateFlow<String?>(null)
    private val _filterMemberId = MutableStateFlow<String?>(null)
    private val _sortBy = MutableStateFlow(TransactionSortOption.NEWEST_FIRST)
    private val _selectedMonthYear = MutableStateFlow(
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    )
    private val _userErrorMessage = MutableStateFlow<String?>(null)
    private val _isSavingTransaction = MutableStateFlow(false)
    private val _transactionSaveResult = MutableStateFlow<String?>(null)

    // =========================================================================
    // INIT
    // =========================================================================

    init {
        val db = CashFlowDatabase.getDatabase(application)
        repository = FamilyLedgerRepository(
            ledgerDao = db.familyLedgerDao(),
            legacyTransactionDao = db.transactionDao(),
            legacyFamilyDao = db.familyDao(),
            legacyMemberDao = db.familyMemberDao()
        )

        // Resolve / create default vault, then start sync
        viewModelScope.launch {
            val defaultVault = repository.getOrCreateDefaultVault(currentUserId, currentUserName)
            val currentPref = prefs.getString("active_family_id", null)
            if (currentPref == null || !isValidUuid(currentPref) ||
                currentPref == "00000000-0000-0000-0000-000000000001") {
                setActiveFamily(defaultVault.familyId)
            }
        }

        // Start Realtime + sync whenever the active family changes
        viewModelScope.launch {
            _activeFamilyId.collect { fId ->
                if (fId.isNotBlank() && isValidUuid(fId)) {
                    repository.startSync(fId, currentUserId)
                }
            }
        }
    }

    // =========================================================================
    // UI STATE (single combined StateFlow)
    // =========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FamilyLedgerUiState> = combine(
        _activeFamilyId.flatMapLatest { fId -> repository.observeFamilyVault(fId) },
        repository.observeAllFamilyVaults(),
        _activeFamilyId.flatMapLatest { fId -> repository.observeTransactions(fId) },
        _activeFamilyId.flatMapLatest { fId -> repository.observeMembers(fId) },
        repository.syncStatus
    ) { activeVault, allVaults, transactions, members, syncStatus ->
        // Provide a placeholder vault when Room hasn't loaded yet
        val effectiveVault = activeVault ?: FamilyVault(
            familyId = _activeFamilyId.value,
            familyName = if (currentUserName.isNotBlank() && currentUserName != "You")
                "$currentUserName's Family Vault" else "My Family Vault",
            inviteCode = "FAM-" + _activeFamilyId.value.take(6).uppercase()
        )
        FamilyLedgerUiState(
            activeVault = effectiveVault,
            allVaults = allVaults.ifEmpty { listOf(effectiveVault) },
            transactions = transactions,
            members = members,
            syncStatus = syncStatus
        )
    }
    .combine(_selectedSubTab) { s, v -> s.copy(selectedSubTab = v) }
    .combine(_searchQuery) { s, v -> s.copy(searchQuery = v) }
    .combine(_filterType) { s, v -> s.copy(filterType = v) }
    .combine(_filterCategory) { s, v -> s.copy(filterCategory = v) }
    .combine(_filterMemberId) { s, v -> s.copy(filterMemberId = v) }
    .combine(_sortBy) { s, v -> s.copy(sortBy = v) }
    .combine(_selectedMonthYear) { s, v -> s.copy(selectedMonthYear = v) }
    .combine(_userErrorMessage) { s, v -> s.copy(userErrorMessage = v) }
    .combine(_isSavingTransaction) { s, v -> s.copy(isSavingTransaction = v) }
    .combine(_transactionSaveResult) { s, v -> s.copy(transactionSaveResult = v) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FamilyLedgerUiState(isLoading = true)
    )

    // =========================================================================
    // UI FILTER & NAVIGATION MUTATIONS
    // =========================================================================

    fun setSubTab(index: Int) { _selectedSubTab.value = index }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setFilterType(type: TransactionType?) { _filterType.value = type }
    fun setFilterCategory(category: String?) { _filterCategory.value = category }
    fun setFilterMemberId(memberId: String?) { _filterMemberId.value = memberId }
    fun setSortBy(sort: TransactionSortOption) { _sortBy.value = sort }
    fun setSelectedMonthYear(monthYear: String) { _selectedMonthYear.value = monthYear }

    fun clearError() { _userErrorMessage.value = null }
    fun clearSaveResult() { _transactionSaveResult.value = null }

    fun setActiveFamily(familyId: String) {
        if (familyId == _activeFamilyId.value) return
        _activeFamilyId.value = familyId
        prefs.edit().putString("active_family_id", familyId).apply()
        // Reset filters when switching vaults
        _searchQuery.value = ""
        _filterType.value = null
        _filterCategory.value = null
        _filterMemberId.value = null
    }

    // =========================================================================
    // TRANSACTION MUTATIONS
    // =========================================================================

    fun createTransaction(
        title: String,
        description: String,
        amount: Double,
        category: String,
        type: TransactionType,
        paymentMethod: String,
        paidByMemberId: String,
        paidByName: String,
        dateMillis: Long,
        onComplete: (Boolean) -> Unit
    ) {
        if (amount <= 0.0) {
            _userErrorMessage.value = "Amount must be greater than ₹0"
            onComplete(false)
            return
        }
        if (title.isBlank()) {
            _userErrorMessage.value = "Please enter a transaction title"
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _isSavingTransaction.value = true
            _transactionSaveResult.value = "Saving to Family Vault…"
            try {
                repository.createTransaction(
                    familyId = _activeFamilyId.value,
                    title = title,
                    description = description,
                    amount = amount,
                    category = category,
                    type = type,
                    paymentMethod = paymentMethod,
                    paidByMemberId = paidByMemberId,
                    paidByName = paidByName.ifBlank { currentUserName },
                    dateMillis = dateMillis,
                    currentUserId = currentUserId
                )
                _transactionSaveResult.value = "Saved!"
                onComplete(true)
            } catch (e: Exception) {
                _userErrorMessage.value = "Could not save transaction: ${e.localizedMessage}"
                _transactionSaveResult.value = "Failed"
                onComplete(false)
            } finally {
                _isSavingTransaction.value = false
            }
        }
    }

    fun updateTransaction(
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
        onComplete: (Boolean) -> Unit
    ) {
        if (amount <= 0.0) {
            _userErrorMessage.value = "Amount must be greater than ₹0"
            onComplete(false)
            return
        }
        if (title.isBlank()) {
            _userErrorMessage.value = "Please enter a transaction title"
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _isSavingTransaction.value = true
            _transactionSaveResult.value = "Updating…"
            try {
                repository.updateTransaction(
                    existing = existing,
                    title = title,
                    description = description,
                    amount = amount,
                    category = category,
                    type = type,
                    paymentMethod = paymentMethod,
                    paidByMemberId = paidByMemberId,
                    paidByName = paidByName,
                    dateMillis = dateMillis,
                    currentUserId = currentUserId
                )
                _transactionSaveResult.value = "Updated!"
                onComplete(true)
            } catch (e: Exception) {
                _userErrorMessage.value = "Could not update transaction: ${e.localizedMessage}"
                _transactionSaveResult.value = "Failed"
                onComplete(false)
            } finally {
                _isSavingTransaction.value = false
            }
        }
    }

    fun deleteTransaction(transactionId: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transactionId, currentUserId)
                onComplete?.invoke(true)
            } catch (e: Exception) {
                _userErrorMessage.value = "Could not delete: ${e.localizedMessage}"
                onComplete?.invoke(false)
            }
        }
    }

    // =========================================================================
    // VAULT & MEMBER MUTATIONS
    // =========================================================================

    fun createFamilyVault(familyName: String, onComplete: ((Boolean) -> Unit)? = null) {
        if (familyName.isBlank()) {
            _userErrorMessage.value = "Please enter a family vault name"
            onComplete?.invoke(false)
            return
        }
        viewModelScope.launch {
            try {
                val vault = repository.createFamilyVault(familyName, currentUserId, currentUserName)
                setActiveFamily(vault.familyId)
                onComplete?.invoke(true)
            } catch (e: Exception) {
                _userErrorMessage.value = "Failed to create vault: ${e.localizedMessage}"
                onComplete?.invoke(false)
            }
        }
    }

    fun joinFamilyByInviteCode(inviteCode: String, onResult: (Boolean, String) -> Unit) {
        val clean = inviteCode.trim().uppercase()
        if (clean.isBlank()) {
            onResult(false, "Please enter a valid Invite Code")
            return
        }
        val targetUserId = currentUserId
        viewModelScope.launch {
            val result = repository.joinFamilyByInviteCode(clean, targetUserId, currentUserName)
            result
                .onSuccess { vault ->
                    setActiveFamily(vault.familyId)
                    onResult(true, "Connected to ${vault.familyName}!")
                }
                .onFailure { e ->
                    onResult(false, e.localizedMessage ?: "Failed to join vault")
                }
        }
    }

    fun handleScannedVaultQr(qrText: String, onResult: (Boolean, String) -> Unit) {
        val trimmed = qrText.trim()
        val parsed = repository.parseScannedVaultQr(trimmed)
        if (parsed != null && isValidUuid(parsed.familyId)) {
            viewModelScope.launch {
                val directResult = repository.joinFamilyVaultDirect(
                    familyId = parsed.familyId,
                    familyName = parsed.familyName,
                    inviteCode = parsed.inviteCode,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName
                )
                directResult
                    .onSuccess { vault ->
                        setActiveFamily(vault.familyId)
                        onResult(true, "Connected to ${vault.familyName}!")
                    }
                    .onFailure { e ->
                        onResult(false, e.localizedMessage ?: "Failed to join vault")
                    }
            }
            return
        }
        val codeToJoin = parsed?.inviteCode?.takeIf { it.isNotBlank() } ?: trimmed
        joinFamilyByInviteCode(codeToJoin, onResult)
    }

    fun addFamilyMember(name: String, role: FamilyRole, onComplete: ((Boolean) -> Unit)? = null) {
        if (name.isBlank()) {
            _userErrorMessage.value = "Member name cannot be empty"
            onComplete?.invoke(false)
            return
        }
        viewModelScope.launch {
            try {
                repository.addMember(_activeFamilyId.value, name, role, currentUserId)
                onComplete?.invoke(true)
            } catch (e: Exception) {
                _userErrorMessage.value = "Failed to add member: ${e.localizedMessage}"
                onComplete?.invoke(false)
            }
        }
    }

    fun removeFamilyMember(memberId: String) {
        viewModelScope.launch {
            try {
                repository.removeMember(memberId)
            } catch (e: Exception) {
                _userErrorMessage.value = "Failed to remove member: ${e.localizedMessage}"
            }
        }
    }

    /** Triggers an immediate full reconciliation sync for the active family. */
    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncWithCloud(_activeFamilyId.value, currentUserId)
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun isValidUuid(str: String): Boolean = try {
        UUID.fromString(str); true
    } catch (e: Exception) { false }

    override fun onCleared() {
        super.onCleared()
        repository.stopSync()
    }
}
