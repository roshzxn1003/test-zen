package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payment
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.data.upi.UpiService
import com.example.ui.theme.*

@Composable
fun UPIPaySheet(
    currencySymbol: String,
    currentFinanceScope: FinanceScope,
    currentUserId: String,
    currentUserName: String,
    familyName: String,
    familyMembers: List<FamilyMemberEntity>,
    onDismiss: () -> Unit,
    onOpenScanQr: (() -> Unit)? = null,
    onSaveTransaction: (
        title: String,
        amount: Double,
        category: String,
        scope: FinanceScope,
        memberId: String?,
        upiId: String?,
        upiTransactionId: String?
    ) -> Unit
) {
    val context = LocalContext.current

    var amountText by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var vpaText by remember { mutableStateOf("") }

    var selectedScope by remember { mutableStateOf(currentFinanceScope) }
    var selectedCategory by remember { mutableStateOf("Food & Dining") }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }

    var payRequest by remember { mutableStateOf<UpiPayRequest?>(null) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val isValid = amount > 0 && purpose.isNotBlank() && UpiService.isValidVpa(vpaText)

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .imePadding()
                .testTag("upi_pay_sheet")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF06B6D4).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = null,
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pay via UPI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "Pay through an installed UPI app",
                                fontSize = 11.sp,
                                color = SlateDarkTextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) amountText = clean
                    },
                    label = { Text("Amount ($currencySymbol)") },
                    placeholder = { Text("0.00", color = SlateDarkTextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF06B6D4),
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant,
                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("upi_amount_field"),
                    singleLine = true
                )

                // Quick Amount Chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50, 100, 200, 500, 1000).forEach { chipAmount ->
                        SuggestionChip(
                            onClick = { amountText = chipAmount.toString() },
                            label = { Text("₹$chipAmount", fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = SlateDarkSurfaceVariant,
                                labelColor = SlateDarkTextPrimary
                            ),
                            border = BorderStroke(1.dp, GlassBorderColor),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Purpose / Merchant
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose / Merchant") },
                    placeholder = { Text("e.g. Food, Grocery, Swiggy", color = SlateDarkTextMuted) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF06B6D4),
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant,
                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("upi_purpose_field"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Payee UPI ID (VPA)
                OutlinedTextField(
                    value = vpaText,
                    onValueChange = { vpaText = it },
                    label = { Text("Payee UPI ID (VPA)") },
                    placeholder = { Text("e.g. merchant@upi or 10-digit number", color = SlateDarkTextMuted) },
                    isError = vpaText.isNotBlank() && !UpiService.isValidVpa(vpaText) && !(vpaText.length == 10 && vpaText.all { it.isDigit() }),
                    supportingText = {
                        if (vpaText.isNotBlank() && !UpiService.isValidVpa(vpaText) && !(vpaText.length == 10 && vpaText.all { it.isDigit() })) {
                            Text("Enter a valid UPI ID like name@bank", fontSize = 11.sp, color = ExpenseRed)
                        } else if (vpaText.length == 10 && vpaText.all { it.isDigit() }) {
                            Text("Phone number detected. Select a UPI bank handle below:", fontSize = 11.sp, color = Color(0xFF06B6D4))
                        }
                    },
                    trailingIcon = {
                        if (onOpenScanQr != null) {
                            IconButton(onClick = {
                                onDismiss()
                                onOpenScanQr()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR Code",
                                    tint = Color(0xFF06B6D4)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF06B6D4),
                        unfocusedBorderColor = GlassBorderColor,
                        focusedContainerColor = SlateDarkSurfaceVariant,
                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                        focusedTextColor = SlateDarkTextPrimary,
                        unfocusedTextColor = SlateDarkTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("upi_vpa_field"),
                    singleLine = true
                )

                // Phone number quick handle chips
                if (vpaText.length == 10 && vpaText.all { it.isDigit() }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("@okhdfcbank", "@okaxis", "@paytm", "@ybl").forEach { handle ->
                            SuggestionChip(
                                onClick = { vpaText = "$vpaText$handle" },
                                label = { Text(handle, fontSize = 10.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFF06B6D4).copy(alpha = 0.15f),
                                    labelColor = Color(0xFF06B6D4)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                UpiTransactionFields(
                    selectedScope = selectedScope,
                    onScopeSelected = { selectedScope = it },
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    selectedMemberId = selectedMemberId,
                    onMemberSelected = { selectedMemberId = it },
                    purpose = purpose,
                    familyName = familyName,
                    familyMembers = familyMembers,
                    currentUserName = currentUserName
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Pay button
                Button(
                    onClick = {
                        if (!UpiService.isValidVpa(vpaText)) {
                            Toast.makeText(context, "Please enter a valid UPI ID (VPA).", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (amount <= 0 || purpose.isBlank()) {
                            Toast.makeText(context, "Enter an amount and purpose.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        payRequest = UpiPayRequest(amount = amount, purpose = purpose, vpa = vpaText)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_pay_via_upi"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    enabled = isValid
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pay via UPI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Zenith opens your UPI app. Payment is confirmed in that app — Zenith never stores your UPI PIN or credentials.",
                    fontSize = 10.sp,
                    color = SlateDarkTextMuted,
                    lineHeight = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            }
        }
    }

    UpiPaymentFlow(
        payRequest = payRequest,
        currencySymbol = currencySymbol,
        onSaveConfirmed = { upiTransactionId ->
            val req = payRequest
            if (req != null) {
                onSaveTransaction(
                    req.purpose.trim(),
                    req.amount,
                    selectedCategory,
                    selectedScope,
                    if (selectedScope == FinanceScope.FAMILY) selectedMemberId else null,
                    req.vpa.trim().ifBlank { null },
                    upiTransactionId
                )
                onDismiss()
            }
        },
        onDismiss = onDismiss
    )
}
