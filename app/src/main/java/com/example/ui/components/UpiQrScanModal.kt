package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.data.upi.UpiService
import com.example.ui.theme.*

enum class UpiModalStep {
    REDIRECT_CHOOSER,
    ENTER_EXPENSE
}

@Composable
fun UpiQrScanModal(
    currencySymbol: String = "₹",
    currentFinanceScope: FinanceScope = FinanceScope.PERSONAL,
    currentUserName: String = "You",
    familyName: String = "Family Vault",
    familyMembers: List<FamilyMemberEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveTransaction: (
        title: String,
        amount: Double,
        category: String,
        scope: FinanceScope,
        memberId: String?,
        upiId: String?,
        upiTransactionId: String?
    ) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var modalStep by remember { mutableStateOf(UpiModalStep.REDIRECT_CHOOSER) }
    var awaitingUpiReturn by remember { mutableStateOf(false) }

    // Transaction form states for the "Enter Expense" step
    var enteredTitle by remember { mutableStateOf("UPI Payment") }
    var enteredAmount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food & Dining") }
    var selectedScope by remember { mutableStateOf(currentFinanceScope) }
    var selectedMemberId by remember { mutableStateOf(familyMembers.firstOrNull()?.id) }
    var amountError by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf(
            "Food & Dining",
            "Shopping",
            "Transportation",
            "Bills & Utilities",
            "Entertainment",
            "Housing & Rent",
            "Healthcare",
            "Other"
        )
    }

    val installedUpiApps = remember { UpiService.installedUpiApps(context) }
    val isGPayInstalled = remember { UpiService.isGooglePayInstalled(context) }
    val isPhonePeInstalled = remember { UpiService.isPhonePeInstalled(context) }
    val isPaytmInstalled = remember { UpiService.isPaytmInstalled(context) }
    val isBhimInstalled = remember { UpiService.isBhimInstalled(context) }

    // Listen for app resume after opening UPI app -> smoothly transition to enter expense
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingUpiReturn) {
                awaitingUpiReturn = false
                modalStep = UpiModalStep.ENTER_EXPENSE
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun launchUpiApp(targetPackage: String?) {
        try {
            awaitingUpiReturn = true
            val intent = UpiService.buildUpiScannerIntent(context, targetPackage)
            context.startActivity(intent)
        } catch (e: Exception) {
            awaitingUpiReturn = false
            Toast.makeText(context, "Could not open UPI app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
                .testTag("upi_qr_scan_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (modalStep == UpiModalStep.REDIRECT_CHOOSER)
                                        PastelCyan.copy(alpha = 0.18f)
                                    else
                                        IncomeGreen.copy(alpha = 0.18f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (modalStep == UpiModalStep.REDIRECT_CHOOSER)
                                    Icons.Default.Payment
                                else
                                    Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (modalStep == UpiModalStep.REDIRECT_CHOOSER) PastelCyan else IncomeGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (modalStep == UpiModalStep.REDIRECT_CHOOSER) "UPI Payment" else "Enter Expense Details",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = if (modalStep == UpiModalStep.REDIRECT_CHOOSER)
                                    "Redirect to your UPI app, then log here"
                                else
                                    "Record your completed UPI transaction",
                                fontSize = 11.5.sp,
                                color = SlateDarkTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateDarkTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                AnimatedContent(
                    targetState = modalStep,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "upi_step_transition"
                ) { currentStep ->
                    when (currentStep) {
                        UpiModalStep.REDIRECT_CHOOSER -> {
                            Column {
                                // Primary Action: Open Default / Chooser
                                Button(
                                    onClick = { launchUpiApp(null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("btn_launch_upi_chooser"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PastelCyan)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payment,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Open Default UPI App",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "OR CHOOSE INSTALLED APP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = SlateDarkTextSecondary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    UpiAppRedirectTile(
                                        appName = "Google Pay",
                                        badge = if (isGPayInstalled) "Installed" else "Available",
                                        icon = Icons.Default.Payment,
                                        accentColor = Color(0xFF4285F4),
                                        onClick = { launchUpiApp(UpiService.GOOGLE_PAY_PACKAGE) }
                                    )

                                    UpiAppRedirectTile(
                                        appName = "PhonePe",
                                        badge = if (isPhonePeInstalled) "Installed" else "Available",
                                        icon = Icons.Default.AccountBalanceWallet,
                                        accentColor = Color(0xFF5F259F),
                                        onClick = { launchUpiApp(UpiService.PHONEPE_PACKAGE) }
                                    )

                                    UpiAppRedirectTile(
                                        appName = "Paytm",
                                        badge = if (isPaytmInstalled) "Installed" else "Available",
                                        icon = Icons.Default.AccountBalanceWallet,
                                        accentColor = Color(0xFF00BAF2),
                                        onClick = { launchUpiApp(UpiService.PAYTM_PACKAGE) }
                                    )

                                    if (isBhimInstalled) {
                                        UpiAppRedirectTile(
                                            appName = "BHIM UPI",
                                            badge = "Installed",
                                            icon = Icons.Default.AccountBalance,
                                            accentColor = Color(0xFF00897B),
                                            onClick = { launchUpiApp(UpiService.BHIM_PACKAGE) }
                                        )
                                    }

                                    installedUpiApps.filter { app ->
                                        app.packageName !in listOf(
                                            UpiService.GOOGLE_PAY_PACKAGE,
                                            UpiService.PHONEPE_PACKAGE,
                                            UpiService.PAYTM_PACKAGE,
                                            UpiService.BHIM_PACKAGE
                                        )
                                    }.forEach { customApp ->
                                        UpiAppRedirectTile(
                                            appName = customApp.label,
                                            badge = "Installed",
                                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                                            accentColor = EmeraldDarkPrimary,
                                            onClick = { launchUpiApp(customApp.packageName) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Shortcut to enter directly if already paid
                                OutlinedButton(
                                    onClick = { modalStep = UpiModalStep.ENTER_EXPENSE },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, GlassBorderColor)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = PastelCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Already Paid? Enter Expense Directly", fontSize = 12.5.sp, color = SlateDarkTextPrimary)
                                }
                            }
                        }

                        UpiModalStep.ENTER_EXPENSE -> {
                            Column {
                                Surface(
                                    color = IncomeGreen.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Payment complete? Log the expense below to keep your ledger accurate.",
                                            fontSize = 11.5.sp,
                                            color = SlateDarkTextPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Amount Field
                                OutlinedTextField(
                                    value = enteredAmount,
                                    onValueChange = {
                                        enteredAmount = it
                                        amountError = null
                                    },
                                    label = { Text("Amount ($currencySymbol)") },
                                    placeholder = { Text("0.00") },
                                    singleLine = true,
                                    isError = amountError != null,
                                    supportingText = amountError?.let { { Text(it, color = ExpenseRed) } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("upi_expense_amount_input"),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Title / Merchant Field
                                OutlinedTextField(
                                    value = enteredTitle,
                                    onValueChange = { enteredTitle = it },
                                    label = { Text("Title / Payee") },
                                    placeholder = { Text("e.g. Lunch, Grocery, Chai") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("upi_expense_title_input"),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Category Selector
                                Text(
                                    text = "CATEGORY",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = SlateDarkTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(categories) { cat ->
                                        val isSelected = selectedCategory == cat
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCategory = cat },
                                            label = { Text(cat, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PastelCyan.copy(alpha = 0.25f),
                                                selectedLabelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Scope Selection: Personal vs Family
                                Text(
                                    text = "LEDGER SCOPE",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = SlateDarkTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedScope == FinanceScope.PERSONAL,
                                        onClick = { selectedScope = FinanceScope.PERSONAL },
                                        label = { Text("Personal Ledger", fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    FilterChip(
                                        selected = selectedScope == FinanceScope.FAMILY,
                                        onClick = { selectedScope = FinanceScope.FAMILY },
                                        label = { Text(familyName, fontSize = 11.sp, maxLines = 1) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Save Expense & Proceed Button
                                Button(
                                    onClick = {
                                        val amt = enteredAmount.toDoubleOrNull()
                                        if (amt == null || amt <= 0.0) {
                                            amountError = "Please enter a valid amount"
                                            return@Button
                                        }
                                        val finalTitle = enteredTitle.trim().ifBlank { "UPI Payment" }
                                        onSaveTransaction(
                                            finalTitle,
                                            amt,
                                            selectedCategory,
                                            selectedScope,
                                            if (selectedScope == FinanceScope.FAMILY) selectedMemberId else null,
                                            null,
                                            null
                                        )
                                        Toast.makeText(context, "Saved $currencySymbol$amt for $finalTitle", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_save_upi_expense"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Expense & Proceed", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpiAppRedirectTile(
    appName: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SlateDarkSurfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, GlassBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateDarkTextPrimary
                    )
                    Text(
                        text = "Tap to open and pay",
                        fontSize = 10.5.sp,
                        color = SlateDarkTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = SlateDarkTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
