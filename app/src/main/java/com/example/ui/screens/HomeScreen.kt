package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionEntity
import com.example.data.models.TransactionType
import com.example.ui.components.CleanCard
import com.example.ui.components.GlassCard
import com.example.ui.components.TransactionDetailModal
import com.example.ui.components.TransactionItemCard
import com.example.ui.components.ZenithLogo
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowUiState
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
    onOpenUpiScan: () -> Unit = {},
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onManageFamilyMembers: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onUpdateTransaction: ((TransactionEntity) -> Unit)? = null,
    familyMembers: List<FamilyMemberEntity> = emptyList(),
    currentUserName: String = "You"
) {
    var selectedTransactionForDetails by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedFamilyMemberFilter by remember { mutableStateOf<String?>(null) }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp)
    ) {
        // --- 1. BRAND HEADER ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZenithLogo(size = 38.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ZENITH",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.6.sp,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = if (isFamily) "Family Ledger Active" else "Good day, $currentUserName",
                            fontSize = 12.sp,
                            color = if (isFamily) PastelCyan else SlateDarkTextSecondary,
                            fontWeight = if (isFamily) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clean Voice Entry Pill in Header
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PastelIndigoContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PastelIndigo.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenVoiceAssistant() }
                            .testTag("btn_voice_entry_header")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Entry",
                                tint = PastelIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Voice",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary,
                                maxLines = 1
                            )
                        }
                    }

                    // Profile Avatar Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SlateDarkSurfaceVariant)
                            .border(1.dp, GlassBorderColor, CircleShape)
                            .clickable { onNavigateToProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFamily) Icons.Default.Group else Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = if (isFamily) PastelCyan else PastelIndigo,
                            modifier = Modifier.size(19.dp)
                        )
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

        // --- 3. FAMILY MEMBER BAR (If Family Mode) ---
        if (isFamily) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FAMILY MEMBERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = SlateDarkTextSecondary
                    )

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

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
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

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // --- 4. MAIN BALANCE CLEAN HERO CARD ---
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
                            brush = if (isFamily) FamilyHeroCardGradient else HeroCardGradient,
                            shape = RoundedCornerShape(26.dp)
                        )
                        .border(1.dp, if (isFamily) PastelCyan.copy(alpha = 0.35f) else GlassBorderColor, RoundedCornerShape(26.dp))
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isFamily) "FAMILY VAULT BALANCE" else "TOTAL NET BALANCE",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp,
                                color = if (isFamily) PastelCyan else PastelIndigo
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

                        // Large Net Balance Typography
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

                        // Inflow / Outflow Breakdown Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Income Pill
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
                                            if (isFamily) "Family In" else "Income",
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

                            // Expense Pill
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
                                            if (isFamily) "Family Out" else "Spent",
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

        // --- 5. QUICK ACTIONS HUB (4 Clean Action Cards) ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Scan UPI QR Action
                QuickActionCard(
                    icon = Icons.Default.QrCodeScanner,
                    label = "Scan QR",
                    accentColor = PastelPurple,
                    containerColor = PastelPurpleContainer,
                    testTag = "btn_scan_upi_qr",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenUpiScan
                )

                // Scan Receipt Action
                QuickActionCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    label = "Receipt",
                    accentColor = PastelCyan,
                    containerColor = PastelCyanContainer,
                    testTag = "btn_scan_receipt",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenReceiptScanner
                )

                // UPI Pay Action
                QuickActionCard(
                    icon = Icons.Default.Payment,
                    label = "UPI Pay",
                    accentColor = PastelGreen,
                    containerColor = PastelGreenContainer,
                    testTag = "btn_pay_upi",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenUpiPay
                )

                // AI Voice Entry Action
                QuickActionCard(
                    icon = Icons.Default.Mic,
                    label = "Voice AI",
                    accentColor = PastelIndigo,
                    containerColor = PastelIndigoContainer,
                    testTag = "btn_voice_entry",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenVoiceAssistant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 6. SPENDING INSIGHT CARD ---
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
                            text = if (isFamily) "Family Spending Insight" else "Smart Insight",
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

        // --- 7. RECENT TRANSACTIONS HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isFamily) "Family Activity" else "Recent Activity",
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
                        color = if (isFamily) GoldAccent else EmeraldDarkPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- 8. RECENT TRANSACTIONS LIST ---
        if (displayedTransactions.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassCardBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = SlateDarkTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isFamily) "No family transactions yet" else "No transactions yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = if (isFamily) "Add your first family expense or income entry." else "Your recent income and expenses will appear here.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(displayedTransactions.take(6), key = { "recent_${it.id}" }) { tx ->
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SlateDarkSurface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
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
