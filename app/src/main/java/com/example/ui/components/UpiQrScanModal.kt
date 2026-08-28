package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.data.upi.UpiPaymentInfo
import com.example.data.upi.UpiService
import com.example.ui.theme.*
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpiQrScanModal(
    currencySymbol: String,
    currentFinanceScope: FinanceScope,
    currentUserName: String,
    familyName: String,
    familyMembers: List<FamilyMemberEntity>,
    onDismiss: () -> Unit,
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
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scanFailed by remember { mutableStateOf(false) }
    var failureReason by remember { mutableStateOf<String?>(null) }
    var scannedInfo by remember { mutableStateOf<UpiPaymentInfo?>(null) }

    // Editable review state after a successful scan
    var editMerchant by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editVpa by remember { mutableStateOf("") }

    var selectedScope by remember { mutableStateOf(currentFinanceScope) }
    var selectedCategory by remember { mutableStateOf("Food & Dining") }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var payRequest by remember { mutableStateOf<UpiPayRequest?>(null) }

    val isGPayInstalled = remember { UpiService.isGooglePayInstalled(context) }

            

    fun processBarcodeResult(raw: String) {
        val parsed = UpiService.parseQrPayload(raw)
        if (parsed != null) {
            scannedInfo = parsed
            editVpa = parsed.payeeAddress
            editMerchant = parsed.payeeName.ifBlank { parsed.note.ifBlank { "UPI Payment" } }
            editAmount = parsed.amount.ifBlank { "" }
            scanFailed = false
            failureReason = null
        } else {
            // Check if it's a bare UPI ID or raw text with VPA
            if (raw.contains("@")) {
                val candidateVpa = raw.split(Regex("[\\s:?&=;,/|]")).firstOrNull { it.contains("@") && UpiService.isValidVpa(it.trim()) }
                if (candidateVpa != null) {
                    val cleanVpa = candidateVpa.trim()
                    scannedInfo = UpiPaymentInfo(payeeAddress = cleanVpa, payeeName = "UPI Merchant")
                    editVpa = cleanVpa
                    editMerchant = "UPI Merchant"
                    editAmount = ""
                    scanFailed = false
                    failureReason = null
                    return
                }
            }
            scanFailed = true
            failureReason = "Scanned QR does not contain valid UPI payment information."
            Toast.makeText(context, "Not a recognized UPI QR code. Please scan a standard UPI QR.", Toast.LENGTH_SHORT).show()
        }
    }

    // On-device ML Kit Barcode reader fallback for Gallery / image URI
    fun analyzeQrImage(uri: Uri) {
        coroutineScope.launch {
            isScanning = true
            scanFailed = false
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val fallbackScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )
                fallbackScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        isScanning = false
                        val firstBarcode = barcodes.firstOrNull()
                        val raw = firstBarcode?.rawValue ?: firstBarcode?.displayValue ?: ""
                        if (raw.isNotBlank()) {
                            processBarcodeResult(raw)
                        } else {
                            scanFailed = true
                            failureReason = "No QR code found in selected image."
                            Toast.makeText(context, "No UPI QR code found in image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        isScanning = false
                        scanFailed = true
                        failureReason = "Could not analyze QR from image: ${e.localizedMessage}"
                    }
            } catch (e: Exception) {
                isScanning = false
                scanFailed = true
                failureReason = "Failed to load image: ${e.localizedMessage}"
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            analyzeQrImage(uri)
        }
    }

    fun startScan() {
        isScanning = true
        scanFailed = false
        failureReason = null
    }

    LaunchedEffect(Unit) {
        startScan()
    }

    val amount = editAmount.toDoubleOrNull() ?: 0.0
    val isValid = amount > 0 && editMerchant.isNotBlank() && UpiService.isValidVpa(editVpa)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .testTag("upi_qr_scan_modal")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Scan UPI QR",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Instant payment via Google Pay & UPI",
                                fontSize = 11.sp,
                                color = SlateDarkTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scan state: loading / failed / scanned
                when {
                    isScanning -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GlassCardBg,
                            modifier = Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(16.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                QrCameraView(
                                    modifier = Modifier.fillMaxSize(),
                                    onQrScanned = { raw ->
                                        isScanning = false
                                        processBarcodeResult(raw)
                                    }
                                )
                                Column(
                                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier.size(240.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            val strokeWidth = 4.dp.toPx()
                                            val length = 30.dp.toPx()
                                            val color = Color(0xFF8B5CF6)
                                            
                                            // Top-Left
                                            drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(length, 0f), strokeWidth)
                                            drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, length), strokeWidth)
                                            // Top-Right
                                            drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - length, 0f), strokeWidth)
                                            drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, length), strokeWidth)
                                            // Bottom-Left
                                            drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(length, size.height), strokeWidth)
                                            drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - length), strokeWidth)
                                            // Bottom-Right
                                            drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - length, size.height), strokeWidth)
                                            drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - length), strokeWidth)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    scanFailed || scannedInfo == null -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GlassCardBg,
                            border = BorderStroke(1.5.dp, GlassBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (scanFailed) "Scanner Ready" else "Ready to scan UPI QR",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextPrimary
                                )
                                Text(
                                    text = failureReason ?: "Point camera at any UPI QR code or pick a screenshot from Gallery.",
                                    fontSize = 12.sp,
                                    color = SlateDarkTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp),
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { startScan() },
                                        modifier = Modifier.weight(1f).height(42.dp).testTag("btn_scan_qr_again"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan QR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { galleryLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f).height(42.dp).testTag("btn_pick_qr_gallery"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("From Gallery", fontSize = 12.sp)
                                    }
                                }

                                // Direct UPI ID entry fallback
                                Spacer(modifier = Modifier.height(10.dp))
                                TextButton(
                                    onClick = {
                                        scannedInfo = UpiPaymentInfo(payeeAddress = "", payeeName = "UPI Payment")
                                        scanFailed = false
                                    }
                                ) {
                                    Text("Enter UPI ID manually →", fontSize = 12.sp, color = EmeraldDarkPrimary)
                                }
                            }
                        }
                    }
                    else -> {
                        // --- REVIEW SCANNED PAYMENT ---
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SCANNED UPI DETAILS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6),
                                        letterSpacing = 0.8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = IncomeGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "QR Verified",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IncomeGreen,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                // UPI ID
                                OutlinedTextField(
                                    value = editVpa,
                                    onValueChange = { editVpa = it },
                                    label = { Text("UPI ID (VPA)") },
                                    placeholder = { Text("e.g. merchant@okhdfcbank", color = SlateDarkTextMuted) },
                                    isError = editVpa.isNotBlank() && !UpiService.isValidVpa(editVpa),
                                    supportingText = {
                                        if (editVpa.isNotBlank() && !UpiService.isValidVpa(editVpa)) {
                                            Text("Invalid UPI ID", fontSize = 11.sp, color = ExpenseRed)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8B5CF6),
                                        unfocusedBorderColor = GlassBorderColor,
                                        focusedContainerColor = SlateDarkSurfaceVariant,
                                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                                        focusedTextColor = SlateDarkTextPrimary,
                                        unfocusedTextColor = SlateDarkTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Merchant
                                OutlinedTextField(
                                    value = editMerchant,
                                    onValueChange = { editMerchant = it },
                                    label = { Text("Merchant / Purpose") },
                                    placeholder = { Text("Enter merchant name", color = SlateDarkTextMuted) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8B5CF6),
                                        unfocusedBorderColor = GlassBorderColor,
                                        focusedContainerColor = SlateDarkSurfaceVariant,
                                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                                        focusedTextColor = SlateDarkTextPrimary,
                                        unfocusedTextColor = SlateDarkTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Amount (editable)
                                OutlinedTextField(
                                    value = editAmount,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isDigit() || it == '.' }
                                        if (clean.count { it == '.' } <= 1) editAmount = clean
                                    },
                                    label = { Text("Amount ($currencySymbol)") },
                                    placeholder = { Text("0.00", color = SlateDarkTextMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8B5CF6),
                                        unfocusedBorderColor = GlassBorderColor,
                                        focusedContainerColor = SlateDarkSurfaceVariant,
                                        unfocusedContainerColor = SlateDarkSurfaceVariant,
                                        focusedTextColor = SlateDarkTextPrimary,
                                        unfocusedTextColor = SlateDarkTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("upi_qr_amount_field"),
                                    singleLine = true
                                )

                                if (scannedInfo?.amount.isNullOrBlank()) {
                                    Text(
                                        text = "Amount was not in QR. Enter amount to complete payment.",
                                        fontSize = 11.sp,
                                        color = GoalAmber,
                                        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                                        maxLines = 2
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { startScan() },
                                        contentPadding = PaddingValues(horizontal = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF8B5CF6))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan another QR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                                    }

                                    TextButton(
                                        onClick = { galleryLauncher.launch("image/*") },
                                        contentPadding = PaddingValues(horizontal = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp), tint = SlateDarkTextSecondary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pick from Gallery", fontSize = 12.sp, color = SlateDarkTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                UpiTransactionFields(
                    selectedScope = selectedScope,
                    onScopeSelected = { selectedScope = it },
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    selectedMemberId = selectedMemberId,
                    onMemberSelected = { selectedMemberId = it },
                    purpose = editMerchant,
                    familyName = familyName,
                    familyMembers = familyMembers,
                    currentUserName = currentUserName
                )

                Spacer(modifier = Modifier.height(16.dp))

                // PRIMARY ACTION: Generic UPI Intent Chooser (PhonePe, Paytm, GPay, BHIM, CRED, etc.)
                Button(
                    onClick = {
                        if (!UpiService.isValidVpa(editVpa)) {
                            Toast.makeText(context, "Please enter a valid UPI ID (VPA).", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (amount <= 0 || editMerchant.isBlank()) {
                            Toast.makeText(context, "Enter an amount and merchant.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        payRequest = UpiPayRequest(
                            amount = amount,
                            purpose = editMerchant,
                            vpa = editVpa,
                            targetPackage = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_pay_via_upi_chooser"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                    enabled = scannedInfo != null && isValid
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pay via UPI App (Choose App)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // OPTIONAL DIRECT SHORTCUT: Google Pay Direct Launch
                OutlinedButton(
                    onClick = {
                        if (!UpiService.isValidVpa(editVpa)) {
                            Toast.makeText(context, "Please enter a valid UPI ID (VPA).", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        if (amount <= 0 || editMerchant.isBlank()) {
                            Toast.makeText(context, "Enter an amount and merchant.", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        payRequest = UpiPayRequest(
                            amount = amount,
                            purpose = editMerchant,
                            vpa = editVpa,
                            targetPackage = UpiService.GOOGLE_PAY_PACKAGE
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_pay_via_gpay_direct"),
                    shape = RoundedCornerShape(14.dp),
                    enabled = scannedInfo != null && isValid
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pay Directly via Google Pay", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Opens Google Pay or selected UPI app directly. Payment is verified and saved safely.",
                    fontSize = 10.sp,
                    color = SlateDarkTextMuted,
                    lineHeight = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    maxLines = 2
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
                    req.purpose.trim().ifBlank { "UPI Payment" },
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
