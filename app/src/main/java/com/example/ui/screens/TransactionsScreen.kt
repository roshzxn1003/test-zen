package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.TransactionEntity
import com.example.data.models.TransactionType
import com.example.ui.components.GlassCard
import com.example.ui.components.TransactionDetailModal
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowUiState
import java.util.*

@Composable
fun TransactionsScreen(
    state: CashFlowUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterTypeChange: (TransactionType?) -> Unit,
    onFilterCategoryChange: (String?) -> Unit,
    onFilterMemberChange: (String?) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onOpenAddTransaction: () -> Unit,
    onOpenVoiceAssistant: (() -> Unit)? = null,
    onUpdateTransaction: ((TransactionEntity) -> Unit)? = null,
    familyMembers: List<FamilyMemberEntity> = emptyList()
) {
    var selectedTransactionForDetails by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // --- 1. TITLE & QUICK ADD ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Activity",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SlateDarkTextPrimary
                )
                Text(
                    text = "${state.transactions.size} records • Tap & hold for details",
                    fontSize = 12.sp,
                    color = SlateDarkTextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenVoiceAssistant != null) {
                    IconButton(onClick = { onOpenVoiceAssistant.invoke() }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Assistant", tint = EmeraldDarkPrimary)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassCardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor)
                ) {
                    Text(
                        text = "${state.transactions.size} entries",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDarkPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 2. GLASS SEARCH BAR ---
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search title, category, payment method or note...", fontSize = 12.sp, color = SlateDarkTextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateDarkTextSecondary) },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = SlateDarkTextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = GlassBorderColor,
                focusedBorderColor = EmeraldDarkPrimary,
                unfocusedContainerColor = GlassCardBg,
                focusedContainerColor = GlassCardBg,
                focusedTextColor = SlateDarkTextPrimary,
                unfocusedTextColor = SlateDarkTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().testTag("search_transactions_input"),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- 3. TYPE FILTER CHIPS (All, Expenses, Income) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.selectedFilterType == null,
                onClick = { onFilterTypeChange(null) },
                label = { Text("All", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.testTag("filter_all_chip")
            )
            FilterChip(
                selected = state.selectedFilterType == TransactionType.EXPENSE,
                onClick = { onFilterTypeChange(TransactionType.EXPENSE) },
                label = { Text("Expenses", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.testTag("filter_expense_chip")
            )
            FilterChip(
                selected = state.selectedFilterType == TransactionType.INCOME,
                onClick = { onFilterTypeChange(TransactionType.INCOME) },
                label = { Text("Income", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.testTag("filter_income_chip")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 4. CATEGORY HORIZONTAL ROW ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = state.selectedFilterCategory == null,
                    onClick = { onFilterCategoryChange(null) },
                    label = { Text("All Categories", fontSize = 11.sp) }
                )
            }
            items(state.categories, key = { "cat_${it.id}" }) { cat ->
                FilterChip(
                    selected = state.selectedFilterCategory == cat.name,
                    onClick = {
                        if (state.selectedFilterCategory == cat.name) onFilterCategoryChange(null)
                        else onFilterCategoryChange(cat.name)
                    },
                    label = { Text(cat.name, fontSize = 11.sp) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // --- 5. MEMBER FILTER ROW (If Family Members present) ---
        if (familyMembers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = state.selectedFilterMember == null,
                        onClick = { onFilterMemberChange(null) },
                        label = { Text("All Members", fontSize = 11.sp) }
                    )
                }
                items(familyMembers, key = { "member_${it.id}" }) { member ->
                    FilterChip(
                        selected = state.selectedFilterMember == member.userId,
                        onClick = {
                            if (state.selectedFilterMember == member.userId) onFilterMemberChange(null)
                            else onFilterMemberChange(member.userId)
                        },
                        label = { Text("👤 ${member.name}", fontSize = 11.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 6. TRANSACTION LIST ---
        if (state.transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    backgroundColor = GlassCardBg
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = SlateDarkTextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No transactions found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Your financial activity will appear here.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenAddTransaction,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add Transaction", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                items(state.transactions, key = { "tx_${it.id}" }) { tx ->
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

    // Confirm Delete Transaction Dialog
    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction", color = SlateDarkTextPrimary) },
            text = { Text("Are you sure you want to delete '${tx.title}' (${state.currencySymbol}${String.format(Locale.US, "%.2f", tx.amount)})?", color = SlateDarkTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction(tx)
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateDarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
