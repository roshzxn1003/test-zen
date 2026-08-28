package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.BudgetEntity
import com.example.data.models.SavingsGoalEntity
import com.example.data.models.TransactionType
import com.example.ui.components.CleanCard
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowUiState
import java.util.*

@Composable
fun BudgetsAndGoalsScreen(
    state: CashFlowUiState,
    onSaveBudget: (categoryName: String, limit: Double, periodType: String, customPeriodName: String, budgetId: Long) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    onSaveSavingsGoal: (SavingsGoalEntity) -> Unit,
    onDeleteSavingsGoal: (SavingsGoalEntity) -> Unit,
    onDepositToGoal: (SavingsGoalEntity, Double) -> Unit
) {
    var showAddBudgetModal by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var budgetToDelete by remember { mutableStateOf<BudgetEntity?>(null) }

    var showAddGoalModal by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var goalToDelete by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var selectedGoalForDeposit by remember { mutableStateOf<SavingsGoalEntity?>(null) }

    var selectedPeriodFilter by remember { mutableStateOf("ALL") }

    val filteredBudgets = remember(state.budgets, selectedPeriodFilter) {
        when (selectedPeriodFilter) {
            "MONTHLY" -> state.budgets.filter { it.periodType == "MONTHLY" }
            "WEEKLY" -> state.budgets.filter { it.periodType == "WEEKLY" }
            "CUSTOM" -> state.budgets.filter { it.periodType == "CUSTOM" || it.periodType == "YEARLY" }
            else -> state.budgets
        }
    }

    val sdfMonth = remember { java.text.SimpleDateFormat("yyyy-MM", Locale.getDefault()) }
    val sdfYear = remember { java.text.SimpleDateFormat("yyyy", Locale.getDefault()) }

    // Helper to calculate spent for a specific budget
    val calculateSpent = { budget: BudgetEntity, txs: List<com.example.data.models.TransactionEntity> ->
        txs.filter { it.type == TransactionType.EXPENSE && it.category == budget.categoryName }
            .filter { tx ->
                val date = Date(tx.dateMillis)
                when (budget.periodType) {
                    "MONTHLY" -> budget.monthYear.isNotBlank() && sdfMonth.format(date) == budget.monthYear
                    "YEARLY" -> budget.monthYear.length >= 4 && sdfYear.format(date) == budget.monthYear.take(4)
                    else -> true
                }
            }
            .sumOf { it.amount }
    }

    val totalBudgeted = remember(filteredBudgets) { filteredBudgets.sumOf { it.monthlyLimit } }
    val totalSpentOnBudgets = remember(filteredBudgets, state.transactions) {
        filteredBudgets.sumOf { budget -> calculateSpent(budget, state.transactions) }
    }
    val totalRemaining = totalBudgeted - totalSpentOnBudgets
    val overallProgress = if (totalBudgeted > 0) (totalSpentOnBudgets / totalBudgeted).toFloat().coerceIn(0f, 1f) else 0f
    val overbudgetCount = remember(filteredBudgets, state.transactions) {
        filteredBudgets.count { budget -> calculateSpent(budget, state.transactions) > budget.monthlyLimit }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp)
    ) {
        // --- 1. BUDGET HEALTH OVERVIEW CLEAN CARD ---
        item {
            CleanCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                backgroundColor = SlateDarkSurface.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Budget Overview",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Tracking ${state.budgets.size} active spending limits",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary
                        )
                    }

                    AnimatedVisibility(visible = overbudgetCount > 0) {
                        Surface(
                            color = PastelRoseContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PastelRose.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = PastelRose,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$overbudgetCount Over",
                                    color = PastelRose,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Total Spent",
                            fontSize = 11.sp,
                            color = SlateDarkTextSecondary
                        )
                        Text(
                            text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", totalSpentOnBudgets)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalSpentOnBudgets > totalBudgeted && totalBudgeted > 0) PastelRose else SlateDarkTextPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Budgeted",
                            fontSize = 11.sp,
                            color = SlateDarkTextSecondary
                        )
                        Text(
                            text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", totalBudgeted)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = when {
                        overallProgress >= 1.0f -> PastelRose
                        overallProgress >= 0.8f -> PastelAmber
                        else -> PastelGreen
                    },
                    trackColor = SlateDarkSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (totalRemaining >= 0) "Remaining: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", totalRemaining)}"
                        else "Over budget by ${state.currencySymbol}${String.format(Locale.US, "%,.2f", -totalRemaining)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (totalRemaining < 0) PastelRose else PastelGreen
                    )

                    Text(
                        text = "${(overallProgress * 100).toInt()}% Used",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextPrimary
                    )
                }
            }
        }

        // --- 2. CATEGORY BUDGETS HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Category Budgets",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextPrimary
                    )
                    Text(
                        text = "Set spending limits for monthly or custom periods",
                        fontSize = 12.sp,
                        color = SlateDarkTextSecondary
                    )
                }

                Button(
                    onClick = {
                        editingBudget = null
                        showAddBudgetModal = true
                    },
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("add_budget_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Budget", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontally Scrollable Period Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedPeriodFilter == "ALL",
                        onClick = { selectedPeriodFilter = "ALL" },
                        label = { Text("All (${state.budgets.size})", fontSize = 12.sp) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedPeriodFilter == "MONTHLY",
                        onClick = { selectedPeriodFilter = "MONTHLY" },
                        label = { Text("Monthly", fontSize = 12.sp) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedPeriodFilter == "WEEKLY",
                        onClick = { selectedPeriodFilter = "WEEKLY" },
                        label = { Text("Weekly", fontSize = 12.sp) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedPeriodFilter == "CUSTOM",
                        onClick = { selectedPeriodFilter = "CUSTOM" },
                        label = { Text("Custom", fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // --- 3. BUDGET ITEMS LIST ---
        if (filteredBudgets.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    backgroundColor = GlassCardBg
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PieChart,
                            contentDescription = null,
                            tint = SlateDarkTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No budgets yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Set your first spending limit to start controlling expenses.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(filteredBudgets, key = { "budget_${it.id}" }) { budget ->
                val categorySpent = remember(state.transactions, budget) {
                    calculateSpent(budget, state.transactions)
                }

                val remaining = budget.monthlyLimit - categorySpent
                val progress = if (budget.monthlyLimit > 0) (categorySpent / budget.monthlyLimit).toFloat().coerceIn(0f, 1f) else 0f
                val isOver = categorySpent > budget.monthlyLimit
                val isNearLimit = progress >= 0.8f && !isOver

                val periodLabel = when (budget.periodType) {
                    "WEEKLY" -> "Weekly"
                    "YEARLY" -> "Yearly"
                    "CUSTOM" -> if (budget.customPeriodName.isNotBlank()) budget.customPeriodName else "Custom"
                    else -> "Monthly"
                }

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .animateItem(),
                    backgroundColor = if (isOver) ExpenseRed.copy(alpha = 0.05f) else GlassCardBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = budget.categoryName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = SlateDarkSurfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = periodLabel,
                                        fontSize = 10.sp,
                                        color = SlateDarkTextSecondary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Status Badge
                                Surface(
                                    color = when {
                                        isOver -> ExpenseRed.copy(alpha = 0.15f)
                                        isNearLimit -> GoalAmber.copy(alpha = 0.15f)
                                        else -> IncomeGreen.copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isOver -> "Over Budget"
                                            isNearLimit -> "Near Limit"
                                            else -> "Healthy"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isOver -> ExpenseRed
                                            isNearLimit -> GoalAmber
                                            else -> IncomeGreen
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (isOver) {
                                Text(
                                    text = "Over limit by ${state.currencySymbol}${String.format(Locale.US, "%.2f", categorySpent - budget.monthlyLimit)}!",
                                    fontSize = 12.sp,
                                    color = ExpenseRed,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "${state.currencySymbol}${String.format(Locale.US, "%.2f", remaining)} left of ${state.currencySymbol}${String.format(Locale.US, "%.2f", budget.monthlyLimit)} limit",
                                    fontSize = 12.sp,
                                    color = SlateDarkTextSecondary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    editingBudget = budget
                                    showAddBudgetModal = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = EmeraldDarkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { budgetToDelete = budget },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = ExpenseRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Spent: ${state.currencySymbol}${String.format(Locale.US, "%.2f", categorySpent)}",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary,
                            maxLines = 1
                        )
                        Text(
                            text = "${(progress * 100).toInt()}% Used",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOver) ExpenseRed else EmeraldDarkPrimary,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (isOver) ExpenseRed else EmeraldDarkPrimary,
                        trackColor = SlateDarkSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- 4. SAVINGS GOALS SECTION ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Savings Goals",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Track your savings progress towards big purchases",
                        fontSize = 12.sp,
                        color = SlateDarkTextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        editingGoal = null
                        showAddGoalModal = true
                    },
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("add_goal_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Goal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        if (state.savingsGoals.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    backgroundColor = GlassCardBg
                ) {
                    Text(
                        text = "No savings goals added yet. Click 'New Goal' to set target savings milestones!",
                        fontSize = 13.sp,
                        color = SlateDarkTextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(state.savingsGoals, key = { "goal_${it.id}" }) { goal ->
                val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                val percentInt = (progress * 100).toInt()

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .animateItem(),
                    backgroundColor = GlassCardBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldDarkContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = EmeraldDarkPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = goal.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$percentInt% Achieved",
                                    fontSize = 12.sp,
                                    color = EmeraldDarkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedGoalForDeposit = goal },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("+ Deposit", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            IconButton(
                                onClick = {
                                    editingGoal = goal
                                    showAddGoalModal = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = EmeraldDarkPrimary, modifier = Modifier.size(15.dp))
                            }

                            IconButton(
                                onClick = { goalToDelete = goal },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRed.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Saved: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", goal.currentAmount)}",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary
                        )
                        Text(
                            "Target: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", goal.targetAmount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateDarkTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = EmeraldDarkPrimary,
                        trackColor = SlateDarkSurfaceVariant
                    )
                }
            }
        }
    }

    // --- MODALS & DIALOGS ---

    // Add or Edit Budget Modal
    if (showAddBudgetModal) {
        AddOrEditBudgetModal(
            categories = state.categories,
            existingBudget = editingBudget,
            currencySymbol = state.currencySymbol,
            onDismiss = {
                showAddBudgetModal = false
                editingBudget = null
            },
            onSave = { category, limit, periodType, customName ->
                onSaveBudget(
                    category,
                    limit,
                    periodType,
                    customName,
                    editingBudget?.id ?: 0L
                )
                showAddBudgetModal = false
                editingBudget = null
            }
        )
    }

    // Confirm Delete Budget
    budgetToDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("Delete Budget") },
            text = { Text("Are you sure you want to delete the budget for '${budget.categoryName}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBudget(budget)
                        budgetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { budgetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add or Edit Savings Goal Modal
    if (showAddGoalModal) {
        AddOrEditGoalModal(
            currencySymbol = state.currencySymbol,
            existingGoal = editingGoal,
            onDismiss = {
                showAddGoalModal = false
                editingGoal = null
            },
            onSave = { title, target, current ->
                val goal = editingGoal?.copy(
                    title = title,
                    targetAmount = target,
                    currentAmount = current
                ) ?: SavingsGoalEntity(
                    title = title,
                    targetAmount = target,
                    currentAmount = current
                )
                onSaveSavingsGoal(goal)
                showAddGoalModal = false
                editingGoal = null
            }
        )
    }

    // Confirm Delete Goal
    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("Delete Goal") },
            text = { Text("Are you sure you want to delete the goal '${goal.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSavingsGoal(goal)
                        goalToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Deposit to Goal Modal
    selectedGoalForDeposit?.let { goal ->
        DepositGoalModal(
            goal = goal,
            currencySymbol = state.currencySymbol,
            onDismiss = { selectedGoalForDeposit = null },
            onDeposit = { amount ->
                onDepositToGoal(goal, amount)
                selectedGoalForDeposit = null
            }
        )
    }
}

@Composable
fun AddOrEditBudgetModal(
    categories: List<com.example.data.models.CategoryEntity>,
    existingBudget: BudgetEntity?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (categoryName: String, limit: Double, periodType: String, customPeriodName: String) -> Unit
) {
    var selectedCat by remember {
        mutableStateOf(existingBudget?.categoryName ?: categories.firstOrNull()?.name ?: "Food & Dining")
    }
    var limitText by remember {
        mutableStateOf(existingBudget?.monthlyLimit?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var selectedPeriodType by remember {
        mutableStateOf(existingBudget?.periodType ?: "MONTHLY")
    }
    var customPeriodTitle by remember {
        mutableStateOf(existingBudget?.customPeriodName ?: "")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (existingBudget == null) "Create Budget Limit" else "Edit Budget Limit",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextPrimary
                )
                Text(
                    text = "Track and limit spending for a category",
                    fontSize = 12.sp,
                    color = SlateDarkTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Budget Limit ($currencySymbol)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Budgeting Period", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("MONTHLY" to "Monthly", "WEEKLY" to "Weekly", "CUSTOM" to "Custom").forEach { (typeKey, label) ->
                        FilterChip(
                            selected = selectedPeriodType == typeKey,
                            onClick = { selectedPeriodType = typeKey },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                if (selectedPeriodType == "CUSTOM") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customPeriodTitle,
                        onValueChange = { customPeriodTitle = it },
                        label = { Text("Custom Period Title (e.g. Vacation, Q3)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Select Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Column {
                    categories.take(6).forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCat = cat.name }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCat == cat.name,
                                onClick = { selectedCat = cat.name }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name, fontSize = 14.sp, color = SlateDarkTextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val limit = limitText.toDoubleOrNull() ?: 0.0
                        if (limit > 0) {
                            onSave(selectedCat, limit, selectedPeriodType, customPeriodTitle)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_budget_modal_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Text(if (existingBudget == null) "Save Budget" else "Update Budget", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddOrEditGoalModal(
    currencySymbol: String,
    existingGoal: SavingsGoalEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, targetAmount: Double, currentAmount: Double) -> Unit
) {
    var title by remember { mutableStateOf(existingGoal?.title ?: "") }
    var targetText by remember { mutableStateOf(existingGoal?.targetAmount?.toString() ?: "") }
    var currentText by remember { mutableStateOf(existingGoal?.currentAmount?.toString() ?: "0") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (existingGoal == null) "Create Savings Goal" else "Edit Savings Goal",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal Title (e.g. Emergency Fund, Trip)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target Amount ($currencySymbol)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_target_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it },
                    label = { Text("Current Saved ($currencySymbol)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val target = targetText.toDoubleOrNull() ?: 0.0
                        val current = currentText.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && target > 0) onSave(title, target, current)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_goal_modal_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Text(if (existingGoal == null) "Create Goal" else "Update Goal", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DepositGoalModal(
    goal: SavingsGoalEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onDeposit: (Double) -> Unit
) {
    var depositText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Deposit to ${goal.title}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                Text(
                    "Current Saved: $currencySymbol${String.format(Locale.US, "%,.2f", goal.currentAmount)} / $currencySymbol${String.format(Locale.US, "%,.2f", goal.targetAmount)}",
                    fontSize = 12.sp,
                    color = SlateDarkTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = depositText,
                    onValueChange = { depositText = it },
                    label = { Text("Deposit Amount ($currencySymbol)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deposit_amount_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val amount = depositText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) onDeposit(amount)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_deposit_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Text("Add Funds", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
