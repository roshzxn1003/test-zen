package com.example.ui.familyledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.TransactionType
import com.example.ui.screens.AnalyticsTimeRange
import com.example.ui.theme.*
import java.util.*

@Composable
fun FamilyLedgerAnalyticsView(
    state: FamilyLedgerUiState,
    currencySymbol: String = "₹"
) {
    var selectedRange by remember { mutableStateOf(AnalyticsTimeRange.THIS_MONTH) }

    val filteredTransactions = remember(state.transactions, selectedRange) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        when (selectedRange) {
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
                state.transactions.filter { it.dateMillis >= cal.timeInMillis }
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
    val netBalance = rangeIncome - rangeExpense

    val categoryBreakdown = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val memberSpendingBreakdown = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.paidByName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val palette = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF59E0B), // Amber
        Color(0xFF10B981), // Emerald
        Color(0xFFEC4899), // Pink
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEF4444)  // Red
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. TIME RANGE SELECTOR ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsTimeRange.values().forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { selectedRange = range },
                        label = { Text(range.label, fontSize = 12.sp, fontWeight = if (selectedRange == range) FontWeight.Bold else FontWeight.Medium) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldDarkPrimary.copy(alpha = 0.25f),
                            selectedLabelColor = EmeraldDarkPrimary
                        )
                    )
                }
            }
        }

        // --- 2. SUMMARY HERO CARD ---
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "TOTAL FAMILY OUTFLOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextMuted,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$currencySymbol%,.2f".format(rangeExpense),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = SlateDarkTextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IncomeGreenContainer.copy(alpha = 0.25f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Inflow", fontSize = 10.sp, color = SlateDarkTextSecondary)
                                Text("+$currencySymbol%,.0f".format(rangeIncome), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (netBalance >= 0) IncomeGreenContainer.copy(alpha = 0.25f) else ExpenseRedContainer.copy(alpha = 0.25f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Net Surplus", fontSize = 10.sp, color = SlateDarkTextSecondary)
                                Text(
                                    "${if (netBalance >= 0) "+" else ""}$currencySymbol%,.0f".format(netBalance),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (netBalance >= 0) IncomeGreen else PastelRose
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. MEMBER CONTRIBUTION BREAKDOWN ---
        item {
            Text(
                text = "MEMBER SPENDING BREAKDOWN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SlateDarkTextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (memberSpendingBreakdown.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text("No spending recorded in this period.", fontSize = 13.sp, color = SlateDarkTextSecondary)
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        memberSpendingBreakdown.forEachIndexed { index, (payer, amount) ->
                            val pct = if (rangeExpense > 0) (amount / rangeExpense) else 0.0
                            val pctInt = (pct * 100).toInt()
                            val color = palette[index % palette.size]

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(payer, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                                    }
                                    Text(
                                        "$currencySymbol%,.0f ($pctInt%)".format(amount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateDarkTextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { pct.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = color,
                                    trackColor = SlateDarkSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 4. CATEGORY BREAKDOWN ---
        item {
            Text(
                text = "CATEGORY DISTRIBUTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SlateDarkTextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (categoryBreakdown.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text("No category data in this period.", fontSize = 13.sp, color = SlateDarkTextSecondary)
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        categoryBreakdown.forEachIndexed { index, (category, amount) ->
                            val pct = if (rangeExpense > 0) (amount / rangeExpense) else 0.0
                            val pctInt = (pct * 100).toInt()
                            val color = palette[(index + 1) % palette.size]

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(category, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SlateDarkTextPrimary)
                                    Text(
                                        "$currencySymbol%,.0f ($pctInt%)".format(amount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateDarkTextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { pct.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = color,
                                    trackColor = SlateDarkSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
