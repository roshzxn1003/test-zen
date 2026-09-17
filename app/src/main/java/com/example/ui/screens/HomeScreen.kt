package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FamilyRole
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionEntity
import com.example.data.models.TransactionType
import com.example.ui.components.CleanCard
import com.example.ui.components.GlassCard
import com.example.ui.components.TransactionDetailModal
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowUiState
import com.example.ui.viewmodel.FamilyLedgerSettlementSummary
import com.example.ui.viewmodel.SyncUiState
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    state: CashFlowUiState,
    totalIncome: Double,
    totalExpense: Double,
    netBalance: Double,
    currentFinanceScope: FinanceScope,
    onScopeChange: (FinanceScope) -> Unit,
    onOpenAddTransaction: () -> Unit,
    onOpenVoiceAssistant: () -> Unit,
    onOpenReceiptScanner: () -> Unit,
    onOpenUpiPay: () -> Unit = {},
    onOpenUpiScanner: () -> Unit = {},
    familySettlementSummary: FamilyLedgerSettlementSummary = FamilyLedgerSettlementSummary(),
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onManageFamilyMembers: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onUpdateTransaction: ((TransactionEntity) -> Unit)? = null,
    familyMembers: List<FamilyMemberEntity> = emptyList(),
    familyName: String = "Family Vault",
    inviteCode: String = "",
    currentUserName: String = "You",
    syncUiState: SyncUiState = SyncUiState.Idle,
    onSyncNow: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTransactionForDetails by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedFamilyMemberFilter by remember { mutableStateOf<String?>(null) }
    var familySubTab by remember { mutableIntStateOf(0) }

    val isFamily = currentFinanceScope == FinanceScope.FAMILY

    // Filter transactions if family member is selected
    val displayedTransactions = remember(state.transactions, selectedFamilyMemberFilter) {
        if (selectedFamilyMemberFilter == null) {
            state.transactions
        } else {
            state.transactions.filter { it.createdByUserId == selectedFamilyMemberFilter }
        }
    }

    // Dynamic Spending Insight
    val spendingInsight = remember(state.transactions, totalExpense, totalIncome, isFamily) {
        if (state.transactions.isEmpty()) {
            if (isFamily) "Add family entries to see group financial insights."
            else "Add a few transactions to unlock your spending insights."
        } else {
            val topCategory = state.transactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }
                .maxByOrNull { entry -> entry.value.sumOf { it.amount } }
            if (topCategory != null && totalExpense > 0) {
                val pct = ((topCategory.value.sumOf { it.amount } / totalExpense) * 100).toInt()
                if (isFamily) "$pct% of total family spending is in ${topCategory.key}."
                else "$pct% of your current spending is in ${topCategory.key}."
            } else if (totalIncome > 0) {
                val savingsRate = (((totalIncome - totalExpense) / totalIncome) * 100).toInt().coerceAtLeast(0)
                "Net savings rate is $savingsRate% this cycle."
            } else {
                "Track your daily cash flow to maintain financial clarity."
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin_home")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_home"
    )

    val timeBasedGreeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Welcome back"
        }
    }

    val formattedTodayDate = remember {
        SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
    }

    val userInitial = remember(currentUserName) {
        currentUserName.trim().firstOrNull()?.uppercase() ?: "Y"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 108.dp)
    ) {
        // --- 1. MODERN FINTECH HEADER ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Interactive User Profile Avatar + Greetings & Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToProfile() }
                        .padding(vertical = 2.dp)
                        .testTag("header_user_profile")
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Gradient Ring Background
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            EmeraldDarkPrimary,
                                            PastelIndigo,
                                            PastelCyan,
                                            EmeraldDarkPrimary
                                        )
                                    )
                                )
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(SlateDarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isFamily) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "Family",
                                        tint = PastelCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = userInitial,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Live Sync Dot at bottom-right corner of avatar
                        val syncDotColor = when (syncUiState) {
                            is SyncUiState.Syncing -> PastelCyan
                            is SyncUiState.Success, is SyncUiState.Idle -> EmeraldDarkPrimary
                            is SyncUiState.Error -> Color(0xFFF59E0B)
                        }
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(SlateDarkSurface)
                                .padding(1.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(syncDotColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(11.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (isFamily) "Family Vault • $formattedTodayDate" else "$timeBasedGreeting • $formattedTodayDate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateDarkTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isFamily) "Family Ledger" else currentUserName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp,
                            color = SlateDarkTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Quick Search + Live Sync Pill + Voice AI Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick Search Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GlassCardBgElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigateToActivity() }
                            .testTag("btn_header_search")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Transactions",
                                tint = SlateDarkTextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Live Cloud Sync Pill
                    val isCurrentlySyncing = syncUiState is SyncUiState.Syncing
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GlassCardBgElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrentlySyncing) PastelCyan.copy(alpha = 0.5f) else GlassBorderColor
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSyncNow?.invoke() }
                            .testTag("btn_header_sync")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrentlySyncing) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Syncing",
                                    tint = PastelCyan,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .rotate(spinAngle)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Syncing",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PastelCyan,
                                    maxLines = 1
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (syncUiState is SyncUiState.Error) Color(0xFFF59E0B)
                                            else EmeraldDarkPrimary
                                        )
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (syncUiState is SyncUiState.Error) "Offline" else "Synced",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Voice Entry Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PastelIndigoContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PastelIndigo.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenVoiceAssistant() }
                            .testTag("btn_voice_entry_header")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Assistant",
                                tint = PastelIndigo,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Voice",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // --- 2. MODE SELECTOR (Personal vs Family Ledger) ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SlateDarkSurface.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Personal Segment
                    val isPersonal = currentFinanceScope == FinanceScope.PERSONAL
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPersonal) EmeraldDarkPrimary else Color.Transparent)
                            .clickable { onScopeChange(FinanceScope.PERSONAL) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isPersonal) Color.White else SlateDarkTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Personal",
                                fontSize = 13.sp,
                                fontWeight = if (isPersonal) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPersonal) Color.White else SlateDarkTextSecondary
                            )
                        }
                    }

                    // Family Ledger Segment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFamily) GoldAccent else Color.Transparent)
                            .clickable { onScopeChange(FinanceScope.FAMILY) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = if (isFamily) Color.Black else SlateDarkTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Family Ledger",
                                fontSize = 13.sp,
                                fontWeight = if (isFamily) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFamily) Color.Black else SlateDarkTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // --- 3. PROPER UNIFIED FAMILY LEDGER (If Family Scope) ---
        if (isFamily) {
            // 1. Vault Header Banner & 1-Tap Invite Bar
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateDarkSurface.copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(PastelCyan.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = PastelCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = familyName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateDarkTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${familyMembers.size.coerceAtLeast(1)} Connected Members",
                                        fontSize = 11.5.sp,
                                        color = SlateDarkTextSecondary
                                    )
                                }
                            }

                            TextButton(
                                onClick = onManageFamilyMembers,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = PastelCyan, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Manage",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PastelCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stylized Invite Code Pill
                        val effectiveCode = inviteCode.ifBlank { "FAMILY-VAULT" }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, PastelCyan.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "INVITE: ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateDarkTextMuted,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = effectiveCode,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PastelCyan,
                                        letterSpacing = 1.5.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Family Invite Code", effectiveCode)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Invite code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Code",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, "Join our Family Vault on Zenith! Use invite code: $effectiveCode")
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Invite Code"))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share Code",
                                            tint = PastelCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. Main Hero Vault Balance Card with Respect to Personal & Settlement Banner
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("net_balance_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color(0xFF1E1B4B),
                                        Color(0xFF0F172A),
                                        Color(0xFF134E4A)
                                    )
                                ),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(PastelCyan.copy(alpha = 0.5f), Color(0xFF818CF8).copy(alpha = 0.35f))),
                                RoundedCornerShape(26.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL FAMILY VAULT SPENDING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = PastelCyan
                                )
                                Text(
                                    text = "${familyMembers.size.coerceAtLeast(1)} Members",
                                    fontSize = 11.sp,
                                    color = SlateDarkTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", familySettlementSummary.familyTotalExpense.coerceAtLeast(totalExpense))}",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3-Metric Interlock: Personal | You Paid | Fair Share
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SlateDarkSurface.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, GlassBorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text("Personal", fontSize = 10.sp, color = SlateDarkTextSecondary, maxLines = 1)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${state.currencySymbol}${String.format(Locale.US, "%,.0f", familySettlementSummary.personalTotalExpense)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PastelIndigo,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SlateDarkSurface.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, GlassBorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text("You Paid", fontSize = 10.sp, color = SlateDarkTextSecondary, maxLines = 1)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${state.currencySymbol}${String.format(Locale.US, "%,.0f", familySettlementSummary.userPaidForFamily)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PastelCyan,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SlateDarkSurface.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, GlassBorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text("Fair Share", fontSize = 10.sp, color = SlateDarkTextSecondary, maxLines = 1)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${state.currencySymbol}${String.format(Locale.US, "%,.0f", familySettlementSummary.fairSharePerMember)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA5B4FC),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Settlement Standing Banner
                            val netSettlement = familySettlementSummary.netSettlementBalance
                            val isOwed = netSettlement > 0
                            val isDeficit = netSettlement < 0

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    isOwed -> PastelGreenContainer.copy(alpha = 0.35f)
                                    isDeficit -> PastelRoseContainer.copy(alpha = 0.35f)
                                    else -> PastelCyanContainer.copy(alpha = 0.35f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isOwed -> PastelGreen.copy(alpha = 0.4f)
                                        isDeficit -> PastelRose.copy(alpha = 0.4f)
                                        else -> PastelCyan.copy(alpha = 0.4f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when {
                                                isOwed -> "You are owed by family members"
                                                isDeficit -> "You owe the family vault"
                                                else -> "All family shares are balanced"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                isOwed -> PastelGreen
                                                isDeficit -> PastelRose
                                                else -> PastelCyan
                                            }
                                        )
                                        Text(
                                            text = when {
                                                isOwed -> "+${state.currencySymbol}${String.format(Locale.US, "%,.2f", netSettlement)}"
                                                isDeficit -> "-${state.currencySymbol}${String.format(Locale.US, "%,.2f", Math.abs(netSettlement))}"
                                                else -> "Settled Up"
                                            },
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }

                                    if (isDeficit) {
                                        Button(
                                            onClick = onOpenUpiPay,
                                            colors = ButtonDefaults.buttonColors(containerColor = PastelRose),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Settle UPI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Quick Actions Hub (Direct UPI Redirect Scanner, UPI Pay, Receipt, Add Entry)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Default.QrCodeScanner,
                        label = "Scan UPI",
                        accentColor = PastelCyan,
                        containerColor = PastelCyanContainer,
                        testTag = "btn_scan_upi_qr",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenUpiScanner
                    )
                    QuickActionCard(
                        icon = Icons.Default.Payment,
                        label = "UPI Pay",
                        accentColor = PastelGreen,
                        containerColor = PastelGreenContainer,
                        testTag = "btn_pay_upi",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenUpiPay
                    )
                    QuickActionCard(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        label = "Receipt",
                        accentColor = PastelAmber,
                        containerColor = PastelAmberContainer,
                        testTag = "btn_scan_receipt",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenReceiptScanner
                    )
                    QuickActionCard(
                        icon = Icons.Default.Add,
                        label = "+ Add",
                        accentColor = EmeraldDarkPrimary,
                        containerColor = EmeraldDarkContainer,
                        testTag = "btn_quick_add",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAddTransaction
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. Family Members & Contributions Matrix
            item {
                CleanCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SlateDarkSurface.copy(alpha = 0.85f),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint = PastelCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MEMBER BALANCES & SHARES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = SlateDarkTextSecondary
                                )
                            }

                            TextButton(
                                onClick = onManageFamilyMembers,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "+ Manage",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PastelCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (familyMembers.isEmpty()) {
                            Text(
                                text = "No family members connected yet. Tap + Manage to invite your family.",
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                familyMembers.forEach { member ->
                                    val memberPaid = state.transactions
                                        .filter { it.createdByUserId == member.userId && it.type == TransactionType.EXPENSE }
                                        .sumOf { it.amount }
                                    val diff = memberPaid - familySettlementSummary.fairSharePerMember
                                    val totalExp = familySettlementSummary.familyTotalExpense.coerceAtLeast(1.0)
                                    val progress = (memberPaid / totalExp).toFloat().coerceIn(0f, 1f)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SlateDarkSurfaceVariant.copy(alpha = 0.5f))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(PastelIndigoContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = member.name.take(1).uppercase().ifEmpty { "M" },
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PastelIndigo
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = member.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SlateDarkTextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (member.role == FamilyRole.ADMIN) PastelCyanContainer else Color.White.copy(alpha = 0.08f)
                                                ) {
                                                    Text(
                                                        text = member.role.name,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (member.role == FamilyRole.ADMIN) PastelCyan else SlateDarkTextMuted,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Paid: ${state.currencySymbol}${String.format(Locale.US, "%,.0f", memberPaid)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SlateDarkTextPrimary
                                                )
                                                Text(
                                                    text = if (diff >= 0) "+${state.currencySymbol}${String.format(Locale.US, "%,.0f", diff)} (Owed)" else "-${state.currencySymbol}${String.format(Locale.US, "%,.0f", Math.abs(diff))} (Owes)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (diff >= 0) PastelGreen else PastelRose
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (diff >= 0) PastelGreen else PastelCyan,
                                            trackColor = Color.White.copy(alpha = 0.08f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 5. Family Spending Insight
            item {
                CleanCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SlateDarkSurface.copy(alpha = 0.85f),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PastelAmberContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = PastelAmber,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Family Spending Insight",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = spendingInsight,
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // 6. Recent Family Activity Header & Member Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Recent Family Activity",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Tap & hold any entry for full breakdown",
                            fontSize = 11.sp,
                            color = SlateDarkTextSecondary
                        )
                    }

                    TextButton(onClick = onNavigateToActivity) {
                        Text(
                            text = "See all →",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PastelCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Member Filter Chips
                if (familyMembers.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFamilyMemberFilter == null,
                                onClick = { selectedFamilyMemberFilter = null },
                                label = { Text("All (${familyMembers.size.coerceAtLeast(1)})", fontSize = 11.sp) }
                            )
                        }
                        items(familyMembers) { member ->
                            FilterChip(
                                selected = selectedFamilyMemberFilter == member.userId,
                                onClick = {
                                    selectedFamilyMemberFilter = if (selectedFamilyMemberFilter == member.userId) null else member.userId
                                },
                                label = { Text("👤 ${member.name}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Family Transactions List
            if (displayedTransactions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SlateDarkSurface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, GlassBorderColor),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PastelIndigoContainer)
                                    .border(1.dp, PastelIndigo.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = PastelIndigo,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No family transactions yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "Add your first shared expense to start tracking with your family.",
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                            )
                            Button(
                                onClick = onOpenAddTransaction,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Transaction", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                items(displayedTransactions.take(8), key = { "family_recent_${it.id}" }) { tx ->
                    val memberName = familyMembers.firstOrNull { it.userId == tx.createdByUserId }?.name
                    TransactionItemCard(
                        transaction = tx,
                        currencySymbol = state.currencySymbol,
                        creatorName = memberName,
                        onClick = { selectedTransactionForDetails = it },
                        onLongClick = { selectedTransactionForDetails = it },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        } else {
            // ==========================================
            // PERSONAL FINANCE DASHBOARD (isFamily == false)
            // ==========================================
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("net_balance_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = HeroCardGradient,
                                shape = RoundedCornerShape(26.dp)
                            )
                            .border(1.dp, GlassBorderColor, RoundedCornerShape(26.dp))
                            .padding(22.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL NET BALANCE",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.3.sp,
                                    color = PastelIndigo
                                )

                                if (state.transactions.isNotEmpty()) {
                                    Surface(
                                        color = if (netBalance >= 0) PastelGreenContainer else PastelRoseContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (netBalance >= 0) PastelGreen.copy(alpha = 0.35f) else PastelRose.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (netBalance >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                                contentDescription = null,
                                                tint = if (netBalance >= 0) PastelGreen else PastelRose,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (netBalance >= 0) "Surplus" else "Deficit",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (netBalance >= 0) PastelGreen else PastelRose
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "${if (netBalance >= 0) "" else "-"}${state.currencySymbol}${String.format(Locale.US, "%,.2f", Math.abs(netBalance))}",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = SlateDarkTextPrimary,
                                letterSpacing = (-0.5).sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SlateDarkSurface.copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(PastelGreenContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = PastelGreen,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(
                                                "Income",
                                                fontSize = 11.sp,
                                                color = SlateDarkTextSecondary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "+${state.currencySymbol}${String.format(Locale.US, "%,.0f", totalIncome)}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PastelGreen,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SlateDarkSurface.copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(PastelRoseContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = PastelRose,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(
                                                "Spent",
                                                fontSize = 11.sp,
                                                color = SlateDarkTextSecondary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "-${state.currencySymbol}${String.format(Locale.US, "%,.0f", totalExpense)}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PastelRose,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Quick Actions Hub (4 Cards: Scan QR, UPI Pay, Receipt, Voice Entry)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Default.QrCodeScanner,
                        label = "Scan QR",
                        accentColor = PastelCyan,
                        containerColor = PastelCyanContainer,
                        testTag = "btn_scan_upi_qr",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenUpiScanner
                    )

                    QuickActionCard(
                        icon = Icons.Default.Payment,
                        label = "UPI Pay",
                        accentColor = PastelGreen,
                        containerColor = PastelGreenContainer,
                        testTag = "btn_pay_upi",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenUpiPay
                    )

                    QuickActionCard(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        label = "Receipt",
                        accentColor = PastelAmber,
                        containerColor = PastelAmberContainer,
                        testTag = "btn_scan_receipt",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenReceiptScanner
                    )

                    QuickActionCard(
                        icon = Icons.Default.Mic,
                        label = "Voice Entry",
                        accentColor = PastelIndigo,
                        containerColor = PastelIndigoContainer,
                        testTag = "btn_voice_entry",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenVoiceAssistant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Spending Insight Card
            item {
                CleanCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SlateDarkSurface.copy(alpha = 0.85f),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PastelAmberContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = PastelAmber,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Insight",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = spendingInsight,
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Recent Activity",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Tap & hold any entry for full breakdown",
                            fontSize = 11.sp,
                            color = SlateDarkTextSecondary
                        )
                    }

                    TextButton(onClick = onNavigateToActivity) {
                        Text(
                            text = "See all →",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDarkPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recent Transactions List
            if (displayedTransactions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SlateDarkSurface.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PastelIndigoContainer)
                                    .border(1.dp, PastelIndigo.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = PastelIndigo,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No transactions yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "Track your income, daily spends, or scan a UPI QR code to get started.",
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                            )
                            Button(
                                onClick = onOpenAddTransaction,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Transaction", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                items(displayedTransactions.take(6), key = { "recent_${it.id}" }) { tx ->
                    TransactionItemCard(
                        transaction = tx,
                        currencySymbol = state.currencySymbol,
                        creatorName = null,
                        onClick = { selectedTransactionForDetails = it },
                        onLongClick = { selectedTransactionForDetails = it },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    // --- FULL TRANSACTION DETAIL MODAL ON TAP & HOLD ---
    selectedTransactionForDetails?.let { tx ->
        val memberName = familyMembers.firstOrNull { it.userId == tx.createdByUserId }?.name
        TransactionDetailModal(
            transaction = tx,
            currencySymbol = state.currencySymbol,
            onDismiss = { selectedTransactionForDetails = null },
            onDelete = {
                onDeleteTransaction(it)
                selectedTransactionForDetails = null
            },
            onUpdate = { updated ->
                onUpdateTransaction?.invoke(updated)
                selectedTransactionForDetails = null
            },
            creatorName = memberName
        )
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    containerColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SlateDarkSurface.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = SlateDarkTextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun GlassActionButton(
    icon: ImageVector,
    contentDescription: String? = null,
    label: String? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = EmeraldDarkPrimary,
    onClick: () -> Unit
) {
    val description = contentDescription ?: label
    Surface(
        shape = CircleShape,
        color = GlassCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
