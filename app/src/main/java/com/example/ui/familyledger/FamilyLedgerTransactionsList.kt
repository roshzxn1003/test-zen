package com.example.ui.familyledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.example.data.familyledger.LedgerTransaction
import com.example.data.models.TransactionType
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyLedgerTransactionsList(
    state: FamilyLedgerUiState,
    currencySymbol: String = "₹",
    onSearchChange: (String) -> Unit,
    onFilterTypeChange: (TransactionType?) -> Unit,
    onFilterCategoryChange: (String?) -> Unit,
    onFilterMemberChange: (String?) -> Unit,
    onSortChange: (TransactionSortOption) -> Unit,
    onOpenAddTransaction: () -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
    onDeleteTransaction: (LedgerTransaction) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val filteredTransactions = remember(
        state.transactions,
        state.searchQuery,
        state.filterType,
        state.filterCategory,
        state.filterMemberId,
        state.sortBy
    ) {
        state.transactions.filter { tx ->
            val matchesQuery = state.searchQuery.isBlank() ||
                    tx.title.contains(state.searchQuery, ignoreCase = true) ||
                    tx.category.contains(state.searchQuery, ignoreCase = true) ||
                    tx.paidByName.contains(state.searchQuery, ignoreCase = true) ||
                    tx.description.contains(state.searchQuery, ignoreCase = true)

            val matchesType = state.filterType == null || tx.type == state.filterType
            val matchesCategory = state.filterCategory == null || tx.category.equals(state.filterCategory, ignoreCase = true)
            val matchesMember = state.filterMemberId == null ||
                    tx.paidByMemberId == state.filterMemberId ||
                    tx.paidByName.equals(state.filterMemberId, ignoreCase = true)

            matchesQuery && matchesType && matchesCategory && matchesMember
        }.let { list ->
            when (state.sortBy) {
                TransactionSortOption.NEWEST_FIRST -> list.sortedByDescending { it.dateMillis }
                TransactionSortOption.OLDEST_FIRST -> list.sortedBy { it.dateMillis }
                TransactionSortOption.HIGHEST_AMOUNT -> list.sortedByDescending { it.amount }
                TransactionSortOption.LOWEST_AMOUNT -> list.sortedBy { it.amount }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // --- 1. SEARCH BAR & SORT BUTTON ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search transactions, members...", fontSize = 13.sp, color = SlateDarkTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateDarkTextMuted, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateDarkTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldDarkPrimary,
                    unfocusedBorderColor = GlassBorderColor,
                    focusedContainerColor = SlateDarkSurface.copy(alpha = 0.7f),
                    unfocusedContainerColor = SlateDarkSurface.copy(alpha = 0.7f),
                    focusedTextColor = SlateDarkTextPrimary,
                    unfocusedTextColor = SlateDarkTextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("input_ledger_search"),
                singleLine = true
            )

            // Sort Menu
            Box {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SlateDarkSurface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { sortMenuExpanded = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = PastelIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    TransactionSortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    fontWeight = if (state.sortBy == option) FontWeight.Bold else FontWeight.Normal,
                                    color = if (state.sortBy == option) EmeraldDarkPrimary else SlateDarkTextPrimary
                                )
                            },
                            onClick = {
                                onSortChange(option)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. FILTER PILLS: ALL / EXPENSE / INCOME ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.filterType == null,
                onClick = { onFilterTypeChange(null) },
                label = { Text("All (${state.transactions.size})", fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldDarkPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = EmeraldDarkPrimary
                )
            )
            FilterChip(
                selected = state.filterType == TransactionType.EXPENSE,
                onClick = { onFilterTypeChange(TransactionType.EXPENSE) },
                label = { Text("Expenses", fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ExpenseRedContainer.copy(alpha = 0.4f),
                    selectedLabelColor = PastelRose
                )
            )
            FilterChip(
                selected = state.filterType == TransactionType.INCOME,
                onClick = { onFilterTypeChange(TransactionType.INCOME) },
                label = { Text("Income", fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IncomeGreenContainer.copy(alpha = 0.4f),
                    selectedLabelColor = IncomeGreen
                )
            )
        }

        // --- 3. FILTER PILLS: MEMBERS ---
        if (state.members.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = state.filterMemberId == null,
                        onClick = { onFilterMemberChange(null) },
                        label = { Text("All Payers", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                items(state.members) { member ->
                    FilterChip(
                        selected = state.filterMemberId == member.name,
                        onClick = {
                            if (state.filterMemberId == member.name) onFilterMemberChange(null)
                            else onFilterMemberChange(member.name)
                        },
                        label = { Text(member.name, fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 4. TRANSACTIONS LIST ---
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = SlateDarkTextMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No transactions found matching your filters.",
                        fontSize = 13.sp,
                        color = SlateDarkTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTransactions, key = { it.transactionId }) { tx ->
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
}
