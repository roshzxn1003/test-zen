package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.models.FinanceScope
import com.example.data.upi.UpiNotificationListenerService
import com.example.ui.components.BackupRestoreModal
import com.example.ui.components.CleanCard
import com.example.ui.components.ExportDataModal
import com.example.ui.components.FamilyMembersDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.ImportDataModal
import com.example.ui.components.PaymentMethodDropdown
import com.example.ui.components.ZenithLogo
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowUiState
import com.example.ui.viewmodel.CashFlowViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    state: CashFlowUiState,
    viewModel: CashFlowViewModel,
    onCurrencySelect: (String) -> Unit,
    onDeleteScannedItem: (com.example.data.models.ScannedItemEntity) -> Unit,
    onNavigateToActivity: () -> Unit = {}
) {
    val context = LocalContext.current
    val currencies = listOf("₹", "$", "€", "£", "¥")

    // Reactive State from ViewModel
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
    val activeUserEmail by viewModel.activeUserEmail.collectAsStateWithLifecycle()
    val activeUserName by viewModel.activeUserName.collectAsStateWithLifecycle()
    val activeFamily by viewModel.activeFamily.collectAsStateWithLifecycle(initialValue = null)
    val activeFamilyId by viewModel.activeFamilyId.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle(initialValue = emptyList())
    val defaultPaymentMethod by viewModel.defaultPaymentMethod.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isHapticsOn by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
    val isNotificationsOn by viewModel.isNotificationsEnabled.collectAsStateWithLifecycle()
    val isUpiAutoDetectionEnabled by viewModel.isUpiAutoDetectionEnabled.collectAsStateWithLifecycle()

    val syncState by viewModel.syncUiState.collectAsStateWithLifecycle()

    var showAuthDialog by remember { mutableStateOf(false) }
    var initialAuthModeIsSignUp by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showFamilyDialog by remember { mutableStateOf(false) }
    var showUpiSettingsDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showExportModal by remember { mutableStateOf(false) }
    var showImportModal by remember { mutableStateOf(false) }
    var showBackupModal by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isUpiNotifGranted by remember { mutableStateOf(UpiNotificationListenerService.isPermissionGranted(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isUpiNotifGranted = UpiNotificationListenerService.isPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 108.dp)
    ) {
        // --- 1. USER PROFILE HEADER CARD ---
        item {
            UserProfileGlassHeader(
                userName = activeUserName ?: viewModel.currentUserName,
                userEmail = activeUserEmail,
                isGuest = isGuestMode || !isAuthenticated,
                onEditNameClick = { showEditNameDialog = true },
                onSignInClick = {
                    initialAuthModeIsSignUp = false
                    showAuthDialog = true
                },
                onCreateAccountClick = {
                    initialAuthModeIsSignUp = true
                    showAuthDialog = true
                },
                onSyncClick = {
                    if (isGuestMode || !isAuthenticated) {
                        Toast.makeText(context, "Please sign in or create an account to sync with Zenith Cloud.", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.syncNow { success ->
                            if (success) {
                                Toast.makeText(context, "Cloud sync completed successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Cloud sync failed. Please check your network connection.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onSignOutClick = {
                    viewModel.signOut()
                    Toast.makeText(context, "Signed out of Zenith account.", Toast.LENGTH_SHORT).show()
                },
                isSyncing = syncState is com.example.ui.viewmodel.SyncUiState.Syncing
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        // --- 2. FAMILY VAULT CARD ---
        item {
            ProfileSectionTitle("Family Vault Status")

            val currentFamId = activeFamily?.inviteCode?.takeIf { it.isNotBlank() } ?: activeFamily?.id ?: activeFamilyId ?: ""
            val currentFamName = activeFamily?.name ?: "${activeUserName ?: "Zenith"} Family Vault"

            CleanCard(
                modifier = Modifier.fillMaxWidth().testTag("profile_family_card"),
                backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(24.dp))
                        }

                        Column {
                            Text(
                                text = currentFamName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "${familyMembers.size.coerceAtLeast(1)} connected member${if (familyMembers.size > 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = { showFamilyDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Manage", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (currentFamId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorderColor)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Vault ID: ", fontSize = 12.sp, color = SlateDarkTextSecondary)
                            Text(currentFamId, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFE9D5FF), letterSpacing = 0.5.sp)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Zenith Family ID", currentFamId))
                                Toast.makeText(context, "Family ID ($currentFamId) copied!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFFC4B5FD), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Code", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC4B5FD))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // --- 3. FINANCE PREFERENCES SECTION ---
        item {
            ProfileSectionTitle("Finance Preferences")

            CleanCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
            ) {
                // Currency Selector
                Text(
                    text = "Display Currency",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateDarkTextPrimary
                )
                Text(
                    text = "Applies across all ledgers, budgets & insights",
                    fontSize = 11.sp,
                    color = SlateDarkTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currencies.forEach { curr ->
                        val isSelected = state.currencySymbol == curr
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCurrencySelect(curr) },
                            label = {
                                Text(
                                    text = when (curr) {
                                        "₹" -> "₹ INR"
                                        "$" -> "$ USD"
                                        "€" -> "€ EUR"
                                        "£" -> "£ GBP"
                                        "¥" -> "¥ JPY"
                                        else -> curr
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Default Payment Method Dropdown
                PaymentMethodDropdown(
                    selectedMethod = defaultPaymentMethod,
                    onMethodSelected = { method ->
                        viewModel.setDefaultPaymentMethod(method)
                        Toast.makeText(context, "Default payment method set to $method", Toast.LENGTH_SHORT).show()
                    },
                    label = "Default Payment Method"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // --- 4. APP PREFERENCES SECTION ---
        item {
            ProfileSectionTitle("App Preferences")

            CleanCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
            ) {
                // Dark Mode Switch
                PreferenceSwitchRow(
                    icon = Icons.Default.DarkMode,
                    title = "Dark OLED Theme",
                    subtitle = "High contrast glassmorphic dark interface",
                    checked = isDarkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Haptic Feedback Switch
                PreferenceSwitchRow(
                    icon = Icons.Default.Vibration,
                    title = "Haptic Touch Feedback",
                    subtitle = "Vibrate on voice and quick transaction actions",
                    checked = isHapticsOn,
                    onCheckedChange = { viewModel.setHapticsEnabled(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Notifications Switch
                PreferenceSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = "Budget Alerts & Insights",
                    subtitle = "Notify when approaching monthly spending limits",
                    checked = isNotificationsOn,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Automatic UPI Payment Tracking (Notification Access)
                val isServiceFullyActive = isUpiNotifGranted && isUpiAutoDetectionEnabled
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        !isUpiNotifGranted -> GoalAmber.copy(alpha = 0.1f)
                        !isUpiAutoDetectionEnabled -> SlateDarkSurfaceVariant.copy(alpha = 0.5f)
                        else -> EmeraldDarkPrimary.copy(alpha = 0.1f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            !isUpiNotifGranted -> GoalAmber.copy(alpha = 0.35f)
                            !isUpiAutoDetectionEnabled -> GlassBorderColor
                            else -> EmeraldDarkPrimary.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUpiSettingsDialog = true }
                        .testTag("row_upi_notification_settings")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isUpiNotifGranted -> GoalAmber.copy(alpha = 0.2f)
                                        !isUpiAutoDetectionEnabled -> SlateDarkSurfaceVariant
                                        else -> EmeraldDarkPrimary.copy(alpha = 0.2f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = when {
                                    !isUpiNotifGranted -> GoalAmber
                                    !isUpiAutoDetectionEnabled -> SlateDarkTextSecondary
                                    else -> EmeraldDarkPrimary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Auto-Track UPI Payments",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when {
                                        !isUpiNotifGranted -> GoalAmber.copy(alpha = 0.2f)
                                        !isUpiAutoDetectionEnabled -> SlateDarkSurfaceVariant
                                        else -> IncomeGreen.copy(alpha = 0.2f)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            !isUpiNotifGranted -> "SETUP NEEDED"
                                            !isUpiAutoDetectionEnabled -> "PAUSED"
                                            else -> "ACTIVE"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            !isUpiNotifGranted -> GoalAmber
                                            !isUpiAutoDetectionEnabled -> SlateDarkTextSecondary
                                            else -> IncomeGreen
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    !isUpiNotifGranted -> "Tap to grant Android Notification Access so Zenith can auto-detect receipts."
                                    !isUpiAutoDetectionEnabled -> "Detection is temporarily paused in settings. Tap to configure."
                                    else -> "Active: Intercepts GPay, PhonePe, Paytm, CRED & Bank payment notifications on-device."
                                },
                                fontSize = 11.sp,
                                color = SlateDarkTextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = SlateDarkTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // --- 5. DATA & PRIVACY SECTION ---
        item {
            ProfileSectionTitle("Data & Privacy")

            CleanCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
            ) {
                // Export Data
                PreferenceActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Export Financial Data",
                    subtitle = "Download PDF report, Excel, CSV or JSON",
                    actionLabel = "Export",
                    onClick = { showExportModal = true }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Import Data
                PreferenceActionRow(
                    icon = Icons.Default.FileUpload,
                    title = "Import Transactions",
                    subtitle = "Load CSV, Excel or JSON records into Zenith",
                    actionLabel = "Import",
                    onClick = { showImportModal = true }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Backup & Restore
                PreferenceActionRow(
                    icon = Icons.Default.CloudSync,
                    title = "Backup & Restore",
                    subtitle = "Full encrypted snapshot of budgets & history",
                    actionLabel = "Backup",
                    onClick = { showBackupModal = true }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Clear Local Data
                PreferenceActionRow(
                    icon = Icons.Default.DeleteOutline,
                    title = "Clear Local Transactions",
                    subtitle = "Reset device transaction history",
                    actionLabel = "Clear",
                    accentColor = ExpenseRed,
                    onClick = { showClearDataDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // --- 6. SUPPORT & ABOUT SECTION ---
        item {
            ProfileSectionTitle("Support & About")

            CleanCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
            ) {
                PreferenceActionRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "Help & Voice Guide",
                    subtitle = "How to speak Tanglish & English voice entries",
                    actionLabel = "Guide",
                    onClick = { showHelpDialog = true }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderColor)
                Spacer(modifier = Modifier.height(10.dp))

                PreferenceActionRow(
                    icon = Icons.Default.Info,
                    title = "About Zenith",
                    subtitle = "v2.0.0 • Your money. Your clarity.",
                    actionLabel = "Info",
                    onClick = { showAboutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // --- 7. ACCOUNT ACTIONS SECTION ---
        item {
            ProfileSectionTitle("Account Actions")

            CleanCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
            ) {
                if (isAuthenticated && !isGuestMode) {
                    PreferenceActionRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Sign Out",
                        subtitle = "Sign out of your synced account on this device",
                        actionLabel = "Sign Out",
                        accentColor = GoldAccent,
                        onClick = {
                            viewModel.signOut()
                            Toast.makeText(context, "Signed out of Zenith account.", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = GlassBorderColor)
                    Spacer(modifier = Modifier.height(10.dp))

                    PreferenceActionRow(
                        icon = Icons.Default.DeleteForever,
                        title = "Reset Device Account",
                        subtitle = "Clear account session and local vault records",
                        actionLabel = "Reset",
                        accentColor = ExpenseRed,
                        onClick = {
                            viewModel.signOut()
                            viewModel.clearAllLocalData()
                            Toast.makeText(context, "Account data reset.", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    PreferenceActionRow(
                        icon = Icons.Default.AccountCircle,
                        title = "Sign In / Register",
                        subtitle = "Sync across devices and connect family vault",
                        actionLabel = "Sign In",
                        accentColor = EmeraldDarkPrimary,
                        onClick = {
                            initialAuthModeIsSignUp = false
                            showAuthDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- MODALS & DIALOGS ---

    // Edit Name Dialog
    if (showEditNameDialog) {
        EditNameDialog(
            currentName = activeUserName ?: viewModel.currentUserName,
            onDismiss = { showEditNameDialog = false },
            onSave = { newName ->
                viewModel.updateUserName(newName)
                showEditNameDialog = false
                Toast.makeText(context, "Name updated to $newName", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Family Members Dialog
    if (showFamilyDialog) {
        FamilyMembersDialog(
            familyMembers = familyMembers,
            familyName = activeFamily?.name ?: "Family Vault",
            familyId = activeFamily?.id ?: activeFamilyId ?: "",
            inviteCode = activeFamily?.inviteCode ?: "",
            onDismiss = { showFamilyDialog = false },
            onAddMember = { name, role ->
                viewModel.addFamilyMember(name, role)
            },
            onJoinFamily = { code ->
                viewModel.handleScannedVaultQr(code) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onSyncNow = {
                viewModel.syncFamilyLedgerNow { success ->
                    Toast.makeText(
                        context,
                        if (success) "Family ledger synchronized!" else "Sync complete (offline mode).",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onExportVaultFile = {
                viewModel.exportVaultSyncFile(context) { shareIntent ->
                    if (shareIntent != null) {
                        context.startActivity(shareIntent)
                    } else {
                        Toast.makeText(context, "Could not export vault file.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onImportVaultFile = { jsonPayload ->
                viewModel.importVaultSyncFile(jsonPayload) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // UPI Notification Settings Dialog
    if (showUpiSettingsDialog) {
        UpiNotificationSettingsDialog(
            isPermissionGranted = isUpiNotifGranted,
            isAutoDetectEnabled = isUpiAutoDetectionEnabled,
            onToggleAutoDetect = { enabled ->
                viewModel.setUpiAutoDetectionEnabled(enabled)
                Toast.makeText(context, if (enabled) "UPI auto-detection enabled." else "UPI auto-detection paused.", Toast.LENGTH_SHORT).show()
            },
            onOpenSystemSettings = {
                UpiNotificationListenerService.openPermissionSettings(context)
            },
            onSimulateTestPayment = {
                viewModel.simulateTestUpiPayment()
                Toast.makeText(context, "Simulated payment notification posted! Check modal prompt.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showUpiSettingsDialog = false }
        )
    }

    // Clear Local Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Reset Local Database & Vaults?") },
            text = { Text("This will permanently wipe all local transactions, budgets, goals, and cached family vaults, and re-initialize clean default categories. Ideal when reconnecting to a reset backend.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllLocalData()
                        showClearDataDialog = false
                        Toast.makeText(context, "Local database and family ledger reset successfully.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Reset Database")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ZenithLogo(size = 48.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ZENITH", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = SlateDarkTextPrimary)
                    Text("Your money. Your clarity.", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Zenith is a high-performance offline-first personal & family finance manager powered by Gemini AI, real-time voice expense intelligence, and cloud synchronization.",
                        fontSize = 13.sp,
                        color = SlateDarkTextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showAboutDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Help & Voice Guide Dialog
    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Voice Entry Guide", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                    Text("Speak your expense or income in English", fontSize = 12.sp, color = SlateDarkTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    listOf(
                        "• \"Spent 150 on lunch\"" to "Records ₹150 in Food & Dining",
                        "• \"Paid 500 for petrol via UPI\"" to "Records ₹500 in Transportation",
                        "• \"Received salary 50000 in bank\"" to "Records ₹50,000 Income",
                        "• \"Got 200 cashback on Google Pay\"" to "Records ₹200 Income"
                    ).forEach { (cmd, desc) ->
                        Text(cmd, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
                        Text(desc, fontSize = 11.sp, color = SlateDarkTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showHelpDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Got it", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Auth Dialog
    if (showAuthDialog) {
        AuthDialog(
            isSignUpInitial = initialAuthModeIsSignUp,
            onDismiss = { showAuthDialog = false },
            onSignIn = { email, pass ->
                viewModel.signIn(email, pass) { result ->
                    if (result.success) {
                        showAuthDialog = false
                        Toast.makeText(context, "Welcome back to Zenith!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, result.message ?: "Sign in failed. Please verify credentials.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSignUp = { email, pass, name ->
                viewModel.signUp(email, pass, name) { result ->
                    if (result.success) {
                        showAuthDialog = false
                        Toast.makeText(
                            context,
                            result.message ?: "Zenith account created successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, result.message ?: "Sign up failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Export Data Modal
    if (showExportModal) {
        ExportDataModal(
            transactions = state.transactions,
            currencySymbol = state.currencySymbol,
            onDismiss = { showExportModal = false }
        )
    }

    // Import Data Modal
    if (showImportModal) {
        ImportDataModal(
            existingTransactions = state.transactions,
            currencySymbol = state.currencySymbol,
            onDismiss = { showImportModal = false },
            onImportConfirmed = { transactionsToImport ->
                viewModel.importTransactions(transactionsToImport)
            },
            onNavigateToActivity = onNavigateToActivity
        )
    }

    // Backup & Restore Modal
    if (showBackupModal) {
        BackupRestoreModal(
            transactions = state.transactions,
            categories = state.categories,
            budgets = state.budgets,
            savingsGoals = state.savingsGoals,
            onDismiss = { showBackupModal = false },
            onRestoreBackup = { restoredTx, restoredBudgets, restoredGoals ->
                viewModel.restoreFullBackup(restoredTx, restoredBudgets, restoredGoals)
            }
        )
    }
}

@Composable
fun ProfileSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = SlateDarkTextSecondary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun UserProfileGlassHeader(
    userName: String,
    userEmail: String?,
    isGuest: Boolean,
    onEditNameClick: () -> Unit,
    onSignInClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSignOutClick: () -> Unit,
    isSyncing: Boolean
) {
    val initial = userName.trim().firstOrNull()?.uppercase() ?: "Z"

    CleanCard(
        modifier = Modifier.fillMaxWidth().testTag("profile_header_card"),
        backgroundColor = SlateDarkSurface.copy(alpha = 0.85f),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (!isGuest) listOf(EmeraldDarkPrimary, PastelCyan)
                            else listOf(SlateDarkSurfaceVariant, SlateDarkBorder)
                        )
                    )
                    .border(
                        1.5.dp,
                        if (!isGuest) PastelIndigo.copy(alpha = 0.6f) else GlassBorderColor,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!isGuest) {
                    Text(
                        text = initial,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PersonOutline,
                        contentDescription = null,
                        tint = PastelIndigo,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // User Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (!isGuest) userName else "Guest Mode",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SlateDarkTextPrimary
                    )

                    if (!isGuest) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onEditNameClick,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = PastelCyan, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Text(
                    text = if (!isGuest) (userEmail ?: "Cloud Account") else "Local Vault on Device",
                    fontSize = 12.sp,
                    color = SlateDarkTextSecondary
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isGuest) PastelGreenContainer else PastelCyanContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (!isGuest) PastelGreen.copy(alpha = 0.3f) else PastelCyan.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (!isGuest) "● Cloud Synced" else "● Local Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isGuest) PastelGreen else PastelCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = GlassBorderColor)
        Spacer(modifier = Modifier.height(12.dp))

        if (!isGuest) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSyncClick,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Syncing...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onSignOutClick,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSignInClick,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onCreateAccountClick,
                    modifier = Modifier.weight(1.2f).height(40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create Account", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Edit Display Name", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                Text("Change how your name appears on family ledger entries", fontSize = 12.sp, color = SlateDarkTextSecondary, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) onSave(name.trim())
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldDarkPrimary,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateDarkTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = SlateDarkTextSecondary
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldDarkPrimary,
                uncheckedThumbColor = SlateDarkTextSecondary,
                uncheckedTrackColor = SlateDarkSurfaceVariant
            )
        )
    }
}

@Composable
fun PreferenceActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    accentColor: Color = SlateDarkTextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accentColor != SlateDarkTextPrimary) accentColor else EmeraldDarkPrimary,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = SlateDarkTextSecondary
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = actionLabel,
            tint = SlateDarkTextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun AuthDialog(
    isSignUpInitial: Boolean,
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit
) {
    var isSignUp by remember { mutableStateOf(isSignUpInitial) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ZenithLogo(size = 40.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isSignUp) "Create Zenith Account" else "Sign In to Zenith",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextPrimary
                )
                Text(
                    text = if (isSignUp) "Cloud backup & family vault synchronization" else "Access your synced financial vaults",
                    fontSize = 12.sp,
                    color = SlateDarkTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (isSignUp) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (isSignUp) {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                onSignUp(email.trim(), password.trim(), fullName.trim().ifBlank { email.substringBefore("@") })
                            }
                        } else {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                onSignIn(email.trim(), password.trim())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Text(if (isSignUp) "Create Account" else "Sign In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Create one",
                        fontSize = 12.sp,
                        color = GoldAccent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpiNotificationSettingsDialog(
    isPermissionGranted: Boolean,
    isAutoDetectEnabled: Boolean,
    onToggleAutoDetect: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onSimulateTestPayment: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPermissionGranted && isAutoDetectEnabled) IncomeGreen.copy(alpha = 0.2f) else GoalAmber.copy(alpha = 0.2f))
                                .border(1.dp, if (isPermissionGranted && isAutoDetectEnabled) IncomeGreen.copy(alpha = 0.4f) else GoalAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isPermissionGranted && isAutoDetectEnabled) IncomeGreen else GoalAmber,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Auto-Track UPI Payments",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "Instant zero-click expense logging",
                                fontSize = 11.sp,
                                color = SlateDarkTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        !isPermissionGranted -> GoalAmber.copy(alpha = 0.12f)
                        !isAutoDetectEnabled -> SlateDarkSurfaceVariant.copy(alpha = 0.5f)
                        else -> IncomeGreen.copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            !isPermissionGranted -> GoalAmber.copy(alpha = 0.35f)
                            !isAutoDetectEnabled -> GlassBorderColor
                            else -> IncomeGreen.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPermissionGranted && isAutoDetectEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isPermissionGranted && isAutoDetectEnabled) IncomeGreen else GoalAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    !isPermissionGranted -> "Android Permission Required"
                                    !isAutoDetectEnabled -> "Detection Paused in App"
                                    else -> "Service Active & Listening"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    !isPermissionGranted -> GoalAmber
                                    !isAutoDetectEnabled -> SlateDarkTextSecondary
                                    else -> IncomeGreen
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = when {
                                !isPermissionGranted -> "Android requires Notification Access so Zenith can detect payment receipts from your UPI apps. 100% private & on-device."
                                !isAutoDetectEnabled -> "Notification listener is granted in system, but paused by your in-app preference."
                                else -> "Zenith is actively monitoring UPI receipts. When you make a payment in GPay, PhonePe, Paytm, or your banking app, Zenith will prompt you with one tap to save it."
                            },
                            fontSize = 11.sp,
                            color = SlateDarkTextSecondary,
                            lineHeight = 15.sp
                        )

                        if (!isPermissionGranted) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onOpenSystemSettings,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoalAmber),
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Grant Permission in Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // In-App Master Toggle
                CleanCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SlateDarkSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    PreferenceSwitchRow(
                        icon = Icons.Default.PowerSettingsNew,
                        title = "Enable Auto-Detection",
                        subtitle = "Toggle payment interceptor inside Zenith",
                        checked = isAutoDetectEnabled,
                        onCheckedChange = onToggleAutoDetect
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Supported Apps section
                Text(
                    text = "Supported Indian Payment Apps",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateDarkTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val supportedApps = listOf("Google Pay", "PhonePe", "Paytm", "CRED", "BHIM", "Amazon Pay", "SBI YONO", "HDFC PayZapp", "ICICI iMobile", "Axis Mobile")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    supportedApps.forEach { appName ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SlateDarkSurfaceVariant,
                            border = BorderStroke(1.dp, GlassBorderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(6.dp).clip(CircleShape).background(IncomeGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(appName, fontSize = 11.sp, color = SlateDarkTextSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Simulate Test & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSimulateTestPayment,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Detection", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

