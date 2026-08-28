package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionType
import com.example.ui.components.CleanCard
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowUiState
import java.util.*

enum class AnalyticsTimeRange(val label: String) {
    THIS_MONTH("This Month"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days"),
    ALL_TIME("All Time")
}

@Composable
fun AnalyticsScreen(
    state: CashFlowUiState,
    totalIncome: Double,
    totalExpense: Double,
    onGenerateAiAdvice: (Double, Double, String) -> Unit,
    currentFinanceScope: FinanceScope = FinanceScope.PERSONAL,
    familyMembers: List<FamilyMemberEntity> = emptyList()
) {
    var selectedTimeRange by remember { mutableStateOf(AnalyticsTimeRange.THIS_MONTH) }

    val filteredTransactions = remember(state.transactions, selectedTimeRange) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        when (selectedTimeRange) {
            AnalyticsTimeRange.LAST_7_DAYS -> {
                val cutOff = now - (7L * 24 * 60 * 60 * 1000)
                state.transactions.filter { it.dateMillis >= cutOff }
            }
            AnalyticsTimeRange.LAST_30_DAYS -> {
                val cutOff = now - (30L * 24 * 60 * 60 * 1000)
                state.transactions.filter { it.dateMillis >= cutOff }
            }
            AnalyticsTimeRange.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfMonth = cal.timeInMillis
                state.transactions.filter { it.dateMillis >= startOfMonth }
            }
            AnalyticsTimeRange.ALL_TIME -> state.transactions
        }
    }

    val rangeIncome = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }
    val rangeExpense = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    val netSavings = rangeIncome - rangeExpense
    val savingsRate = if (rangeIncome > 0) ((netSavings / rangeIncome) * 100).toInt().coerceAtLeast(0) else 0

    val expensesByCategory = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    val paymentMethodBreakdown = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.paymentMethod }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    val topCategory = remember(expensesByCategory) {
        expensesByCategory.maxByOrNull { it.value }?.key ?: "Food & Dining"
    }

    val palette = remember {
        listOf(
            Color(0xFF10B981), // Emerald
            Color(0xFF38BDF8), // Sky
            Color(0xFF8B5CF6), // Violet
            Color(0xFFF59E0B), // Amber
            Color(0xFFEC4899), // Pink
            Color(0xFFEF4444), // Red
            Color(0xFF6366F1), // Indigo
            Color(0xFF14B8A6)  // Teal
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp)
    ) {
        // --- 1. TITLE HEADER & TIME RANGE SELECTOR ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentFinanceScope == FinanceScope.FAMILY) "Family Analytics" else "Financial Analytics",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SlateDarkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Visual spending breakdown & health metrics",
                        fontSize = 12.sp,
                        color = SlateDarkTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Time Range Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SlateDarkSurfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnalyticsTimeRange.entries.forEach { range ->
                    val isSelected = selectedTimeRange == range
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) EmeraldDarkPrimary else Color.Transparent
                            )
                            .clickable { selectedTimeRange = range },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = range.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else SlateDarkTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 2. 4-METRIC SUMMARY CARDS GRID ---
        if (filteredTransactions.isEmpty()) {
            item {
                CleanCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = SlateDarkTextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No records for selected period",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Add income or expenses to see real-time chart insights.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Income Summary Card
                    CleanCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = SlateDarkSurface.copy(alpha = 0.85f),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PastelGreenContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = PastelGreen, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Income", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextSecondary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "+${state.currencySymbol}${String.format(Locale.US, "%,.0f", rangeIncome)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PastelGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Expenses Summary Card
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = GlassCardBg
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Expenses", fontSize = 11.sp, color = SlateDarkTextSecondary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "-${state.currencySymbol}${String.format(Locale.US, "%,.0f", rangeExpense)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Net Savings Card
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = GlassCardBg
                    ) {
                        Text("Net Cash Flow", fontSize = 11.sp, color = SlateDarkTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${if (netSavings >= 0) "+" else ""}${state.currencySymbol}${String.format(Locale.US, "%,.0f", netSavings)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (netSavings >= 0) IncomeGreen else ExpenseRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Savings Rate Card
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = GlassCardBg
                    ) {
                        Text("Savings Rate", fontSize = 11.sp, color = SlateDarkTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$savingsRate%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- 3. REDESIGNED CASH FLOW COMPARISON CHART (INCOME VS EXPENSES) ---
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("income_vs_expense_chart_card"),
                    backgroundColor = GlassCardBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Income vs. Expenses Flow",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "${selectedTimeRange.label} cash flow ratio",
                                fontSize = 11.sp,
                                color = SlateDarkTextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (netSavings >= 0) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (netSavings >= 0) "Surplus" else "Deficit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (netSavings >= 0) IncomeGreen else ExpenseRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val maxVal = maxOf(rangeIncome, rangeExpense, 1.0)
                    val incomeRatio = (rangeIncome / maxVal).toFloat().coerceIn(0.1f, 1.0f)
                    val expenseRatio = (rangeExpense / maxVal).toFloat().coerceIn(0.1f, 1.0f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Income Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight().weight(1f)
                        ) {
                            Text(
                                text = "${state.currencySymbol}${String.format(Locale.US, "%,.0f", rangeIncome)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .fillMaxHeight(incomeRatio)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(IncomeGreen, Color(0xFF059669))
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Total Income",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateDarkTextSecondary,
                                maxLines = 1
                            )
                        }

                        // Expense Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight().weight(1f)
                        ) {
                            Text(
                                text = "${state.currencySymbol}${String.format(Locale.US, "%,.0f", rangeExpense)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .fillMaxHeight(expenseRatio)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(ExpenseRed, Color(0xFFB91C1C))
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Total Expenses",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateDarkTextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- 4. REDESIGNED EXPENSE BREAKDOWN DONUT & CATEGORY LIST ---
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("category_breakdown_card"),
                    backgroundColor = GlassCardBg
                ) {
                    Text(
                        text = "Expense Breakdown",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextPrimary
                    )
                    Text(
                        text = "Where your money went in ${selectedTimeRange.label.lowercase()}",
                        fontSize = 11.sp,
                        color = SlateDarkTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (expensesByCategory.isEmpty() || rangeExpense <= 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = SlateDarkTextMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No expenses recorded",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                        }
                    } else {
                        val totalCatExpense = expensesByCategory.values.sum().coerceAtLeast(1.0)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Donut Canvas with Center Summary
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(130.dp)
                            ) {
                                Canvas(modifier = Modifier.size(130.dp)) {
                                    var startAngle = -90f
                                    val isSingleEntry = expensesByCategory.size == 1
                                    expensesByCategory.entries.forEachIndexed { idx, entry ->
                                        val sweep = ((entry.value / totalCatExpense) * 360f).toFloat().coerceIn(0f, 360f)
                                        val color = palette[idx % palette.size]

                                        if (sweep > 0f) {
                                            drawArc(
                                                color = color,
                                                startAngle = startAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                style = Stroke(
                                                    width = 18.dp.toPx(),
                                                    cap = if (isSingleEntry) StrokeCap.Butt else StrokeCap.Round
                                                )
                                            )
                                        }
                                        startAngle += sweep
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${state.currencySymbol}${String.format(Locale.US, "%,.0f", rangeExpense)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SlateDarkTextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Spent",
                                        fontSize = 10.sp,
                                        color = SlateDarkTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Category Legend Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                expensesByCategory.entries.sortedByDescending { it.value }.take(5).forEachIndexed { idx, entry ->
                                    val color = palette[idx % palette.size]
                                    val pct = ((entry.value / totalCatExpense) * 100).toInt()

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = entry.key,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = SlateDarkTextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "$pct% • ${state.currencySymbol}${String.format(Locale.US, "%,.0f", entry.value)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SlateDarkTextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        // Mini Progress Track
                                        LinearProgressIndicator(
                                            progress = { (pct / 100f).coerceIn(0.02f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = color,
                                            trackColor = SlateDarkSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- 5. PAYMENT METHOD DISTRIBUTION ---
            if (paymentMethodBreakdown.isNotEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassCardBg
                    ) {
                        Text(
                            text = "Payment Method Breakdown",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val totalPayExpense = paymentMethodBreakdown.values.sum().coerceAtLeast(1.0)
                        paymentMethodBreakdown.entries.sortedByDescending { it.value }.forEach { entry ->
                            val pct = ((entry.value / totalPayExpense) * 100).toInt()
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when {
                                                entry.key.contains("UPI", ignoreCase = true) -> Icons.Default.QrCodeScanner
                                                entry.key.contains("Card", ignoreCase = true) -> Icons.Default.CreditCard
                                                entry.key.contains("Bank", ignoreCase = true) -> Icons.Default.AccountBalance
                                                else -> Icons.Default.Payments
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = CyanDarkSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(entry.key, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SlateDarkTextPrimary)
                                    }
                                    Text(
                                        "${state.currencySymbol}${String.format(Locale.US, "%,.0f", entry.value)} ($pct%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateDarkTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0.02f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = CyanDarkSecondary,
                                    trackColor = SlateDarkSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // --- 6. GEMINI FINANCIAL COACH CARD ---
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateDarkSurfaceVariant),
                    border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(EmeraldDarkPrimary.copy(alpha = 0.6f), CyanDarkSecondary.copy(alpha = 0.6f)))),
                    modifier = Modifier.fillMaxWidth().testTag("ai_financial_coach_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(EmeraldDarkPrimary, CyanDarkSecondary))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Coach",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Gemini Financial Coach",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "AI-Powered Budget & Cashflow Insights",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onGenerateAiAdvice(rangeIncome, rangeExpense, topCategory) },
                                modifier = Modifier.size(32.dp).testTag("refresh_ai_coach_btn")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh Advice",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (state.isAiCoachLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Analyzing your spending patterns...",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            val adviceText = state.aiCoachAdvice?.ifBlank { null } ?: "• **Spending Velocity**: Log your daily expenses to unlock actionable savings recommendations.\n• **Rule of 50/30/20**: Strive to keep fixed living costs under 50% of your net income.\n• **Savings Cushion**: Build a 3-6 month reserve fund in high-yield liquid savings."

                            Text(
                                text = adviceText,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
