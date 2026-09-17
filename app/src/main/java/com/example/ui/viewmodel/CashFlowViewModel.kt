package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiAiService
import com.example.data.ai.ParsedReceipt
import com.example.data.ai.ParsedVoiceExpense
import com.example.data.database.CashFlowDatabase
import com.example.data.familyledger.FamilyLedgerRepository
import com.example.data.familyledger.VaultSyncQrData
import com.example.data.familyledger.VaultImportSummary
import com.example.data.network.AuthResult
import com.example.data.network.AuthUser
import com.example.data.models.*
import com.example.data.repository.CashFlowRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.*
import com.example.data.ai.VoiceAssistantIntent
import com.example.data.ai.VoiceAssistantResponse
import com.example.data.network.SupabaseClientConfig

enum class MessageSender {
    USER,
    ASSISTANT
}

data class VoiceChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val parsedExpense: ParsedVoiceExpense? = null,
    val isActionCompleted: Boolean = false,
    val intent: VoiceAssistantIntent = VoiceAssistantIntent.LOG_TRANSACTION
)

sealed interface SyncUiState {
    object Idle : SyncUiState
    object Syncing : SyncUiState
    data class Success(val lastSyncedTimeMillis: Long) : SyncUiState
    data class Error(val message: String) : SyncUiState
}

data class MemberContributionItem(
    val memberId: String,
    val name: String,
    val totalPaid: Double,
    val shareDiff: Double // positive = paid more than fair share (is owed), negative = owes
)

data class FamilyLedgerSettlementSummary(
    val personalTotalExpense: Double = 0.0,
    val familyTotalExpense: Double = 0.0,
    val userPaidForFamily: Double = 0.0,
    val fairSharePerMember: Double = 0.0,
    val netSettlementBalance: Double = 0.0, // positive = owed to user, negative = user owes
    val memberCount: Int = 1,
    val memberContributions: List<MemberContributionItem> = emptyList()
)

data class CashFlowUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val savingsGoals: List<SavingsGoalEntity> = emptyList(),
    val scannedItems: List<ScannedItemEntity> = emptyList(),
    val currencySymbol: String = "₹",
    val searchQuery: String = "",
    val selectedFilterType: TransactionType? = null,
    val selectedFilterCategory: String? = null,
    val selectedFilterMember: String? = null,
    val isVoiceDialogShowing: Boolean = false,
    val isVoiceProcessing: Boolean = false,
    val parsedVoiceExpense: ParsedVoiceExpense? = null,
    val voiceChatMessages: List<VoiceChatMessage> = emptyList(),
    val latestAssistantResponse: VoiceAssistantResponse? = null,
    val isUpiDialogShowing: Boolean = false,
    val isUpiScanDialogShowing: Boolean = false,
    val isReceiptDialogShowing: Boolean = false,
    val isReceiptProcessing: Boolean = false,
    val parsedReceipt: ParsedReceipt? = null,
    val aiCoachAdvice: String? = null,
    val isAiCoachLoading: Boolean = false,
    val selectedTab: Int = 0,
    val scannedBarcodeValue: String? = null,
    val isScannedBarcodeSheetShowing: Boolean = false,
    val hasCompletedOnboarding: Boolean = true,
    val defaultPaymentMethod: String = "UPI",
    val defaultTransactionType: TransactionType = TransactionType.EXPENSE,
    val isHapticEnabled: Boolean = true,
    val isNotificationEnabled: Boolean = true,
    val detectedUpiPayment: com.example.data.upi.ExtractedUpiPayment? = null
)

class CashFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CashFlowRepository
    val familyLedgerRepository: FamilyLedgerRepository

    private val _searchQuery = MutableStateFlow("")
    private val _filterType = MutableStateFlow<TransactionType?>(null)
    private val _filterCategory = MutableStateFlow<String?>(null)
    private val _filterMember = MutableStateFlow<String?>(null)
    private val _currencySymbol = MutableStateFlow("₹")
    private val _selectedTab = MutableStateFlow(0)

    private val _voiceDialogShowing = MutableStateFlow(false)
    private val _voiceProcessing = MutableStateFlow(false)
    private val _parsedVoice = MutableStateFlow<ParsedVoiceExpense?>(null)
    private val _voiceChatMessages = MutableStateFlow<List<VoiceChatMessage>>(emptyList())
    private val _latestAssistantResponse = MutableStateFlow<VoiceAssistantResponse?>(null)

    private val _upiDialogShowing = MutableStateFlow(false)
    private val _upiScanDialogShowing = MutableStateFlow(false)
    private val _detectedUpiPayment = MutableStateFlow<com.example.data.upi.ExtractedUpiPayment?>(null)

    private val _receiptDialogShowing = MutableStateFlow(false)
    private val _receiptProcessing = MutableStateFlow(false)
    private val _parsedReceipt = MutableStateFlow<ParsedReceipt?>(null)

    private val _aiCoachAdvice = MutableStateFlow<String?>(null)
    private val _aiCoachLoading = MutableStateFlow(false)

    private val _scannedBarcodeValue = MutableStateFlow<String?>(null)
    private val _isScannedBarcodeSheetShowing = MutableStateFlow(false)

    private val syncEngine: com.example.data.network.SyncEngine
    private val userProfileRepository: com.example.data.repository.UserProfileRepository
    val authService: com.example.data.network.AuthService = com.example.data.network.SupabaseAuthService()

    init {
        val database = CashFlowDatabase.getDatabase(application)
        repository = CashFlowRepository(
            database.transactionDao(),
            database.categoryDao(),
            database.budgetDao(),
            database.savingsGoalDao(),
            database.scannedItemDao(),
            database.familyDao(),
            database.familyMemberDao(),
            database.receiptDao()
        )
        familyLedgerRepository = FamilyLedgerRepository(
            ledgerDao = database.familyLedgerDao(),
            legacyTransactionDao = database.transactionDao(),
            legacyFamilyDao = database.familyDao(),
            legacyMemberDao = database.familyMemberDao()
        )
        syncEngine = com.example.data.network.SyncEngine(
            transactionDao = database.transactionDao(),
            familyDao = database.familyDao(),
            authService = authService,
            familyMemberDao = database.familyMemberDao(),
            budgetDao = database.budgetDao(),
            savingsGoalDao = database.savingsGoalDao()
        )

        userProfileRepository = com.example.data.repository.UserProfileRepository(database.userProfileDao())
        
        viewModelScope.launch(Dispatchers.IO) {
            authService.restoreSession()
            authService.currentUser.collect { user ->
                if (user != null) {
                    _userSupabaseId.value = user.id
                    _activeUserEmail.value = user.email
                    _activeUserName.value = user.fullName
                    _isAuthenticated.value = true
                    _isGuestMode.value = false
                    userProfileRepository.syncProfile(user.id)
                    // Reassign local records created as guest/offline and execute sync
                    repository.reassignPersonalTransactionsToUser(user.id)
                    executeSync()
                }
            }
        }

        // Automatic background cloud synchronization loop (Periodic every 15s when authenticated)
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15000)
                val uid = authService.currentUser.value?.id ?: _userSupabaseId.value
                if (!uid.isNullOrBlank() && syncEngine.isValidUuid(uid)) {
                    try {
                        val famId = _activeFamilyId.value
                        syncEngine.syncAll(uid, famId)
                        if (!famId.isNullOrBlank()) {
                            familyLedgerRepository.syncWithCloud(famId, uid)
                        }
                    } catch (e: Exception) {
                        // background sync tick
                    }
                }
            }
        }

        // Listen to real-time background UPI payment detections
        viewModelScope.launch(Dispatchers.IO) {
            com.example.data.upi.UpiPaymentBus.detectedPayments.collect { payment ->
                _detectedUpiPayment.value = payment
            }
        }
    }

    val currentMonthYear: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            return sdf.format(Date())
        }


    private val prefs = application.getSharedPreferences("zenith_prefs", android.content.Context.MODE_PRIVATE)

    private val _currentFinanceScope = MutableStateFlow(FinanceScope.PERSONAL)
    val currentFinanceScope: StateFlow<FinanceScope> = _currentFinanceScope.asStateFlow()

    private val _activeFamilyId = MutableStateFlow<String?>(prefs.getString("active_family_id", null))
    val activeFamilyId: StateFlow<String?> = _activeFamilyId.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(prefs.getBoolean("is_authenticated", false))
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _activeUserEmail = MutableStateFlow(prefs.getString("user_email", null))
    val activeUserEmail: StateFlow<String?> = _activeUserEmail.asStateFlow()

    private val _activeUserName = MutableStateFlow(prefs.getString("user_name", null))
    val activeUserName: StateFlow<String?> = _activeUserName.asStateFlow()

    private val _isGuestMode = MutableStateFlow(prefs.getBoolean("is_guest_mode", false))
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _defaultPaymentMethod = MutableStateFlow(prefs.getString("default_payment_method", "UPI") ?: "UPI")
    val defaultPaymentMethod: StateFlow<String> = _defaultPaymentMethod.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isHapticsEnabled = MutableStateFlow(prefs.getBoolean("is_haptics_enabled", true))
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private val _isNotificationsEnabled = MutableStateFlow(prefs.getBoolean("is_notifications_enabled", true))
    val isNotificationsEnabled: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    private val _userSupabaseId = MutableStateFlow<String?>(prefs.getString("user_supabase_id", null))
    val userSupabaseId: StateFlow<String?> = _userSupabaseId.asStateFlow()

    private val _syncUiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    val currentUserId: String
        get() = authService.currentUser.value?.id
            ?: _userSupabaseId.value
            ?: "local_user_1"
    val currentUserName: String
        get() = _activeUserName.value ?: authService.currentUser.value?.email?.substringBefore("@") ?: "You"

    fun setDefaultPaymentMethod(method: String) {
        _defaultPaymentMethod.value = method
        prefs.edit().putString("default_payment_method", method).apply()
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean("is_dark_mode", enabled).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        prefs.edit().putBoolean("is_haptics_enabled", enabled).apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _isNotificationsEnabled.value = enabled
        prefs.edit().putBoolean("is_notifications_enabled", enabled).apply()
    }

    fun updateUserName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            _activeUserName.value = trimmed
            prefs.edit().putString("user_name", trimmed).apply()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeFamily: Flow<FamilyEntity?> = _activeFamilyId.flatMapLatest { id ->
        flow {
            val famId = id ?: getOrCreateFamilyIdSync()
            val legacy = repository.getFamilyById(famId)
            if (legacy != null) {
                emit(legacy)
                return@flow
            }
            val vault = familyLedgerRepository.getFamilyVault(famId)
            if (vault != null) {
                val entity = FamilyEntity(
                    id = vault.familyId,
                    name = vault.familyName,
                    createdByUserId = vault.createdBy,
                    createdAt = vault.createdAt,
                    updatedAt = vault.updatedAt,
                    inviteCode = vault.inviteCode,
                    serverId = vault.familyId,
                    syncStatus = vault.syncStatus
                )
                repository.insertFamily(entity)
                emit(entity)
            } else {
                emit(repository.getFirstFamily())
            }
        }
    }
    
    val userFamilies: Flow<List<FamilyEntity>> = repository.getAllFamilies()
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val familyMembers: Flow<List<FamilyMemberEntity>> = _activeFamilyId.flatMapLatest { id ->
        val famId = id ?: getOrCreateFamilyIdSync()
        combine(
            repository.getMembersByFamilyId(famId),
            familyLedgerRepository.observeMembers(famId)
        ) { legacyList, vaultList ->
            val map = linkedMapOf<String, FamilyMemberEntity>()
            for (m in legacyList) {
                if (!m.isDeleted) map[m.id] = m
            }
            for (vm in vaultList) {
                if (!vm.isDeleted) {
                    map[vm.memberId] = FamilyMemberEntity(
                        id = vm.memberId,
                        familyId = vm.familyId,
                        userId = vm.userId,
                        name = vm.name,
                        role = vm.role,
                        joinedAt = vm.joinedAt,
                        syncStatus = vm.syncStatus,
                        isDeleted = vm.isDeleted
                    )
                }
            }
            map.values.toList()
        }
    }

    val familySettlementSummary: StateFlow<FamilyLedgerSettlementSummary> = combine(
        repository.allTransactions,
        familyMembers
    ) { allTxs, members ->
        val personalExpenses = allTxs.filter { it.financeScope == FinanceScope.PERSONAL && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val familyExpenses = allTxs.filter { it.financeScope == FinanceScope.FAMILY && it.type == TransactionType.EXPENSE }
        val familyTotal = familyExpenses.sumOf { it.amount }

        val activeMemberCount = members.size.coerceAtLeast(1)
        val fairShare = if (activeMemberCount > 0) familyTotal / activeMemberCount else 0.0

        val userPaid = familyExpenses.filter {
            it.createdByUserId == currentUserId || it.createdByUserId == currentUserName
        }.sumOf { it.amount }

        val netBalance = userPaid - fairShare

        val memberItems = members.map { member ->
            val paid = familyExpenses.filter {
                it.createdByUserId == member.userId || it.createdByUserId == member.name
            }.sumOf { it.amount }
            MemberContributionItem(
                memberId = member.userId,
                name = member.name,
                totalPaid = paid,
                shareDiff = paid - fairShare
            )
        }

        FamilyLedgerSettlementSummary(
            personalTotalExpense = personalExpenses,
            familyTotalExpense = familyTotal,
            userPaidForFamily = userPaid,
            fairSharePerMember = fairShare,
            netSettlementBalance = netBalance,
            memberCount = activeMemberCount,
            memberContributions = memberItems
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FamilyLedgerSettlementSummary()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseDataState: Flow<BaseData> = combine(
        _currentFinanceScope,
        _activeFamilyId
    ) { scope, familyId -> Pair(scope, familyId) }.flatMapLatest { (scope, familyId) ->
        val txFlow = if (scope == FinanceScope.FAMILY) {
            if (familyId != null) repository.getFamilyTransactions(familyId)
            else repository.getTransactionsByScope(FinanceScope.FAMILY)
        } else {
            repository.getTransactionsByScope(FinanceScope.PERSONAL)
        }
        val budgetFlow = if (scope == FinanceScope.FAMILY) {
            if (familyId != null) repository.getBudgetsByFamilyId(familyId)
            else repository.getBudgetsByScope(FinanceScope.FAMILY)
        } else {
            repository.getBudgetsByScope(FinanceScope.PERSONAL)
        }
        val goalFlow = if (scope == FinanceScope.FAMILY) {
            if (familyId != null) repository.getGoalsByFamilyId(familyId)
            else repository.getGoalsByScope(FinanceScope.FAMILY)
        } else {
            repository.getGoalsByScope(FinanceScope.PERSONAL)
        }

        combine(
            txFlow,
            repository.allCategories,
            budgetFlow,
            goalFlow,
            repository.allScannedItems
        ) { txList, catList, budgetList, goalList, scannedList ->
            BaseData(txList, catList, budgetList, goalList, scannedList)
        }
    }


    private val filterState: Flow<FilterState> = combine(
        _searchQuery,
        _filterType,
        _filterCategory,
        _filterMember
    ) { search, type, cat, member ->
        object { val s = search; val t = type; val c = cat; val m = member }
    }.combine(combine(_currencySymbol, _selectedTab, ::Pair)) { f1, f2 ->
        FilterState(f1.s, f1.t, f1.c, f1.m, f2.first, f2.second)
    }

    private val voiceState: Flow<VoiceState> = combine(
        combine(_voiceDialogShowing, _voiceProcessing, _parsedVoice, ::Triple),
        combine(_voiceChatMessages, _latestAssistantResponse, ::Pair),
        combine(_upiDialogShowing, _upiScanDialogShowing, _detectedUpiPayment, ::Triple)
    ) { (voiceShow, voiceProc, voiceParsed), (messages, latestResp), (upiShow, upiScanShow, detectedPayment) ->
        VoiceState(voiceShow, voiceProc, voiceParsed, messages, latestResp, upiShow, upiScanShow, detectedPayment)
    }

    private val aiState: Flow<AiState> = combine(
        _receiptDialogShowing,
        _receiptProcessing,
        _parsedReceipt,
        _aiCoachAdvice,
        _aiCoachLoading
    ) { receiptShow, receiptProc, receiptParsed, coachAdvice, coachLoading ->
        AiState(receiptShow, receiptProc, receiptParsed, coachAdvice, coachLoading)
    }

    private val scannedItemState: Flow<ScannedItemState> = combine(
        _scannedBarcodeValue,
        _isScannedBarcodeSheetShowing
    ) { barcodeValue, sheetShowing ->
        ScannedItemState(barcodeValue, sheetShowing)
    }

    val uiState: StateFlow<CashFlowUiState> = combine(
        baseDataState,
        filterState,
        voiceState,
        aiState,
        scannedItemState
    ) { base, filter, voice, ai, scannedItem ->
        val filteredTx = base.transactions.filter { tx ->
            val matchesSearch = filter.search.isBlank() ||
                    tx.title.contains(filter.search, ignoreCase = true) ||
                    tx.category.contains(filter.search, ignoreCase = true) ||
                    tx.paymentMethod.contains(filter.search, ignoreCase = true) ||
                    tx.note.contains(filter.search, ignoreCase = true)

            val matchesType = filter.type == null || tx.type == filter.type
            val matchesCat = filter.category == null || tx.category == filter.category
            val matchesMember = filter.member == null || tx.createdByUserId == filter.member

            matchesSearch && matchesType && matchesCat && matchesMember
        }

        CashFlowUiState(
            transactions = filteredTx,
            categories = base.categories,
            budgets = base.budgets,
            savingsGoals = base.savingsGoals,
            scannedItems = base.scannedItems,
            currencySymbol = filter.currency,
            searchQuery = filter.search,
            selectedFilterType = filter.type,
            selectedFilterCategory = filter.category,
            selectedFilterMember = filter.member,
            isVoiceDialogShowing = voice.voiceShow,
            isVoiceProcessing = voice.voiceProc,
            parsedVoiceExpense = voice.voiceParsed,
            voiceChatMessages = voice.voiceMessages,
            latestAssistantResponse = voice.latestAssistantResponse,
            isUpiDialogShowing = voice.upiShow,
            isUpiScanDialogShowing = voice.upiScanShow,
            isReceiptDialogShowing = ai.receiptShow,
            isReceiptProcessing = ai.receiptProc,
            parsedReceipt = ai.receiptParsed,
            aiCoachAdvice = ai.coachAdvice,
            isAiCoachLoading = ai.coachLoading,
            selectedTab = filter.tab,
            scannedBarcodeValue = scannedItem.barcodeValue,
            isScannedBarcodeSheetShowing = scannedItem.sheetShowing,
            detectedUpiPayment = voice.detectedPayment
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CashFlowUiState()
    )

    fun getTotalIncome(transactions: List<TransactionEntity>): Double {
        return transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }

    fun getTotalExpense(transactions: List<TransactionEntity>): Double {
        return transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    fun getNetBalance(transactions: List<TransactionEntity>): Double {
        return getTotalIncome(transactions) - getTotalExpense(transactions)
    }

    fun setTab(index: Int) {
        _selectedTab.value = index
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: TransactionType?) {
        _filterType.value = type
    }

    fun setFilterCategory(category: String?) {
        _filterCategory.value = category
    }

    fun setFilterMember(memberId: String?) {
        _filterMember.value = memberId
    }

    fun setCurrency(symbol: String) {
        _currencySymbol.value = symbol
    }


    val currentUser = authService.currentUser
    @OptIn(ExperimentalCoroutinesApi::class)
    val localUserProfile: StateFlow<UserProfileEntity?> = authService.currentUser.flatMapLatest { user ->
        if (user != null) {
            userProfileRepository.getProfileFlow(user.id)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    fun setAuthenticatedUser(email: String, name: String, userId: String? = null, isGuest: Boolean = false) {
        val editor = prefs.edit()
            .putBoolean("is_authenticated", true)
            .putBoolean("is_guest_mode", isGuest)
            .putString("user_email", email)
            .putString("user_name", name)
        if (userId != null) {
            editor.putString("user_supabase_id", userId)
        } else if (isGuest) {
            editor.remove("user_supabase_id")
        }
        editor.apply()

        _isAuthenticated.value = true
        _isGuestMode.value = isGuest
        _activeUserEmail.value = email
        _activeUserName.value = name
        if (userId != null) {
            _userSupabaseId.value = userId
        } else if (isGuest) {
            _userSupabaseId.value = null
        }
    }

    fun signIn(email: String, pass: String, onResult: (AuthResult) -> Unit) {
        viewModelScope.launch {
            val result = authService.signIn(email, pass)
            if (result.success) {
                val user = result.user ?: authService.currentUser.value
                val cleanName = user?.fullName?.ifBlank { null }
                    ?: user?.email?.substringBefore("@")
                    ?: email.substringBefore("@")
                val userId = user?.id
                setAuthenticatedUser(email, cleanName, userId = userId, isGuest = false)

                if (userId != null && syncEngine.isValidUuid(userId)) {
                    repository.reassignPersonalTransactionsToUser(userId)
                    executeSync()
                }
            }
            onResult(result)
        }
    }

    fun signUp(email: String, pass: String, name: String, onResult: (AuthResult) -> Unit) {
        viewModelScope.launch {
            val result = authService.signUp(email, pass, name)
            if (result.success && !result.requiresEmailConfirmation) {
                val user = result.user ?: authService.currentUser.value
                val cleanName = user?.fullName?.ifBlank { null } ?: name.ifBlank { email.substringBefore("@") }
                val userId = user?.id
                setAuthenticatedUser(email, cleanName, userId = userId, isGuest = false)

                if (userId != null && syncEngine.isValidUuid(userId)) {
                    repository.reassignPersonalTransactionsToUser(userId)
                    executeSync()
                }
            }
            onResult(result)
        }
    }

    fun resetPassword(email: String, onResult: (AuthResult) -> Unit) {
        viewModelScope.launch {
            val result = authService.resetPassword(email)
            onResult(result)
        }
    }

    fun continueAsGuest() {
        setAuthenticatedUser("guest@zenith.vault", "Guest Explorer", userId = null, isGuest = true)
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            prefs.edit()
                .putBoolean("is_authenticated", false)
                .putBoolean("is_guest_mode", false)
                .remove("user_email")
                .remove("user_name")
                .remove("user_supabase_id")
                .apply()
            _isAuthenticated.value = false
            _isGuestMode.value = false
            _activeUserEmail.value = null
            _activeUserName.value = null
            _userSupabaseId.value = null
            _syncUiState.value = SyncUiState.Idle
        }
    }

    fun syncNow(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = executeSync()
            withContext(Dispatchers.Main) {
                onComplete?.invoke(success)
            }
        }
    }

    suspend fun executeSync(): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseClientConfig.isConfigured) {
            _syncUiState.value = SyncUiState.Success(System.currentTimeMillis())
            return@withContext true
        }
        val userId = authService.currentUser.value?.id ?: _userSupabaseId.value
        if (userId.isNullOrBlank() || !syncEngine.isValidUuid(userId)) {
            _syncUiState.value = SyncUiState.Error("Please sign in with a Zenith account to sync.")
            return@withContext false
        }
        _syncUiState.value = SyncUiState.Syncing
        val success = syncEngine.syncAll(userId)
        if (success) {
            _syncUiState.value = SyncUiState.Success(System.currentTimeMillis())
        } else {
            _syncUiState.value = SyncUiState.Error("Cloud sync failed. Check your network connection.")
        }
        success
    }

    fun addTransaction(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMethod: String,
        note: String,
        memberId: String? = null,
        scope: FinanceScope? = null,
        dateMillis: Long = System.currentTimeMillis(),
        upiId: String? = null,
        upiTransactionId: String? = null
    ) {
        viewModelScope.launch {
            val iconName = when (category) {
                "Food & Dining" -> "Restaurant"
                "Shopping" -> "ShoppingBag"
                "Housing & Rent" -> "Home"
                "Transportation" -> "DirectionsCar"
                "Bills & Utilities" -> "Receipt"
                "Entertainment" -> "Movie"
                "Healthcare", "Health" -> "MedicalServices"
                "Salary & Income", "Income" -> "Payments"
                "Freelance / Business" -> "Work"
                else -> "Category"
            }
            val targetScope = scope ?: _currentFinanceScope.value
            val isFamily = targetScope == FinanceScope.FAMILY
            val fId = if (isFamily) getOrCreateFamilyIdSync() else null
            val creator = if (isFamily) (memberId ?: currentUserId) else null

            // 1. Direct write to decoupled high-reliability family ledger if in family scope
            if (isFamily && fId != null) {
                val payerMemberId = memberId ?: currentUserId
                val payerName = currentUserName
                familyLedgerRepository.createTransaction(
                    familyId = fId,
                    title = title,
                    description = note,
                    amount = amount,
                    category = category,
                    type = type,
                    paymentMethod = paymentMethod,
                    paidByMemberId = payerMemberId,
                    paidByName = payerName,
                    dateMillis = dateMillis,
                    currentUserId = currentUserId
                )
            }

            // 2. Also write to Room TransactionEntity for backward compatibility
            repository.addTransaction(
                TransactionEntity(
                    title = title,
                    amount = amount,
                    type = type,
                    category = category,
                    categoryIconName = iconName,
                    paymentMethod = paymentMethod,
                    note = note,
                    financeScope = if (isFamily) FinanceScope.FAMILY else FinanceScope.PERSONAL,
                    familyId = fId,
                    createdByUserId = creator,
                    dateMillis = dateMillis,
                    upiId = upiId,
                    upiTransactionId = upiTransactionId,
                    syncStatus = "PENDING_CREATE"
                )
            )

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addUpiTransaction(
        title: String,
        amount: Double,
        category: String,
        scope: FinanceScope,
        memberId: String?,
        upiId: String?,
        upiTransactionId: String?,
        dateMillis: Long = System.currentTimeMillis(),
        note: String = "UPI Payment"
    ) {
        addTransaction(
            title = title,
            amount = amount,
            type = TransactionType.EXPENSE,
            category = category,
            paymentMethod = "UPI",
            note = note,
            memberId = memberId,
            scope = scope,
            dateMillis = dateMillis,
            upiId = upiId,
            upiTransactionId = upiTransactionId
        )
    }

    fun dismissDetectedUpiPayment() {
        _detectedUpiPayment.value = null
    }

    fun confirmDetectedUpiPayment(
        title: String,
        amount: Double,
        category: String,
        scope: FinanceScope,
        memberId: String?,
        upiId: String?,
        upiTransactionId: String?
    ) {
        addUpiTransaction(
            title = title,
            amount = amount,
            category = category,
            scope = scope,
            memberId = memberId,
            upiId = upiId,
            upiTransactionId = upiTransactionId
        )
        _detectedUpiPayment.value = null
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction.copy(syncStatus = "PENDING_UPDATE"))
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            val toDelete = transaction.copy(syncStatus = "PENDING_DELETE", isDeleted = true)
            repository.updateTransaction(toDelete)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun importTransactions(transactions: List<TransactionEntity>) {
        viewModelScope.launch {
            transactions.forEach { tx ->
                repository.addTransaction(tx)
            }
        }
    }

    fun restoreFullBackup(
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        goals: List<SavingsGoalEntity>
    ) {
        viewModelScope.launch {
            transactions.forEach { repository.addTransaction(it) }
            budgets.forEach { repository.saveBudget(it) }
            goals.forEach { repository.saveSavingsGoal(it) }
        }
    }

    fun saveBudget(
        categoryName: String,
        limit: Double,
        periodType: String = "MONTHLY",
        customPeriodName: String = "",
        budgetId: Long = 0
    ) {
        viewModelScope.launch {
            val isFamily = _currentFinanceScope.value == FinanceScope.FAMILY
            val fId = if (isFamily) getOrCreateFamilyIdSync() else null
            repository.saveBudget(
                BudgetEntity(
                    id = budgetId,
                    categoryName = categoryName,
                    monthlyLimit = limit,
                    monthYear = currentMonthYear,
                    periodType = periodType,
                    customPeriodName = customPeriodName,
                    financeScope = _currentFinanceScope.value,
                    familyId = fId,
                    syncStatus = "PENDING_CREATE"
                )
            )
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun saveBudgetEntity(budget: BudgetEntity) {
        viewModelScope.launch {
            repository.saveBudget(budget.copy(syncStatus = "PENDING_UPDATE"))
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            repository.deleteBudget(budget.copy(syncStatus = "PENDING_DELETE", isDeleted = true))
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun saveSavingsGoal(
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        goalId: Long = 0
    ) {
        viewModelScope.launch {
            val isFamily = _currentFinanceScope.value == FinanceScope.FAMILY
            val fId = if (isFamily) getOrCreateFamilyIdSync() else null
            repository.saveSavingsGoal(
                SavingsGoalEntity(
                    id = goalId,
                    title = title,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    financeScope = _currentFinanceScope.value,
                    familyId = fId,
                    syncStatus = "PENDING_CREATE"
                )
            )
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun saveSavingsGoalEntity(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.saveSavingsGoal(goal.copy(syncStatus = "PENDING_UPDATE"))
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goal.copy(syncStatus = "PENDING_DELETE", isDeleted = true))
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateGoalDeposit(goal: SavingsGoalEntity, addedAmount: Double) {
        viewModelScope.launch {
            val updated = goal.copy(
                currentAmount = goal.currentAmount + addedAmount,
                syncStatus = "PENDING_UPDATE"
            )
            repository.saveSavingsGoal(updated)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.addCategory(
                CategoryEntity(
                    name = name,
                    iconName = "Star",
                    colorHex = colorHex,
                    type = TransactionType.EXPENSE,
                    isDefault = false
                )
            )
        }
    }

    private fun isValidUuid(str: String): Boolean = try {
        UUID.fromString(str); true
    } catch (e: Exception) { false }

    suspend fun getOrCreateFamilyIdSync(): String {
        val current = _activeFamilyId.value
        if (current != null && isValidUuid(current)) return current

        val vault = familyLedgerRepository.getOrCreateDefaultVault(currentUserId, currentUserName)
        _activeFamilyId.value = vault.familyId
        prefs.edit().putString("active_family_id", vault.familyId).apply()
        return vault.familyId
    }

    fun setFinanceScope(scope: FinanceScope) {
        _currentFinanceScope.value = scope
        if (scope == FinanceScope.FAMILY) {
            ensureFamilyLedger()
        }
    }

    fun ensureFamilyLedger() {
        viewModelScope.launch {
            getOrCreateFamilyIdSync()
        }
    }

    fun setActiveFamily(familyId: String?) {
        _activeFamilyId.value = familyId
        if (familyId != null) {
            prefs.edit().putString("active_family_id", familyId).apply()
        } else {
            prefs.edit().remove("active_family_id").apply()
        }
    }

    fun createFamily(name: String) {
        viewModelScope.launch {
            val vault = familyLedgerRepository.createFamilyVault(name, currentUserId, currentUserName)
            setActiveFamily(vault.familyId)
            setFinanceScope(FinanceScope.FAMILY)
            syncFamilyLedgerNow()
        }
    }

    fun addFamilyMember(name: String, role: FamilyRole) {
        viewModelScope.launch {
            val fId = _activeFamilyId.value ?: getOrCreateFamilyIdSync()
            val vaultMember = familyLedgerRepository.addMember(fId, name, role, currentUserId)
            val legacyMember = FamilyMemberEntity(
                id = vaultMember.memberId,
                familyId = fId,
                userId = vaultMember.userId,
                name = vaultMember.name,
                role = vaultMember.role,
                joinedAt = vaultMember.joinedAt,
                syncStatus = "SYNCED"
            )
            repository.insertMember(legacyMember)
        }
    }

    fun deleteFamilyMember(member: FamilyMemberEntity) {
        viewModelScope.launch {
            familyLedgerRepository.removeMember(member.id)
            repository.updateMember(member.copy(syncStatus = "PENDING_DELETE", isDeleted = true))
        }
    }

    fun joinFamily(inviteCode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val cleanCode = inviteCode.trim().uppercase()
            if (cleanCode.isBlank()) {
                onResult(false, "Please enter a valid Family ID or Invite Code")
                return@launch
            }

            // 1. Decoupled Family Vault Join via FamilyLedgerRepository
            val ledgerJoinResult = familyLedgerRepository.joinFamilyByInviteCode(cleanCode, currentUserId, currentUserName)
            if (ledgerJoinResult.isSuccess) {
                val joinedVault = ledgerJoinResult.getOrThrow()
                setActiveFamily(joinedVault.familyId)
                setFinanceScope(FinanceScope.FAMILY)
                onResult(true, "Successfully linked with ${joinedVault.familyName}!")
                return@launch
            }

            // 2. Fallback: Fetch remote family and transactions (legacy sync engine)
            val (remoteFamily, _) = withContext(Dispatchers.IO) {
                syncEngine.fetchRemoteFamilyByInviteCode(cleanCode)
            }

            if (remoteFamily != null) {
                val canonicalFamId = if (isValidUuid(remoteFamily.id)) remoteFamily.id else UUID.nameUUIDFromBytes(remoteFamily.id.toByteArray()).toString()
                val directResult = familyLedgerRepository.joinFamilyVaultDirect(
                    familyId = canonicalFamId,
                    familyName = remoteFamily.name,
                    inviteCode = remoteFamily.inviteCode ?: cleanCode,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName
                )
                if (directResult.isSuccess) {
                    val v = directResult.getOrThrow()
                    setActiveFamily(v.familyId)
                    setFinanceScope(FinanceScope.FAMILY)
                    onResult(true, "Successfully linked with ${v.familyName}!")
                    return@launch
                }
            }

            val errorMsg = ledgerJoinResult.exceptionOrNull()?.message
                ?: "Family Vault with code '$cleanCode' was not found. Please ensure the other device is online or scan the Family QR code."
            onResult(false, errorMsg)
        }
    }

    fun syncFamilyLedgerNow(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = try {
                withContext(Dispatchers.IO) {
                    val famId = _activeFamilyId.value ?: getOrCreateFamilyIdSync()
                    val cloudSuccess = familyLedgerRepository.syncWithCloud(famId, currentUserId)
                    val syncAllSuccess = syncEngine.syncAll(currentUserId, famId)
                    cloudSuccess || syncAllSuccess
                }
            } catch (e: Exception) {
                false
            }
            onComplete?.invoke(success)
        }
    }

    suspend fun getVaultSyncQrBitmap(familyId: String? = null, sizePx: Int = 512): Bitmap? {
        val fId = familyId ?: _activeFamilyId.value ?: getOrCreateFamilyIdSync()
        return familyLedgerRepository.generateVaultQrBitmap(fId, sizePx)
    }

    fun handleScannedVaultQr(qrText: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val trimmed = qrText.trim()
            val parsed = familyLedgerRepository.parseScannedVaultQr(trimmed)
            if (parsed != null && isValidUuid(parsed.familyId)) {
                val directResult = familyLedgerRepository.joinFamilyVaultDirect(
                    familyId = parsed.familyId,
                    familyName = parsed.familyName,
                    inviteCode = parsed.inviteCode,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName
                )
                if (directResult.isSuccess) {
                    val vault = directResult.getOrThrow()
                    setActiveFamily(vault.familyId)
                    setFinanceScope(FinanceScope.FAMILY)
                    onResult(true, "Successfully linked and synchronized with ${vault.familyName}!")
                    return@launch
                }
            }

            val codeToJoin = parsed?.inviteCode?.takeIf { it.isNotBlank() } ?: trimmed
            joinFamily(codeToJoin, onResult)
        }
    }

    fun exportVaultSyncFile(context: Context, familyId: String? = null, onResult: (Intent?) -> Unit) {
        viewModelScope.launch {
            val fId = familyId ?: _activeFamilyId.value ?: getOrCreateFamilyIdSync()
            val intent = familyLedgerRepository.exportVaultFileIntent(context, fId)
            onResult(intent)
        }
    }

    fun importVaultSyncFile(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = familyLedgerRepository.importVaultSyncPayload(jsonString, currentUserId, currentUserName)
            if (result.isSuccess) {
                val summary = result.getOrThrow()
                setActiveFamily(summary.familyId)
                setFinanceScope(FinanceScope.FAMILY)
                onResult(
                    true,
                    "Imported '${summary.familyName}' (${summary.transactionsImported} transactions, ${summary.membersImported} members)."
                )
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to import vault file.")
            }
        }
    }

    fun openVoiceDialog() {
        _voiceDialogShowing.value = true
        _parsedVoice.value = null
        _voiceChatMessages.value = emptyList()
    }

    fun closeVoiceDialog() {
        _voiceDialogShowing.value = false
        _voiceProcessing.value = false
        _parsedVoice.value = null
    }

    fun processVoicePrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isBlank()) return
        _voiceProcessing.value = true
        viewModelScope.launch {
            val parsed = GeminiAiService.parseVoiceCommand(trimmed)
            _parsedVoice.value = parsed
            _voiceProcessing.value = false
        }
    }

    fun processAudioPrompt(audioBase64: String) {
        viewModelScope.launch {
            _voiceProcessing.value = true
            val parsed = GeminiAiService.parseAudioCommand(audioBase64)
            _parsedVoice.value = parsed
            _voiceProcessing.value = false
        }
    }

    fun confirmVoiceExpense() {
        val parsed = _parsedVoice.value ?: return
        addTransaction(
            title = parsed.title,
            amount = parsed.amount,
            type = parsed.type,
            category = parsed.category,
            paymentMethod = parsed.paymentMethod,
            note = parsed.note,
            scope = parsed.scope
        )
        closeVoiceDialog()
    }

    fun confirmVoiceExpenseWithEdits(
        title: String,
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        category: String,
        paymentMethod: String,
        scope: FinanceScope = FinanceScope.PERSONAL
    ) {
        val parsed = _parsedVoice.value
        addTransaction(
            title = title.ifBlank { parsed?.title ?: (if (type == TransactionType.INCOME) "Income" else "Expense") },
            amount = if (amount > 0) amount else (parsed?.amount ?: 0.0),
            type = type,
            category = category.ifBlank { parsed?.category ?: (if (type == TransactionType.INCOME) "Salary & Income" else "Food & Dining") },
            paymentMethod = paymentMethod.ifBlank { parsed?.paymentMethod ?: "UPI" },
            note = parsed?.note ?: "Voice Entry",
            scope = scope
        )
        closeVoiceDialog()
    }

    fun confirmVoiceAssistantTransaction(
        messageId: String,
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMethod: String
    ) {
        confirmVoiceExpenseWithEdits(title, amount, type, category, paymentMethod)
    }

    fun clearVoiceAssistantHistory() {
        _voiceChatMessages.value = emptyList()
        _parsedVoice.value = null
        _latestAssistantResponse.value = null
    }

    fun openUpiDialog() {
        _upiDialogShowing.value = true
    }

    fun closeUpiDialog() {
        _upiDialogShowing.value = false
    }

    fun openUpiScanDialog() {
        _upiScanDialogShowing.value = true
    }

    fun closeUpiScanDialog() {
        _upiScanDialogShowing.value = false
    }

    fun openReceiptDialog() {
        _receiptDialogShowing.value = true
        _parsedReceipt.value = null
    }

    fun closeReceiptDialog() {
        _receiptDialogShowing.value = false
        _receiptProcessing.value = false
        _parsedReceipt.value = null
    }

    fun processReceiptText(sampleReceiptText: String) {
        viewModelScope.launch {
            _receiptProcessing.value = true
            val parsed = GeminiAiService.parseReceiptOcr(sampleReceiptText)
            _parsedReceipt.value = parsed
            _receiptProcessing.value = false
        }
    }

    fun getReceiptForTransaction(transactionId: Long): Flow<ReceiptEntity?> = repository.getReceiptForTransaction(transactionId)
    fun getItemsForTransaction(transactionId: Long): Flow<List<ReceiptItemEntity>> = repository.getItemsForTransaction(transactionId)

    fun saveReceiptExpense(
        merchant: String,
        amount: Double,
        category: String,
        paymentMethod: String,
        dateStr: String,
        timeStr: String?,
        receiptNumber: String?,
        subtotal: Double,
        discount: Double,
        tax: Double,
        items: List<ReceiptItemEntity>,
        imageUri: String?,
        rawText: String?
    ) {
        viewModelScope.launch {
            val isFamily = _currentFinanceScope.value == FinanceScope.FAMILY
            val fId = if (isFamily) getOrCreateFamilyIdSync() else null
            val iconName = when (category) {
                "Food & Dining" -> "Restaurant"
                "Shopping" -> "ShoppingBag"
                "Housing & Rent" -> "Home"
                "Transportation" -> "DirectionsCar"
                "Bills & Utilities" -> "Receipt"
                "Entertainment" -> "Movie"
                "Healthcare", "Health" -> "MedicalServices"
                "Salary & Income", "Income" -> "Payments"
                else -> "Category"
            }
            val receiptNote = if (items.isNotEmpty()) "${items.size} item${if (items.size > 1) "s" else ""}: " + items.take(2).joinToString { it.name } else "Receipt Scan"

            if (isFamily && fId != null) {
                familyLedgerRepository.createTransaction(
                    familyId = fId,
                    title = merchant.ifBlank { "Receipt Purchase" },
                    description = receiptNote,
                    amount = amount,
                    category = category,
                    type = TransactionType.EXPENSE,
                    paymentMethod = paymentMethod,
                    paidByMemberId = currentUserId,
                    paidByName = currentUserName,
                    dateMillis = System.currentTimeMillis(),
                    currentUserId = currentUserId
                )
            }

            val tx = TransactionEntity(
                title = merchant.ifBlank { "Receipt Purchase" },
                amount = amount,
                type = TransactionType.EXPENSE,
                category = category,
                categoryIconName = iconName,
                paymentMethod = paymentMethod,
                note = receiptNote,
                receiptImageUri = imageUri,
                financeScope = if (isFamily) FinanceScope.FAMILY else FinanceScope.PERSONAL,
                familyId = if (isFamily) fId else null,
                createdByUserId = if (isFamily) currentUserId else null,
                dateMillis = System.currentTimeMillis()
            )
            val txId = repository.addTransaction(tx)
            val receipt = ReceiptEntity(
                transactionId = txId,
                merchantName = merchant.ifBlank { "Receipt Purchase" },
                receiptNumber = receiptNumber,
                receiptDate = dateStr,
                receiptTime = timeStr,
                subtotal = subtotal,
                discount = discount,
                tax = tax,
                total = amount,
                currency = _currencySymbol.value,
                paymentMethod = paymentMethod,
                imageUri = imageUri,
                rawText = rawText
            )
            val mappedItems = items.map { it.copy(transactionId = txId) }
            repository.saveReceiptWithItems(receipt, mappedItems)
            closeReceiptDialog()
        }
    }

    fun updateTransactionAndReceipt(
        transaction: TransactionEntity,
        receipt: ReceiptEntity?,
        items: List<ReceiptItemEntity>
    ) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            if (receipt != null) {
                repository.updateReceiptWithItems(receipt, items)
            }
        }
    }

    fun deleteTransactionWithReceipt(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun confirmReceiptExpense() {
        val parsed = _parsedReceipt.value ?: return
        val itemsList = parsed.items.map { 
            ReceiptItemEntity(
                transactionId = 0,
                name = it.name,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                totalPrice = it.totalPrice
            )
        }
        saveReceiptExpense(
            merchant = parsed.merchantName,
            amount = parsed.totalAmount,
            category = parsed.category,
            paymentMethod = parsed.paymentMethod,
            dateStr = parsed.dateString,
            timeStr = parsed.timeString,
            receiptNumber = parsed.receiptNumber,
            subtotal = parsed.subtotal,
            discount = parsed.discount,
            tax = parsed.tax,
            items = itemsList,
            imageUri = null,
            rawText = parsed.rawText
        )
    }

    fun generateAiCoachAdvice(totalIncome: Double, totalExpense: Double, topCat: String) {
        viewModelScope.launch {
            _aiCoachLoading.value = true
            val advice = GeminiAiService.getFinancialCoachAdvice(totalIncome, totalExpense, topCat)
            _aiCoachAdvice.value = advice
            _aiCoachLoading.value = false
        }
    }

    fun addScannedItem(barcodeValue: String, productName: String) {
        viewModelScope.launch {
            repository.addScannedItem(
                ScannedItemEntity(
                    barcodeValue = barcodeValue,
                    productName = productName
                )
            )
        }
    }

    fun deleteScannedItem(item: ScannedItemEntity) {
        viewModelScope.launch {
            repository.deleteScannedItem(item)
        }
    }

    fun openScannedBarcodeSheet(barcodeValue: String) {
        _scannedBarcodeValue.value = barcodeValue
        _isScannedBarcodeSheetShowing.value = true
    }

    fun closeScannedBarcodeSheet() {
        _isScannedBarcodeSheetShowing.value = false
        _scannedBarcodeValue.value = null
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            val txs = repository.allTransactions.first()
            txs.forEach { repository.deleteTransaction(it) }
        }
    }

    fun exportTransactionsCsv(): String {
        val txs = uiState.value.transactions
        val sb = StringBuilder("Date,Title,Category,Type,Amount,PaymentMethod,Note\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        txs.forEach { tx ->
            val dateStr = sdf.format(Date(tx.dateMillis))
            sb.append("\"$dateStr\",\"${tx.title}\",\"${tx.category}\",\"${tx.type.name}\",${tx.amount},\"${tx.paymentMethod}\",\"${tx.note}\"\n")
        }
        return sb.toString()
    }
}

private data class BaseData(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val savingsGoals: List<SavingsGoalEntity>,
    val scannedItems: List<ScannedItemEntity>
)

private data class FilterState(
    val search: String,
    val type: TransactionType?,
    val category: String?,
    val member: String?,
    val currency: String,
    val tab: Int
)

private data class VoiceState(
    val voiceShow: Boolean,
    val voiceProc: Boolean,
    val voiceParsed: ParsedVoiceExpense?,
    val voiceMessages: List<VoiceChatMessage>,
    val latestAssistantResponse: VoiceAssistantResponse?,
    val upiShow: Boolean,
    val upiScanShow: Boolean,
    val detectedPayment: com.example.data.upi.ExtractedUpiPayment? = null
)

private data class AiState(
    val receiptShow: Boolean,
    val receiptProc: Boolean,
    val receiptParsed: ParsedReceipt?,
    val coachAdvice: String?,
    val coachLoading: Boolean
)

private data class ScannedItemState(
    val barcodeValue: String?,
    val sheetShowing: Boolean
)
