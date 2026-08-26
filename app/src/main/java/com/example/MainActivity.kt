package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.models.FinanceScope
import com.example.ui.components.AddTransactionDialog
import com.example.ui.components.FamilyMembersDialog
import com.example.ui.components.ReceiptScanModal
import com.example.ui.components.UPIPaySheet
import com.example.ui.components.UpiQrScanModal
import com.example.ui.components.VoiceAiModal
import com.example.ui.components.ZenithFloatingNavigationBar
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowViewModel

enum class ZenithNavScreen {
    SPLASH,
    AUTH,
    MAIN
}

class MainActivity : ComponentActivity() {

    private val viewModel: CashFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CashFlowTheme {
                val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
                var currentScreen by remember(isAuthenticated) {
                    mutableStateOf(if (isAuthenticated) ZenithNavScreen.MAIN else ZenithNavScreen.SPLASH)
                }
                var initialAuthIsSignUp by remember { mutableStateOf(false) }

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 6 })
                            .togetherWith(fadeOut(animationSpec = tween(250)))
                    },
                    label = "zenith_root_screen_transition"
                ) { screen ->
                    when (screen) {
                        ZenithNavScreen.SPLASH -> {
                            SplashScreen(
                                onNavigateToLogin = {
                                    initialAuthIsSignUp = false
                                    currentScreen = ZenithNavScreen.AUTH
                                },
                                onNavigateToRegister = {
                                    initialAuthIsSignUp = true
                                    currentScreen = ZenithNavScreen.AUTH
                                },
                                onContinueAsGuest = {
                                    viewModel.continueAsGuest()
                                    currentScreen = ZenithNavScreen.MAIN
                                }
                            )
                        }
                        ZenithNavScreen.AUTH -> {
                            AuthScreen(
                                viewModel = viewModel,
                                initialIsSignUp = initialAuthIsSignUp,
                                onAuthSuccess = {
                                    currentScreen = ZenithNavScreen.MAIN
                                },
                                onBackToSplash = {
                                    currentScreen = ZenithNavScreen.SPLASH
                                },
                                onContinueAsGuest = {
                                    viewModel.continueAsGuest()
                                    currentScreen = ZenithNavScreen.MAIN
                                }
                            )
                        }
                        ZenithNavScreen.MAIN -> {
                            CashFlowMainApp(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

enum class NavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Default.Home, Icons.Outlined.Home, "nav_home"),
    TRANSACTIONS("Activity", Icons.Default.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_transactions"),
    BUDGETS("Budgets", Icons.Default.PieChart, Icons.Outlined.PieChart, "nav_budgets"),
    ANALYTICS("Analytics", Icons.Default.BarChart, Icons.Outlined.BarChart, "nav_analytics"),
    PROFILE("Profile", Icons.Default.Person, Icons.Outlined.Person, "nav_profile")
}

@Composable
fun CashFlowMainApp(viewModel: CashFlowViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentFinanceScope by viewModel.currentFinanceScope.collectAsStateWithLifecycle()
    val activeFamilyId by viewModel.activeFamilyId.collectAsStateWithLifecycle()
    val activeFamily by viewModel.activeFamily.collectAsStateWithLifecycle(initialValue = null)
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showFamilyMembersDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val totalIncome = remember(uiState.transactions) { viewModel.getTotalIncome(uiState.transactions) }
    val totalExpense = remember(uiState.transactions) { viewModel.getTotalExpense(uiState.transactions) }
    val netBalance = remember(uiState.transactions) { viewModel.getNetBalance(uiState.transactions) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = AmbientBackgroundBrush),
        containerColor = Color.Transparent,
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 42.dp)
                    .testTag("fab_add_transaction"),
                shape = RoundedCornerShape(18.dp),
                containerColor = if (currentFinanceScope == FinanceScope.FAMILY) GoldAccent else EmeraldDarkPrimary,
                contentColor = if (currentFinanceScope == FinanceScope.FAMILY) Color.Black else Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        bottomBar = {
            ZenithFloatingNavigationBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = { index -> viewModel.setTab(index) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = AmbientBackgroundBrush)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(250)) { width -> width } + fadeIn(animationSpec = tween(250))).togetherWith(slideOutHorizontally(animationSpec = tween(250)) { width -> -width } + fadeOut(animationSpec = tween(250)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(250)) { width -> -width } + fadeIn(animationSpec = tween(250))).togetherWith(slideOutHorizontally(animationSpec = tween(250)) { width -> width } + fadeOut(animationSpec = tween(250)))
                    }
                },
                label = "tab_transition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> HomeScreen(
                        familyMembers = familyMembers,
                        state = uiState,
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        netBalance = netBalance,
                        currentFinanceScope = currentFinanceScope,
                        onScopeChange = { scope ->
                            viewModel.setFinanceScope(scope)
                        },
                        onOpenAddTransaction = { showAddDialog = true },
                        onOpenVoiceAssistant = { viewModel.openVoiceDialog() },
                        onOpenReceiptScanner = { viewModel.openReceiptDialog() },
                        onOpenUpiPay = { viewModel.openUpiDialog() },
                        onOpenUpiScan = { viewModel.openUpiScanDialog() },
                        onDeleteTransaction = { tx -> viewModel.deleteTransactionWithReceipt(tx) },
                        onUpdateTransaction = { tx -> viewModel.updateTransaction(tx) },
                        onManageFamilyMembers = { showFamilyMembersDialog = true },
                        onNavigateToActivity = { viewModel.setTab(1) },
                        onNavigateToProfile = { viewModel.setTab(4) }
                    )
                    1 -> TransactionsScreen(
                        state = uiState,
                        onSearchQueryChange = { query -> viewModel.setSearchQuery(query) },
                        onFilterTypeChange = { type -> viewModel.setFilterType(type) },
                        onFilterCategoryChange = { cat -> viewModel.setFilterCategory(cat) },
                        onFilterMemberChange = { member -> viewModel.setFilterMember(member) },
                        onDeleteTransaction = { tx -> viewModel.deleteTransactionWithReceipt(tx) },
                        onUpdateTransaction = { tx -> viewModel.updateTransaction(tx) },
                        onOpenAddTransaction = { showAddDialog = true },
                        onOpenVoiceAssistant = { viewModel.openVoiceDialog() },
                        familyMembers = familyMembers
                    )
                    2 -> BudgetsAndGoalsScreen(
                        state = uiState,
                        onSaveBudget = { cat, limit, periodType, customName, budgetId ->
                            viewModel.saveBudget(
                                categoryName = cat,
                                limit = limit,
                                periodType = periodType,
                                customPeriodName = customName,
                                budgetId = budgetId
                            )
                        },
                        onDeleteBudget = { budget -> viewModel.deleteBudget(budget) },
                        onSaveSavingsGoal = { goal ->
                            viewModel.saveSavingsGoalEntity(goal)
                        },
                        onDeleteSavingsGoal = { goal -> viewModel.deleteSavingsGoal(goal) },
                        onDepositToGoal = { goal, amount -> viewModel.updateGoalDeposit(goal, amount) }
                    )
                    3 -> AnalyticsScreen(
                        state = uiState,
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        onGenerateAiAdvice = { inc, exp, cat -> viewModel.generateAiCoachAdvice(inc, exp, cat) },
                        currentFinanceScope = currentFinanceScope,
                        familyMembers = familyMembers
                    )
                    4 -> ProfileScreen(
                        state = uiState,
                        viewModel = viewModel,
                        onCurrencySelect = { curr -> viewModel.setCurrency(curr) },
                        onDeleteScannedItem = { item -> viewModel.deleteScannedItem(item) },
                        onNavigateToActivity = { viewModel.setTab(1) }
                    )
                }
            }
        }

        if (showFamilyMembersDialog && currentFinanceScope == com.example.data.models.FinanceScope.FAMILY) {
            FamilyMembersDialog(
                familyMembers = familyMembers,
                familyName = activeFamily?.name ?: "Family Vault",
                familyId = activeFamily?.inviteCode?.takeIf { it.isNotBlank() } ?: activeFamily?.id ?: activeFamilyId ?: "",
                onDismiss = { showFamilyMembersDialog = false },
                onAddMember = { name, role ->
                    viewModel.addFamilyMember(name, role)
                },
                onJoinFamily = { code ->
                    viewModel.joinFamily(code) { success, msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onSyncNow = {
                    viewModel.syncFamilyLedgerNow { success ->
                        android.widget.Toast.makeText(
                            context,
                            if (success) "Family ledger synchronized!" else "Sync complete (offline mode).",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        if (showAddDialog) {
            AddTransactionDialog(
                categories = uiState.categories,
                familyMembers = familyMembers,
                currencySymbol = uiState.currencySymbol,
                currentFinanceScope = currentFinanceScope,
                currentUserId = viewModel.currentUserId,
                currentUserName = viewModel.currentUserName,
                familyName = activeFamily?.name ?: "Family Vault",
                onDismiss = { showAddDialog = false },
                onAdd = { title, amount, type, category, dateMillis, note, paymentMethod, memberId, scope ->
                    viewModel.addTransaction(title, amount, type, category, paymentMethod, note, memberId, scope, dateMillis)
                    showAddDialog = false
                }
            )
        }

        if (uiState.isUpiScanDialogShowing) {
            UpiQrScanModal(
                currencySymbol = uiState.currencySymbol,
                currentFinanceScope = currentFinanceScope,
                currentUserName = viewModel.currentUserName,
                familyName = activeFamily?.name ?: "Family Vault",
                familyMembers = familyMembers,
                onDismiss = { viewModel.closeUpiScanDialog() },
                onSaveTransaction = { title, amount, category, scope, memberId, upiId, upiTransactionId ->
                    viewModel.addUpiTransaction(
                        title = title,
                        amount = amount,
                        category = category,
                        scope = scope,
                        memberId = memberId,
                        upiId = upiId,
                        upiTransactionId = upiTransactionId
                    )
                    viewModel.closeUpiScanDialog()
                }
            )
        }

        if (uiState.isUpiDialogShowing) {
            UPIPaySheet(
                currencySymbol = uiState.currencySymbol,
                currentFinanceScope = currentFinanceScope,
                currentUserId = viewModel.currentUserId,
                currentUserName = viewModel.currentUserName,
                familyName = activeFamily?.name ?: "Family Vault",
                familyMembers = familyMembers,
                onDismiss = { viewModel.closeUpiDialog() },
                onSaveTransaction = { title, amount, category, scope, memberId, upiId, upiTransactionId ->
                    viewModel.addUpiTransaction(
                        title = title,
                        amount = amount,
                        category = category,
                        scope = scope,
                        memberId = memberId,
                        upiId = upiId,
                        upiTransactionId = upiTransactionId
                    )
                    viewModel.closeUpiDialog()
                }
            )
        }

        if (uiState.isVoiceDialogShowing) {
            VoiceAiModal(                isProcessing = uiState.isVoiceProcessing,
                parsedExpense = uiState.parsedVoiceExpense,
                currencySymbol = uiState.currencySymbol,
                onDismiss = { viewModel.closeVoiceDialog() },
                onProcessPrompt = { prompt -> viewModel.processVoicePrompt(prompt) },
                onProcessAudio = { audioBase64 -> viewModel.processAudioPrompt(audioBase64) },
                onConfirmSave = { title, amount, category, paymentMethod ->
                    viewModel.confirmVoiceExpenseWithEdits(title, amount, category, paymentMethod)
                },
                onOpenManualAdd = {
                    viewModel.closeVoiceDialog()
                    showAddDialog = true
                }
            )
        }

        if (uiState.isReceiptDialogShowing) {
            ReceiptScanModal(
                isProcessing = uiState.isReceiptProcessing,
                parsedReceipt = uiState.parsedReceipt,
                currencySymbol = uiState.currencySymbol,
                onDismiss = { viewModel.closeReceiptDialog() },
                onProcessReceipt = { text -> viewModel.processReceiptText(text) },
                onConfirmSave = { viewModel.confirmReceiptExpense() },
                onSaveReceiptDetails = { merchant, amount, category, paymentMethod, dateStr, timeStr, receiptNumber, subtotal, discount, tax, items, imageUri, rawText ->
                    viewModel.saveReceiptExpense(
                        merchant, amount, category, paymentMethod, dateStr, timeStr, receiptNumber, subtotal, discount, tax, items, imageUri, rawText
                    )
                },
                onBarcodeScanned = { barcode -> viewModel.openScannedBarcodeSheet(barcode) },
                onOpenManualAdd = {
                    viewModel.closeReceiptDialog()
                    showAddDialog = true
                }
            )
        }
    }
}
