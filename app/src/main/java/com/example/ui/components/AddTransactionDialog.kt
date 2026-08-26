package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.CategoryEntity
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.data.models.TransactionType
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    categories: List<CategoryEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onAdd: (title: String, amount: Double, type: TransactionType, category: String, dateMillis: Long, note: String, paymentMethod: String, memberId: String?, scope: FinanceScope) -> Unit,
    familyMembers: List<FamilyMemberEntity> = emptyList(),
    currentFinanceScope: FinanceScope = FinanceScope.PERSONAL,
    currentUserId: String = "",
    currentUserName: String = "",
    familyName: String = "Zenith Family"
) {
    var selectedScope by remember { mutableStateOf(currentFinanceScope) }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf("Food & Dining") }
    var selectedPaymentMethod by remember { mutableStateOf("UPI") }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var showCategoryPickerSheet by remember { mutableStateOf(false) }
    var showScopeDropdown by remember { mutableStateOf(false) }
    var showMemberDropdown by remember { mutableStateOf(false) }

    val todayFormatted = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    val fullCategoryList = listOf(
        "Food & Dining" to Icons.Default.Restaurant,
        "Transportation" to Icons.Default.DirectionsCar,
        "Shopping" to Icons.Default.ShoppingBag,
        "Bills & Utilities" to Icons.Default.Receipt,
        "Entertainment" to Icons.Default.Movie,
        "Healthcare" to Icons.Default.MedicalServices,
        "Housing & Rent" to Icons.Default.Home,
        "Education" to Icons.Default.School,
        "Subscriptions" to Icons.Default.Subscriptions,
        "Personal Care" to Icons.Default.Spa,
        "Travel" to Icons.Default.Flight,
        "Salary & Income" to Icons.Default.Payments,
        "Freelance / Business" to Icons.Default.Work,
        "Other" to Icons.Default.Category
    )

    val selectedMemberName = remember(selectedMemberId, familyMembers) {
        if (selectedMemberId == null) "Me (${currentUserName.ifBlank { "You" }})"
        else familyMembers.find { it.userId == selectedMemberId }?.name ?: "Me"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("add_transaction_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedScope == FinanceScope.FAMILY) "Add Family Entry" else "Add Transaction",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = todayFormatted,
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_add_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 1. SCOPE SELECTOR (Personal vs Family) ---
                Text(
                    text = "Transaction Scope",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateDarkTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SlateDarkSurfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showScopeDropdown = true }
                        .testTag("scope_dropdown_trigger")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedScope == FinanceScope.FAMILY) Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                        else EmeraldDarkPrimary.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedScope == FinanceScope.FAMILY) Icons.Default.Groups else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (selectedScope == FinanceScope.FAMILY) Color(0xFFC4B5FD) else EmeraldDarkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = if (selectedScope == FinanceScope.FAMILY) "Family Ledger" else "Personal Space",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = SlateDarkTextSecondary
                        )
                    }
                }

                // Family Details (Only when Family Scope is active)
                AnimatedVisibility(visible = selectedScope == FinanceScope.FAMILY) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        // Active Family Card / Tag
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Family: $familyName", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE9D5FF))
                                }
                                Text("Shared Vault", fontSize = 10.sp, color = Color(0xFFC4B5FD), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Recorded For Member Selector
                        Text(
                            text = "Recorded For",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateDarkTextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SlateDarkSurfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, GlassBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showMemberDropdown = true }
                                .testTag("member_dropdown_trigger")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = selectedMemberName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SlateDarkTextPrimary
                                    )
                                }

                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SlateDarkTextSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 2. EXPENSE / INCOME SEGMENTED SWITCH ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateDarkSurfaceVariant, RoundedCornerShape(14.dp))
                        .padding(4.dp)
                ) {
                    val isExpense = selectedType == TransactionType.EXPENSE
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(if (isExpense) ExpenseRed else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { selectedType = TransactionType.EXPENSE }
                            .testTag("type_expense_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Expense", fontWeight = FontWeight.Bold, color = if (isExpense) Color.White else SlateDarkTextSecondary, fontSize = 13.sp)
                    }

                    val isIncome = selectedType == TransactionType.INCOME
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(if (isIncome) IncomeGreen else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                selectedType = TransactionType.INCOME
                                if (selectedCategory == "Food & Dining") selectedCategory = "Salary & Income"
                            }
                            .testTag("type_income_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Income", fontWeight = FontWeight.Bold, color = if (isIncome) Color.White else SlateDarkTextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 3. AMOUNT FIELD ---
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) {
                            amountText = clean
                        }
                    },
                    label = { Text("Amount ($currencySymbol)") },
                    placeholder = { Text("0.00", color = SlateDarkTextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (selectedType == TransactionType.EXPENSE) ExpenseRed else IncomeGreen,
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant,
                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("add_transaction_amount_field"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // --- 4. TITLE FIELD ---
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Description") },
                    placeholder = { Text("e.g. Grocery, Lunch, Salary", color = SlateDarkTextMuted) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldDarkPrimary,
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant,
                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("add_transaction_title_field"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // --- 5. CATEGORY SELECTOR ---
                Text(
                    text = "Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SlateDarkTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SlateDarkSurfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showCategoryPickerSheet = true }
                        .testTag("category_picker_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldDarkPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val catIcon = fullCategoryList.firstOrNull { it.first == selectedCategory }?.second ?: Icons.Default.Category
                                Icon(catIcon, contentDescription = null, tint = EmeraldDarkPrimary, modifier = Modifier.size(16.dp))
                            }

                            Text(
                                text = selectedCategory,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                        }

                        Text("Change ▾", fontSize = 12.sp, color = EmeraldDarkPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- 6. UNIFIED PAYMENT METHOD DROPDOWN ---
                PaymentMethodDropdown(
                    selectedMethod = selectedPaymentMethod,
                    onMethodSelected = { selectedPaymentMethod = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // --- 7. NOTE FIELD ---
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("Additional details...", color = SlateDarkTextMuted) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldDarkPrimary,
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant,
                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )

                Spacer(modifier = Modifier.height(22.dp))

                // --- 8. SAVE BUTTON ---
                val isValid = (amountText.toDoubleOrNull() ?: 0.0) > 0 && title.isNotBlank()

                Button(
                    onClick = {
                        val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                        if (isValid) {
                            onAdd(
                                title.trim(),
                                parsedAmount,
                                selectedType,
                                selectedCategory,
                                System.currentTimeMillis(),
                                note.trim(),
                                selectedPaymentMethod,
                                if (selectedScope == FinanceScope.FAMILY) selectedMemberId else null,
                                selectedScope
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_transaction_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == TransactionType.EXPENSE) ExpenseRed else IncomeGreen
                    ),
                    enabled = isValid
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedType == TransactionType.EXPENSE) "Save Expense" else "Save Income",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // --- SCOPE SELECTOR MODAL ---
    if (showScopeDropdown) {
        Dialog(onDismissRequest = { showScopeDropdown = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Select Scope", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Personal Option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedScope == FinanceScope.PERSONAL) EmeraldDarkPrimary.copy(alpha = 0.15f) else Color.Transparent,
                        border = if (selectedScope == FinanceScope.PERSONAL) BorderStroke(1.dp, EmeraldDarkPrimary.copy(alpha = 0.4f)) else null,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            selectedScope = FinanceScope.PERSONAL
                            showScopeDropdown = false
                        }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldDarkPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Personal Space", fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary, fontSize = 14.sp)
                                Text("Private to your account only", fontSize = 11.sp, color = SlateDarkTextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Family Option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedScope == FinanceScope.FAMILY) Color(0xFF8B5CF6).copy(alpha = 0.15f) else Color.Transparent,
                        border = if (selectedScope == FinanceScope.FAMILY) BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)) else null,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            selectedScope = FinanceScope.FAMILY
                            showScopeDropdown = false
                        }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFFC4B5FD))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Family Ledger", fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary, fontSize = 14.sp)
                                Text("Shared with $familyName members", fontSize = 11.sp, color = SlateDarkTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- MEMBER SELECTOR MODAL ---
    if (showMemberDropdown) {
        Dialog(onDismissRequest = { showMemberDropdown = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SlateDarkSurface,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Select Member", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Self
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedMemberId == null) EmeraldDarkPrimary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            selectedMemberId = null
                            showMemberDropdown = false
                        }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldDarkPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Me (${currentUserName.ifBlank { "You" }})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                        }
                    }

                    familyMembers.forEach { member ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedMemberId == member.userId) EmeraldDarkPrimary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                selectedMemberId = member.userId
                                showMemberDropdown = false
                            }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = GoldAccent)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(member.name, fontSize = 14.sp, color = SlateDarkTextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- CATEGORY PICKER MODAL ---
    if (showCategoryPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryPickerSheet = false },
            containerColor = SlateDarkSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Select Category",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    items(fullCategoryList) { (catName, icon) ->
                        val isSelected = selectedCategory == catName
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) EmeraldDarkPrimary.copy(alpha = 0.2f) else SlateDarkSurfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) EmeraldDarkPrimary else GlassBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable {
                                    selectedCategory = catName
                                    showCategoryPickerSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = if (isSelected) EmeraldDarkPrimary else SlateDarkTextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = catName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) EmeraldDarkPrimary else SlateDarkTextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
