package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiAiService
import com.example.data.ai.ParsedReceipt
import com.example.data.ai.ParsedVoiceExpense
import com.example.data.database.CashFlowDatabase
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
    val isNotificationEnabled: Boolean = true
)

class CashFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CashFlowRepository

    private val _searchQuery = MutableStateFlow("")
    private val _filterType = MutableStateFlow<TransactionType?>(null)
    private val _filterCategory = MutableStateFlow<String?>(null)
    private val _filterMember = MutableStateFlow<String?>(null)
    private val _currencySymbol = MutableStateFlow("₹")
    private val _selectedTab = MutableStateFlow(0)

    private val _voiceDialogShowing = MutableStateFlow(false)
    private val _voiceProcessing = MutableStateFlow(false)
    private val _parsedVoice = MutableStateFlow<ParsedVoiceExpense?>(null)

    private val _upiDialogShowing = MutableStateFlow(false)
    private val _upiScanDialogShowing = MutableStateFlow(false)

    private val _receiptDialogShowing = MutableStateFlow(false)
    private val _receiptProcessing = MutableStateFlow(false)
    private val _parsedReceipt = MutableStateFlow<ParsedReceipt?>(null)

    private val _aiCoachAdvice = MutableStateFlow<String?>(null)
    private val _aiCoachLoading = MutableStateFlow(false)

    private val _scannedBarcodeValue = MutableStateFlow<String?>(null)
    private val _isScannedBarcodeSheetShowing = MutableStateFlow(false)

    private val syncEngine: com.example.data.network.SyncEngine
    private val userProfileRepository: com.example.data.repository.UserProfileRepository
    val authService = com.example.data.network.SupabaseAuthService()

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
                    userProfileRepository.syncProfile(user.id)
                }
            }
        }

        // Automatic background cloud synchronization loop (Immediate + Periodic every 12s)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncEngine.syncAll(currentUserId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            while (isActive) {
                delay(12000)
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    // background sync tick
                }
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

    val currentUserId: String
        get() = authService.currentUser.value?.id ?: _activeUserEmail.value ?: "local_user_1"
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
        if (id != null) flow { emit(repository.getFamilyById(id)) }
        else flow { emit(repository.getFirstFamily()) }
    }
    
    val userFamilies: Flow<List<FamilyEntity>> = repository.getAllFamilies()
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val familyMembers: Flow<List<FamilyMemberEntity>> = _activeFamilyId.flatMapLatest { id ->
        if (id != null) {
            repository.getMembersByFamilyId(id)
        } else {
            repository.getAllFamilies().flatMapLatest { families ->
                val firstFam = families.firstOrNull()
                if (firstFam != null) {
                    repository.getMembersByFamilyId(firstFam.id)
                } else {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }
        }
    }

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
        _voiceDialogShowing,
        _voiceProcessing,
        _parsedVoice,
        _upiDialogShowing,
        _upiScanDialogShowing
    ) { voiceShow, voiceProc, voiceParsed, upiShow, upiScanShow ->
        VoiceState(voiceShow, voiceProc, voiceParsed, upiShow, upiScanShow)
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
            isUpiDialogShowing = voice.upiShow,
            isUpiScanDialogShowing = voice.upiScanShow,
            isReceiptDialogShowing = ai.receiptShow,
            isReceiptProcessing = ai.receiptProc,
            parsedReceipt = ai.receiptParsed,
            aiCoachAdvice = ai.coachAdvice,
            isAiCoachLoading = ai.coachLoading,
            selectedTab = filter.tab,
            scannedBarcodeValue = scannedItem.barcodeValue,
            isScannedBarcodeSheetShowing = scannedItem.sheetShowing
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


    fun setAuthenticatedUser(email: String, name: String, isGuest: Boolean = false) {
        prefs.edit()
            .putBoolean("is_authenticated", true)
            .putBoolean("is_guest_mode", isGuest)
            .putString("user_email", email)
            .putString("user_name", name)
            .apply()
        _isAuthenticated.value = true
        _isGuestMode.value = isGuest
        _activeUserEmail.value = email
        _activeUserName.value = name
    }

    fun signIn(email: String, pass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = authService.signIn(email, pass)
            if (success) {
                val name = authService.currentUser.value?.email?.substringBefore("@") ?: email.substringBefore("@")
                setAuthenticatedUser(email, name, isGuest = false)
            } else {
                val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                setAuthenticatedUser(email, name, isGuest = false)
            }
            onResult(true)
        }
    }

    fun signUp(email: String, pass: String, name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = authService.signUp(email, pass, name)
            setAuthenticatedUser(email, name, isGuest = false)
            onResult(true)
        }
    }

    fun continueAsGuest() {
        setAuthenticatedUser("guest@zenith.vault", "Guest Explorer", isGuest = true)
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            prefs.edit()
                .putBoolean("is_authenticated", false)
                .putBoolean("is_guest_mode", false)
                .remove("user_email")
                .remove("user_name")
                .apply()
            _isAuthenticated.value = false
            _isGuestMode.value = false
            _activeUserEmail.value = null
            _activeUserName.value = null
        }
    }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            syncEngine.syncAll(currentUserId)
        }
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

    suspend fun getOrCreateFamilyIdSync(): String {
        val current = _activeFamilyId.value
        if (current != null) return current

        val existing = repository.getFirstFamily()
        if (existing != null) {
            _activeFamilyId.value = existing.id
            prefs.edit().putString("active_family_id", existing.id).apply()
            return existing.id
        }

        val newFamilyId = "FAM-" + UUID.randomUUID().toString().take(6).uppercase()
        val family = FamilyEntity(
            id = newFamilyId,
            name = "${currentUserName.ifBlank { "Zenith" }} Family Vault",
            createdByUserId = currentUserId,
            createdAt = System.currentTimeMillis()
        )
        repository.insertFamily(family)

        val member = FamilyMemberEntity(
            id = UUID.randomUUID().toString(),
            familyId = newFamilyId,
            userId = currentUserId,
            name = currentUserName.ifBlank { "You" },
            role = FamilyRole.ADMIN,
            joinedAt = System.currentTimeMillis()
        )
        repository.insertMember(member)

        _activeFamilyId.value = newFamilyId
        prefs.edit().putString("active_family_id", newFamilyId).apply()
        return newFamilyId
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
            val newFamilyId = UUID.randomUUID().toString()
            val inviteCode = "FAM-" + UUID.randomUUID().toString().take(6).uppercase()
            val family = FamilyEntity(
                id = newFamilyId,
                name = name,
                createdByUserId = currentUserId,
                createdAt = System.currentTimeMillis(),
                inviteCode = inviteCode,
                syncStatus = "PENDING_CREATE"
            )
            repository.insertFamily(family)
            
            val member = FamilyMemberEntity(
                id = UUID.randomUUID().toString(),
                familyId = newFamilyId,
                userId = currentUserId,
                name = currentUserName.ifBlank { "You" },
                role = FamilyRole.ADMIN,
                joinedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_CREATE"
            )
            repository.insertMember(member)
            
            setActiveFamily(newFamilyId)
            setFinanceScope(FinanceScope.FAMILY)

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addFamilyMember(name: String, role: FamilyRole) {
        viewModelScope.launch {
            val fId = getOrCreateFamilyIdSync()
            val member = FamilyMemberEntity(
                id = UUID.randomUUID().toString(),
                familyId = fId,
                userId = UUID.randomUUID().toString(),
                name = name,
                role = role,
                joinedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_CREATE"
            )
            repository.insertMember(member)

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteFamilyMember(member: FamilyMemberEntity) {
        viewModelScope.launch {
            val toDelete = member.copy(syncStatus = "PENDING_DELETE", isDeleted = true)
            repository.updateMember(toDelete)

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun joinFamily(inviteCode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val cleanCode = inviteCode.trim().uppercase()
            if (cleanCode.isBlank()) {
                onResult(false, "Please enter a valid Family ID")
                return@launch
            }
            if (!com.example.data.network.SupabaseClientConfig.isConfigured) {
                onResult(false, "Cloud sync is not configured. Please add Supabase credentials in settings.")
                return@launch
            }
            var family = repository.getFamilyById(cleanCode)

            // 1. Fetch remote family and transactions from Supabase
            val (remoteFamily, remoteTxs) = withContext(Dispatchers.IO) {
                syncEngine.fetchRemoteFamilyByInviteCode(cleanCode)
            }

            if (remoteFamily != null) {
                val famEntity = FamilyEntity(
                    id = remoteFamily.id,
                    name = remoteFamily.name,
                    createdByUserId = remoteFamily.createdBy,
                    createdAt = System.currentTimeMillis(),
                    inviteCode = remoteFamily.inviteCode ?: cleanCode,
                    serverId = remoteFamily.id,
                    syncStatus = "SYNCED"
                )
                repository.insertFamily(famEntity)
                family = famEntity

                val member = FamilyMemberEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = remoteFamily.id,
                    userId = currentUserId,
                    name = currentUserName,
                    role = FamilyRole.MEMBER,
                    joinedAt = System.currentTimeMillis()
                )
                repository.insertMember(member)

                // Insert all downloaded family transactions
                withContext(Dispatchers.IO) {
                    for (tx in remoteTxs) {
                        val existing = repository.getTransactionByServerId(tx.id)
                        if (existing == null && !tx.isDeleted) {
                            val txDateMillis = try {
                                java.time.Instant.parse(tx.transactionDate).toEpochMilli()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }
                            repository.insertTransaction(
                                TransactionEntity(
                                    title = tx.description,
                                    amount = tx.amount,
                                    type = try { TransactionType.valueOf(tx.transactionType) } catch (e: Exception) { TransactionType.EXPENSE },
                                    category = "General",
                                    paymentMethod = tx.paymentMethod,
                                    dateMillis = txDateMillis,
                                    upiId = tx.upiId,
                                    upiTransactionId = tx.upiTransactionId,
                                    financeScope = FinanceScope.FAMILY,
                                    familyId = remoteFamily.id,
                                    createdByUserId = tx.userId,
                                    serverId = tx.id,
                                    syncStatus = "SYNCED"
                                )
                            )
                        }
                    }
                }
            } else if (family == null) {
                val newConnectedFamily = FamilyEntity(
                    id = cleanCode,
                    name = "Family Vault ($cleanCode)",
                    createdByUserId = "family_owner",
                    createdAt = System.currentTimeMillis()
                )
                repository.insertFamily(newConnectedFamily)
                family = newConnectedFamily
            }
            val targetFamilyId = family.id
            val existing = repository.getMemberByFamilyAndUser(targetFamilyId, currentUserId)
            if (existing == null) {
                val member = FamilyMemberEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = targetFamilyId,
                    userId = currentUserId,
                    name = currentUserName.ifBlank { "Family Member" },
                    role = FamilyRole.MEMBER,
                    joinedAt = System.currentTimeMillis()
                )
                repository.insertMember(member)
            }
            setActiveFamily(targetFamilyId)
            setFinanceScope(FinanceScope.FAMILY)
            // Trigger background synchronization immediately
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncEngine.syncAll(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onResult(true, "Successfully linked and synchronized with ${family.name}!")
        }
    }

    fun syncFamilyLedgerNow(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = try {
                withContext(Dispatchers.IO) {
                    syncEngine.syncAll(currentUserId)
                }
            } catch (e: Exception) {
                false
            }
            onComplete?.invoke(success)
        }
    }

    fun openVoiceDialog() {
        _voiceDialogShowing.value = true
        _parsedVoice.value = null
    }

    fun closeVoiceDialog() {
        _voiceDialogShowing.value = false
        _voiceProcessing.value = false
        _parsedVoice.value = null
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

    fun processVoicePrompt(promptText: String) {
        viewModelScope.launch {
            _voiceProcessing.value = true
            val parsed = GeminiAiService.parseVoiceCommand(promptText)
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
            note = parsed.note
        )
        closeVoiceDialog()
    }

    fun confirmVoiceExpenseWithEdits(title: String, amount: Double, category: String, paymentMethod: String) {
        val parsed = _parsedVoice.value
        val noteText = if (!parsed?.item.isNullOrBlank() && parsed?.quantity != null) {
            val qStr = if (parsed.quantity % 1.0 == 0.0) "${parsed.quantity.toInt()}" else "${parsed.quantity}"
            "Item: ${parsed.item} (Qty: $qStr ${parsed.unit ?: "pcs"}) • ${parsed.note}"
        } else {
            parsed?.note ?: "Voice Entry"
        }
        addTransaction(
            title = title.ifBlank { parsed?.title ?: "Voice Entry" },
            amount = if (amount > 0) amount else (parsed?.amount ?: 0.0),
            type = parsed?.type ?: TransactionType.EXPENSE,
            category = category.ifBlank { parsed?.category ?: "Food & Dining" },
            paymentMethod = paymentMethod.ifBlank { parsed?.paymentMethod ?: "UPI" },
            note = noteText
        )
        closeVoiceDialog()
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
            val isFamily = _currentFinanceScope.value == FinanceScope.FAMILY && _activeFamilyId.value != null
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
            val tx = TransactionEntity(
                title = merchant.ifBlank { "Receipt Purchase" },
                amount = amount,
                type = TransactionType.EXPENSE,
                category = category,
                categoryIconName = iconName,
                paymentMethod = paymentMethod,
                note = if (items.isNotEmpty()) "${items.size} item${if (items.size > 1) "s" else ""}: " + items.take(2).joinToString { it.name } else "Receipt Scan",
                receiptImageUri = imageUri,
                financeScope = if (isFamily) FinanceScope.FAMILY else FinanceScope.PERSONAL,
                familyId = if (isFamily) _activeFamilyId.value else null,
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
            repository.deleteReceiptByTransactionId(transaction.id)
            repository.deleteTransaction(transaction)
            repository.deleteTransactionById(transaction.id)
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
    val upiShow: Boolean,
    val upiScanShow: Boolean
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
