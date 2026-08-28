package com.example.ui.familyledger

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.familyledger.FamilyVaultMember
import com.example.data.familyledger.LedgerTransaction
import com.example.data.models.TransactionType
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLedgerTransactionDialog(
    existing: LedgerTransaction? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    members: List<FamilyVaultMember>,
    currentUserId: String,
    currentUserName: String,
    currencySymbol: String = "₹",
    isSaving: Boolean = false,
    saveResult: String? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        amount: Double,
        category: String,
        type: TransactionType,
        paymentMethod: String,
        paidByMemberId: String,
        paidByName: String,
        dateMillis: Long
    ) -> Unit
) {
    val isEdit = existing != null
    val context = LocalContext.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var amountText by remember { mutableStateOf(if (existing != null) "%.2f".format(existing.amount) else "") }
    var selectedType by remember { mutableStateOf(existing?.type ?: initialType) }
    var selectedCategory by remember { mutableStateOf(existing?.category ?: "Food & Dining") }
    var selectedPaymentMethod by remember { mutableStateOf(existing?.paymentMethod ?: "UPI") }
    var selectedDateMillis by remember { mutableStateOf(existing?.dateMillis ?: System.currentTimeMillis()) }

    val defaultMember = remember(members, existing) {
        if (existing != null) {
            members.find { it.memberId == existing.paidByMemberId || it.userId == existing.paidByMemberId }
                ?: members.firstOrNull()
        } else {
            members.find { it.userId == currentUserId } ?: members.firstOrNull()
        }
    }
    var selectedMember by remember { mutableStateOf<FamilyVaultMember?>(defaultMember) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf(
            "Food & Dining",
            "Shopping",
            "Housing & Rent",
            "Transportation",
            "Bills & Utilities",
            "Entertainment",
            "Healthcare",
            "Salary & Income",
            "Freelance / Business",
            "Investments",
            "General"
        )
    }

    val paymentMethods = listOf("UPI", "Cash", "Card", "Bank Transfer")

    val formattedDate = remember(selectedDateMillis) {
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateMillis = newCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun validateAndSubmit() {
        val cleanTitle = title.trim()
        val parsedAmount = amountText.toDoubleOrNull()

        var hasError = false
        if (cleanTitle.isBlank()) {
            titleError = "Title cannot be empty"
            hasError = true
        } else {
            titleError = null
        }

        if (parsedAmount == null || parsedAmount <= 0.0) {
            amountError = "Enter a valid amount (> 0)"
            hasError = true
        } else {
            amountError = null
        }

        if (hasError) return

        val payerId = selectedMember?.memberId ?: selectedMember?.userId ?: currentUserId
        val payerName = selectedMember?.name ?: currentUserName

        onSave(
            cleanTitle,
            description.trim(),
            parsedAmount!!,
            selectedCategory,
            selectedType,
            selectedPaymentMethod,
            payerId,
            payerName,
            selectedDateMillis
        )
    }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .testTag("add_edit_ledger_transaction_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEdit) "Edit Entry" else "New Family Entry",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Syncs with all family members",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type Toggle (Expense / Income)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateDarkSurfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedType == TransactionType.EXPENSE) ExpenseRed else Color.Transparent)
                                .clickable { selectedType = TransactionType.EXPENSE },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Expense",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedType == TransactionType.EXPENSE) Color.White else SlateDarkTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedType == TransactionType.INCOME) IncomeGreen else Color.Transparent)
                                .clickable { selectedType = TransactionType.INCOME },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Income",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedType == TransactionType.INCOME) Color.White else SlateDarkTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        if (amountError != null) amountError = null
                    },
                    label = { Text("Amount ($currencySymbol)") },
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Text(
                            text = currencySymbol,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    isError = amountError != null,
                    supportingText = {
                        if (amountError != null) {
                            Text(amountError!!, color = PastelRose, fontSize = 11.sp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldDarkPrimary,
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_ledger_amount"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (titleError != null) titleError = null
                    },
                    label = { Text("Title / Purpose") },
                    placeholder = { Text("e.g. Groceries, Electricity") },
                    isError = titleError != null,
                    supportingText = {
                        if (titleError != null) {
                            Text(titleError!!, color = PastelRose, fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldDarkPrimary,
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_ledger_title"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Paid By Member Selector
                Text(
                    text = "PAID BY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = memberDropdownExpanded,
                    onExpandedChange = { memberDropdownExpanded = !memberDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedMember?.name ?: currentUserName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PastelIndigo)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldDarkPrimary,
                            unfocusedBorderColor = GlassBorderColor,
                            focusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                            focusedTextColor = SlateDarkTextPrimary,
                            unfocusedTextColor = SlateDarkTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = memberDropdownExpanded,
                        onDismissRequest = { memberDropdownExpanded = false }
                    ) {
                        if (members.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(currentUserName) },
                                onClick = { memberDropdownExpanded = false }
                            )
                        } else {
                            members.forEach { m ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(m.name, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = PastelIndigoContainer
                                            ) {
                                                Text(
                                                    text = m.role.name,
                                                    fontSize = 10.sp,
                                                    color = PastelIndigo,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedMember = m
                                        memberDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date & Payment Method Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date Button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, GlassBorderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable { openDatePicker() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = SlateDarkTextMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Date", fontSize = 10.sp, color = SlateDarkTextMuted)
                                Text(formattedDate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextPrimary, maxLines = 1)
                            }
                        }
                    }

                    // Payment Method Dropdown
                    var paymentMenuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, GlassBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable { paymentMenuExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = SlateDarkTextMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Method", fontSize = 10.sp, color = SlateDarkTextMuted)
                                        Text(selectedPaymentMethod, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextPrimary)
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SlateDarkTextMuted)
                            }
                        }

                        DropdownMenu(
                            expanded = paymentMenuExpanded,
                            onDismissRequest = { paymentMenuExpanded = false }
                        ) {
                            paymentMethods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method) },
                                    onClick = {
                                        selectedPaymentMethod = method
                                        paymentMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category Chips
                Text(
                    text = "CATEGORY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EmeraldDarkPrimary.copy(alpha = 0.2f) else GlassCardBgElevated,
                            border = BorderStroke(1.dp, if (isSelected) EmeraldDarkPrimary else GlassBorderColor),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) EmeraldDarkPrimary else SlateDarkTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Note / Description (Optional)") },
                    placeholder = { Text("Add extra details...") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldDarkPrimary,
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = SlateDarkSurfaceVariant.copy(alpha = 0.4f),
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Save Status Feedback
                if (saveResult != null) {
                    Text(
                        text = saveResult,
                        fontSize = 12.sp,
                        color = if (saveResult.contains("Failed")) PastelRose else IncomeGreen,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }

                // Submit Button
                Button(
                    onClick = { validateAndSubmit() },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldDarkPrimary,
                        disabledContainerColor = SlateDarkSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_save_ledger_transaction")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(if (isEdit) Icons.Default.Check else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEdit) "Update Entry" else "Save Entry", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
