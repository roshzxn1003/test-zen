package com.example.ui.familyledger

import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.familyledger.FamilyVaultMember
import com.example.data.familyledger.LedgerTransaction
import com.example.data.models.TransactionType
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FamilyLedgerDashboard(
    state: FamilyLedgerUiState,
    currencySymbol: String = "₹",
    onOpenAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
    onDeleteTransaction: (LedgerTransaction) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onSyncNow: () -> Unit
) {
    val totalIncome = remember(state.transactions) {
        state.transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }
    val totalExpense = remember(state.transactions) {
        state.transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }
    val netBalance = totalIncome - totalExpense
    val expenseRatio = remember(totalIncome, totalExpense) {
        if (totalIncome + totalExpense > 0) (totalExpense / (totalIncome + totalExpense)).toFloat() else 0.5f
    }

    val recentTransactions = remember(state.transactions) {
        state.transactions.take(8)
    }

    val memberAvatarGradients = listOf(
        Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
        Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))),
        Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444))),
        Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))),
        Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. PREMIUM HERO VAULT CARD ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E1B4B), // Deep Indigo
                                Color(0xFF0F172A), // Slate Dark
                                Color(0xFF090D16)  // Base carbon
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF818CF8).copy(alpha = 0.5f),
                                    Color(0xFF22D3EE).copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header row: Vault Name + Member Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF818CF8).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = PastelIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = state.activeVault?.familyName ?: "Family Vault",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateDarkTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = GlassCardBgElevated,
                            border = BorderStroke(1.dp, GlassBorderColor),
                            modifier = Modifier.clickable { onNavigateToMembers() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = PastelCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${state.members.size} Members",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PastelCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main Balance Display
                    Text(
                        text = "TOTAL NET BALANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextMuted,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$currencySymbol%,.2f".format(netBalance),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = SlateDarkTextPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Inflow vs Outflow Metric Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Income Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = IncomeGreenContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(IncomeGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = IncomeGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Income", fontSize = 10.sp, color = SlateDarkTextSecondary)
                                    Text(
                                        text = "+$currencySymbol%,.0f".format(totalIncome),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Expense Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ExpenseRedContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(ExpenseRed.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Expenses", fontSize = 10.sp, color = SlateDarkTextSecondary)
                                    Text(
                                        text = "-$currencySymbol%,.0f".format(totalExpense),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. QUICK ACTIONS ROW ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add Expense Button
                QuickActionButton(
                    icon = Icons.Default.RemoveCircleOutline,
                    label = "Expense",
                    gradient = Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFF43F5E))),
                    onClick = { onOpenAddTransaction(TransactionType.EXPENSE) }
                )

                // Add Income Button
                QuickActionButton(
                    icon = Icons.Default.AddCircleOutline,
                    label = "Income",
                    gradient = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF10B981))),
                    onClick = { onOpenAddTransaction(TransactionType.INCOME) }
                )

                // Invite / Add Member Button
                QuickActionButton(
                    icon = Icons.Default.PersonAddAlt1,
                    label = "Members",
                    gradient = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF818CF8))),
                    onClick = { onNavigateToMembers() }
                )

                // Sync Now Button
                QuickActionButton(
                    icon = Icons.Default.Sync,
                    label = "Sync",
                    gradient = Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF06B6D4))),
                    onClick = { onSyncNow() }
                )
            }
        }

        // --- 3. MEMBER SPENDING CAROUSEL ---
        if (state.members.isNotEmpty()) {
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
                        color = SlateDarkTextMuted,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Manage →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PastelIndigo,
                        modifier = Modifier.clickable { onNavigateToMembers() }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.members.size) { index ->
                        val member = state.members[index]
                        val gradient = memberAvatarGradients[index % memberAvatarGradients.size]
                        val memberSharePct = if (totalExpense > 0) ((member.totalPaid / totalExpense) * 100).toInt() else 0

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = SlateDarkSurface,
                            border = BorderStroke(1.dp, GlassBorderColor),
                            modifier = Modifier
                                .width(135.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onNavigateToMembers() }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(gradient),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = member.name.take(1).uppercase(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GlassCardBgElevated
                                    ) {
                                        Text(
                                            text = "$memberSharePct%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateDarkTextSecondary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = member.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "$currencySymbol%,.0f".format(member.totalPaid),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SlateDarkTextPrimary
                                )

                                Text(
                                    text = "${member.transactionCount} entries",
                                    fontSize = 10.sp,
                                    color = SlateDarkTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 4. RECENT ACTIVITY LIST ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT ACTIVITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextMuted,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "View All (${state.transactions.size}) →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PastelIndigo,
                    modifier = Modifier.clickable { onNavigateToTransactions() }
                )
            }
        }

        if (recentTransactions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SlateDarkSurface,
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = SlateDarkTextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No entries yet. Tap + Expense to record one.",
                                fontSize = 13.sp,
                                color = SlateDarkTextSecondary
                            )
                        }
                    }
                }
            }
        } else {
            items(recentTransactions, key = { it.transactionId }) { tx ->
                ModernLedgerTransactionCard(
                    transaction = tx,
                    currencySymbol = currencySymbol,
                    onEdit = { onEditTransaction(tx) },
                    onDelete = { onDeleteTransaction(tx) }
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(gradient)
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A).copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = SlateDarkTextSecondary
        )
    }
}

@Composable
fun ModernLedgerTransactionCard(
    transaction: LedgerTransaction,
    currencySymbol: String = "₹",
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isExpense = transaction.type == TransactionType.EXPENSE
    val formattedDate = remember(transaction.dateMillis) {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(transaction.dateMillis))
    }

    val iconVector = when (transaction.category) {
        "Food & Dining" -> Icons.Default.Restaurant
        "Shopping" -> Icons.Default.ShoppingBag
        "Housing & Rent" -> Icons.Default.Home
        "Transportation" -> Icons.Default.DirectionsCar
        "Bills & Utilities" -> Icons.Default.Receipt
        "Entertainment" -> Icons.Default.Movie
        "Healthcare" -> Icons.Default.MedicalServices
        "Salary & Income" -> Icons.Default.Payments
        "Freelance / Business" -> Icons.Default.Work
        "Investments" -> Icons.Default.TrendingUp
        else -> Icons.Default.Category
    }

    val iconColor = if (isExpense) PastelRose else IncomeGreen
    val iconBg = if (isExpense) ExpenseRedContainer.copy(alpha = 0.35f) else IncomeGreenContainer.copy(alpha = 0.35f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SlateDarkSurface,
        border = BorderStroke(1.dp, GlassBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Icon + Title + Payer Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                        .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = transaction.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GlassCardBgElevated
                        ) {
                            Text(
                                text = "Paid by ${transaction.paidByName}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PastelCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formattedDate,
                            fontSize = 10.sp,
                            color = SlateDarkTextMuted
                        )
                    }
                }
            }

            // Right: Amount + Options Menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (isExpense) "-" else "+"}$currencySymbol%,.2f".format(transaction.amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isExpense) SlateDarkTextPrimary else IncomeGreen
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = SlateDarkTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Entry") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = PastelRose) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PastelRose) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
