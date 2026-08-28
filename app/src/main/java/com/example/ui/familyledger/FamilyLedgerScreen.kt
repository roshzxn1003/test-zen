package com.example.ui.familyledger

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.familyledger.FamilySyncStatus
import com.example.data.familyledger.FamilyVault
import com.example.data.familyledger.LedgerTransaction
import com.example.data.models.TransactionType
import com.example.ui.theme.*

@Composable
fun FamilyLedgerScreen(
    viewModel: FamilyLedgerViewModel,
    state: FamilyLedgerUiState,
    onBackToPersonal: () -> Unit
) {
    val context = LocalContext.current
    var showAddEditDialog by remember { mutableStateOf(false) }
    var defaultTypeForDialog by remember { mutableStateOf(TransactionType.EXPENSE) }
    var transactionToEdit by remember { mutableStateOf<LedgerTransaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<LedgerTransaction?>(null) }
    var showCreateVaultDialog by remember { mutableStateOf(false) }
    var showVaultSwitchMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = AmbientBackgroundBrush),
        containerColor = Color.Transparent,
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    transactionToEdit = null
                    defaultTypeForDialog = TransactionType.EXPENSE
                    showAddEditDialog = true
                },
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 12.dp)
                    .testTag("fab_add_ledger_transaction"),
                shape = RoundedCornerShape(20.dp),
                containerColor = EmeraldDarkPrimary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Entry",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- 1. ELEGANT TOP BAR ---
            Surface(
                color = SlateDarkSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Back button & Vault Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GlassCardBgElevated,
                                border = BorderStroke(1.dp, GlassBorderColor),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onBackToPersonal() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Personal",
                                        tint = SlateDarkTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Vault Name Pill Dropdown
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = GlassCardBgElevated,
                                    border = BorderStroke(1.dp, GlassBorderHighlight.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { showVaultSwitchMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(PastelIndigo)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = state.activeVault?.familyName ?: "Family Vault",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateDarkTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = SlateDarkTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showVaultSwitchMenu,
                                    onDismissRequest = { showVaultSwitchMenu = false }
                                ) {
                                    state.allVaults.forEach { vault ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = vault.familyName,
                                                    fontWeight = if (vault.familyId == state.activeVault?.familyId) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                                            onClick = {
                                                viewModel.setActiveFamily(vault.familyId)
                                                showVaultSwitchMenu = false
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("+ Create New Vault", color = PastelIndigo, fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = PastelIndigo) },
                                        onClick = {
                                            showVaultSwitchMenu = false
                                            showCreateVaultDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        // Right: Live Sync Status Badge
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when (state.syncStatus) {
                                FamilySyncStatus.SYNCED -> IncomeGreenContainer.copy(alpha = 0.35f)
                                FamilySyncStatus.SYNCING -> GoalAmberContainer.copy(alpha = 0.35f)
                                FamilySyncStatus.OFFLINE -> GlassCardBgElevated
                                FamilySyncStatus.ERROR -> ExpenseRedContainer.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when (state.syncStatus) {
                                    FamilySyncStatus.SYNCED -> IncomeGreen.copy(alpha = 0.4f)
                                    FamilySyncStatus.SYNCING -> GoalAmber.copy(alpha = 0.4f)
                                    FamilySyncStatus.OFFLINE -> GlassBorderColor
                                    FamilySyncStatus.ERROR -> ExpenseRed.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.syncNow()
                                    Toast.makeText(context, "Syncing Family Vault...", Toast.LENGTH_SHORT).show()
                                }
                                .testTag("badge_sync_status")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (state.syncStatus) {
                                    FamilySyncStatus.SYNCED -> {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(IncomeGreen)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Live", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                                    }
                                    FamilySyncStatus.SYNCING -> {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Syncing",
                                            tint = GoalAmber,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .rotate(spinAngle)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Syncing", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoalAmber)
                                    }
                                    FamilySyncStatus.OFFLINE -> {
                                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = SlateDarkTextSecondary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Offline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextSecondary)
                                    }
                                    FamilySyncStatus.ERROR -> {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ExpenseRed)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- 2. SEGMENTED TAB SLIDER ---
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SlateDarkSurfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, GlassBorderColor),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val tabs = listOf("Overview", "Activity", "Members", "Insights")
                            tabs.forEachIndexed { index, title ->
                                val isSelected = state.selectedSubTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(
                                            if (isSelected) EmeraldDarkPrimary else Color.Transparent
                                        )
                                        .clickable { viewModel.setSubTab(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else SlateDarkTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. SUB-SCREEN CONTENT ---
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = state.selectedSubTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "family_tab_switch"
                ) { currentTab ->
                    when (currentTab) {
                        0 -> FamilyLedgerDashboard(
                            state = state,
                            onOpenAddTransaction = { type ->
                                transactionToEdit = null
                                defaultTypeForDialog = type
                                showAddEditDialog = true
                            },
                            onEditTransaction = { tx ->
                                transactionToEdit = tx
                                showAddEditDialog = true
                            },
                            onDeleteTransaction = { tx ->
                                transactionToDelete = tx
                            },
                            onNavigateToTransactions = { viewModel.setSubTab(1) },
                            onNavigateToMembers = { viewModel.setSubTab(2) },
                            onSyncNow = {
                                viewModel.syncNow()
                                Toast.makeText(context, "Syncing Family Vault...", Toast.LENGTH_SHORT).show()
                            }
                        )
                        1 -> FamilyLedgerTransactionsList(
                            state = state,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onFilterTypeChange = { viewModel.setFilterType(it) },
                            onFilterCategoryChange = { viewModel.setFilterCategory(it) },
                            onFilterMemberChange = { viewModel.setFilterMemberId(it) },
                            onSortChange = { viewModel.setSortBy(it) },
                            onOpenAddTransaction = {
                                transactionToEdit = null
                                defaultTypeForDialog = TransactionType.EXPENSE
                                showAddEditDialog = true
                            },
                            onEditTransaction = { tx ->
                                transactionToEdit = tx
                                showAddEditDialog = true
                            },
                            onDeleteTransaction = { tx ->
                                transactionToDelete = tx
                            }
                        )
                        2 -> FamilyLedgerMembersView(
                            state = state,
                            onAddMember = { name, role ->
                                viewModel.addFamilyMember(name, role) { success ->
                                    if (success) Toast.makeText(context, "Member added & synced to cloud!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRemoveMember = { memberId ->
                                viewModel.removeFamilyMember(memberId)
                            },
                            onJoinVault = { code ->
                                viewModel.joinFamilyByInviteCode(code) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        3 -> FamilyLedgerAnalyticsView(state = state)
                    }
                }
            }
        }
    }

    // --- ADD / EDIT TRANSACTION DIALOG ---
    if (showAddEditDialog) {
        AddEditLedgerTransactionDialog(
            existing = transactionToEdit,
            initialType = defaultTypeForDialog,
            members = state.members,
            currentUserId = viewModel.currentUserId,
            currentUserName = viewModel.currentUserName,
            isSaving = state.isSavingTransaction,
            saveResult = state.transactionSaveResult,
            onDismiss = {
                showAddEditDialog = false
                transactionToEdit = null
            },
            onSave = { title, desc, amt, cat, type, paymentMethod, payerId, payerName, dateMillis ->
                if (transactionToEdit == null) {
                    viewModel.createTransaction(
                        title = title,
                        description = desc,
                        amount = amt,
                        category = cat,
                        type = type,
                        paymentMethod = paymentMethod,
                        paidByMemberId = payerId,
                        paidByName = payerName,
                        dateMillis = dateMillis
                    ) { success ->
                        if (success) {
                            showAddEditDialog = false
                            Toast.makeText(context, "Saved to Family Vault!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    viewModel.updateTransaction(
                        existing = transactionToEdit!!,
                        title = title,
                        description = desc,
                        amount = amt,
                        category = cat,
                        type = type,
                        paymentMethod = paymentMethod,
                        paidByMemberId = payerId,
                        paidByName = payerName,
                        dateMillis = dateMillis
                    ) { success ->
                        if (success) {
                            showAddEditDialog = false
                            transactionToEdit = null
                            Toast.makeText(context, "Entry updated!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    // --- DELETE TRANSACTION CONFIRMATION DIALOG ---
    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Family Entry", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${transactionToDelete?.title}' (₹%.2f)? This will sync across all devices.".format(transactionToDelete?.amount ?: 0.0)) },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = transactionToDelete
                        transactionToDelete = null
                        if (toDel != null) {
                            viewModel.deleteTransaction(toDel.transactionId) {
                                Toast.makeText(context, "Entry deleted.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelRose)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- CREATE NEW VAULT DIALOG ---
    if (showCreateVaultDialog) {
        var newVaultName by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showCreateVaultDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Create Family Vault", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                    Text("A shared ledger for your household or group.", fontSize = 12.sp, color = SlateDarkTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newVaultName,
                        onValueChange = { newVaultName = it },
                        label = { Text("Vault Name") },
                        placeholder = { Text("e.g. Roshan Household") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (newVaultName.trim().isNotBlank()) {
                                viewModel.createFamilyVault(newVaultName.trim()) { success ->
                                    if (success) {
                                        showCreateVaultDialog = false
                                        Toast.makeText(context, "New Family Vault created!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Create Vault", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
