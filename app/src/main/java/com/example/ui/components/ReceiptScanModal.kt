package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.ai.ParsedReceipt
import com.example.data.models.ReceiptItemEntity
import com.example.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

@Composable
fun ReceiptScanModal(
    isProcessing: Boolean,
    parsedReceipt: ParsedReceipt?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onProcessReceipt: (String) -> Unit,
    onConfirmSave: () -> Unit,
    onSaveReceiptDetails: ((
        merchant: String,
        amount: Double,
        category: String,
        paymentMethod: String,
        dateStr: String,
        timeStr: String?,
        receiptNumber: String?,
        subtotal: Double,
        discount: Double,
        tax: Double,
        items: List<ReceiptItemEntity>,
        imageUri: String?,
        rawText: String?
    ) -> Unit)? = null,
    onBarcodeScanned: (String) -> Unit,
    onOpenManualAdd: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isOcrRunning by remember { mutableStateOf(false) }
    var ocrFailed by remember { mutableStateOf(false) }
    var extractedRawText by remember { mutableStateOf<String?>(null) }
    var isCameraPermissionDenied by remember { mutableStateOf(false) }

    // Editable review state after OCR
    var editMerchant by remember { mutableStateOf("") }
    var editReceiptNumber by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("Today") }
    var editTime by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("Shopping") }
    var editPaymentMethod by remember { mutableStateOf("UPI") }
    var editSubtotal by remember { mutableStateOf("") }
    var editDiscount by remember { mutableStateOf("") }
    var editTax by remember { mutableStateOf("") }
    var editTotal by remember { mutableStateOf("") }

    // Editable Items List
    var receiptItems by remember { mutableStateOf<List<ReceiptItemEntity>>(emptyList()) }

    // Add Item Dialog State
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var newItemQty by remember { mutableStateOf("1") }
    var newItemUnitPrice by remember { mutableStateOf("") }

    // Validation Alert State
    var showMismatchWarning by remember { mutableStateOf(false) }

    val categories = listOf("Food & Dining", "Shopping", "Transportation", "Bills & Utilities", "Healthcare", "Entertainment", "Other")
    val paymentMethods = listOf("UPI", "Cash", "Card", "Bank Transfer", "Other")

    // Update review fields when parsed receipt is ready
    LaunchedEffect(parsedReceipt) {
        if (parsedReceipt != null) {
            editMerchant = parsedReceipt.merchantName
            editReceiptNumber = parsedReceipt.receiptNumber ?: ""
            editDate = parsedReceipt.dateString.ifBlank { "Today" }
            editTime = parsedReceipt.timeString
            editCategory = parsedReceipt.category
            editPaymentMethod = parsedReceipt.paymentMethod
            editSubtotal = if (parsedReceipt.subtotal > 0) String.format(Locale.US, "%.2f", parsedReceipt.subtotal) else ""
            editDiscount = if (parsedReceipt.discount > 0) String.format(Locale.US, "%.2f", parsedReceipt.discount) else ""
            editTax = if (parsedReceipt.tax > 0) String.format(Locale.US, "%.2f", parsedReceipt.tax) else ""
            editTotal = if (parsedReceipt.totalAmount > 0) String.format(Locale.US, "%.2f", parsedReceipt.totalAmount) else ""

            receiptItems = parsedReceipt.items.map {
                ReceiptItemEntity(
                    transactionId = 0,
                    name = it.name,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    totalPrice = it.totalPrice
                )
            }
            ocrFailed = false
            showMismatchWarning = false
        }
    }

    // Helper to generate a temp file and URI via FileProvider for high-resolution camera capture
    fun createTempImageUri(): Uri? {
        return try {
            val cacheDir = context.cacheDir
            val tempFile = File.createTempFile(
                "zenith_receipt_${System.currentTimeMillis()}",
                ".jpg",
                cacheDir
            ).apply {
                createNewFile()
                deleteOnExit()
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Process image with ML Kit Text Recognition
    fun processImageForOcr(uri: Uri) {
        coroutineScope.launch {
            isOcrRunning = true
            ocrFailed = false
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(context, uri)
                }
                selectedImageUri = uri

                withContext(Dispatchers.IO) {
                    try {
                        val bm = if (Build.VERSION.SDK_INT >= 28) {
                            val source = ImageDecoder.createSource(context.contentResolver, uri)
                            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.isMutableRequired = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                        selectedBitmap = bm
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        if (text.isNotBlank()) {
                            extractedRawText = text
                            onProcessReceipt(text)
                        } else {
                            ocrFailed = true
                        }
                        isOcrRunning = false
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        ocrFailed = true
                        isOcrRunning = false
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                ocrFailed = true
                isOcrRunning = false
            }
        }
    }

    // High-resolution Camera launcher (TakePicture contract)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            processImageForOcr(tempCameraUri!!)
        }
    }

    // Fallback thumbnail camera launcher
    val cameraPreviewFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            isOcrRunning = true
            ocrFailed = false
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isNotBlank()) {
                        extractedRawText = text
                        onProcessReceipt(text)
                    } else {
                        ocrFailed = true
                    }
                    isOcrRunning = false
                }
                .addOnFailureListener {
                    ocrFailed = true
                    isOcrRunning = false
                }
        }
    }

    fun launchCameraFlow() {
        isCameraPermissionDenied = false
        val uri = createTempImageUri()
        if (uri != null) {
            tempCameraUri = uri
            try {
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "No camera app available.", Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                cameraPreviewFallbackLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, "No camera app available.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCameraPermissionDenied = false
            launchCameraFlow()
        } else {
            isCameraPermissionDenied = true
            Toast.makeText(context, "Camera permission is required to scan receipts.", Toast.LENGTH_SHORT).show()
        }
    }

    fun onTakePhotoClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraFlow()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processImageForOcr(uri)
        }
    }

    fun onChooseGalleryClick() {
        try {
            galleryLauncher.launch("image/*")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No gallery or file picker app available.", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSaveExecution(force: Boolean = false) {
        val totalVal = editTotal.toDoubleOrNull() ?: 0.0
        val subtotalVal = editSubtotal.toDoubleOrNull() ?: totalVal
        val discountVal = editDiscount.toDoubleOrNull() ?: 0.0
        val taxVal = editTax.toDoubleOrNull() ?: 0.0

        if (totalVal <= 0) {
            Toast.makeText(context, "Please enter a valid total amount", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate items sum against total if items exist
        if (receiptItems.isNotEmpty() && !force) {
            val itemsSum = receiptItems.sumOf { it.totalPrice }
            val calculatedExpected = itemsSum - discountVal + taxVal
            if (Math.abs(calculatedExpected - totalVal) > 0.50 && Math.abs(itemsSum - totalVal) > 0.50) {
                showMismatchWarning = true
                return
            }
        }

        if (onSaveReceiptDetails != null) {
            onSaveReceiptDetails(
                editMerchant.ifBlank { "Receipt Purchase" },
                totalVal,
                editCategory,
                editPaymentMethod,
                editDate,
                editTime.ifBlank { null },
                editReceiptNumber.ifBlank { null },
                subtotalVal,
                discountVal,
                taxVal,
                receiptItems,
                selectedImageUri?.toString() ?: tempCameraUri?.toString(),
                extractedRawText
            )
            onDismiss()
        } else {
            onConfirmSave()
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .testTag("receipt_scan_modal")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
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
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Receipt Scanner",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "Itemized expense & tax extraction",
                                fontSize = 11.sp,
                                color = SlateDarkTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).testTag("close_receipt_modal")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateDarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Camera Permission Warning Banner if Denied
                if (isCameraPermissionDenied) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoalAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, GoalAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = GoalAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Camera permission needed. Grant access or select receipt from gallery.",
                                fontSize = 12.sp,
                                color = SlateDarkTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (isOcrRunning || isProcessing) {
                    // Loading State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlassCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF8B5CF6), strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Scanning receipt text...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                            Text("Extracting items, quantities, taxes & total", fontSize = 12.sp, color = SlateDarkTextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                } else if (ocrFailed) {
                    // Failed OCR State
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ExpenseRed.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Couldn't read this receipt.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                            Text("Ensure receipt is well-lit and not blurry.", fontSize = 12.sp, color = SlateDarkTextSecondary, modifier = Modifier.padding(top = 2.dp))

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onTakePhotoClick() },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Try Again", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { onChooseGalleryClick() },
                                    modifier = Modifier.weight(1.2f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Choose Another", fontSize = 12.sp)
                                }
                            }

                            if (onOpenManualAdd != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenManualAdd()
                                    }
                                ) {
                                    Text("Enter Manually →", color = EmeraldDarkPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (parsedReceipt != null) {
                    // --- FULL OCR REVIEW & EDIT CARD ---
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GlassCardBg,
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("REVIEW RECEIPT DETAILS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6), letterSpacing = 1.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IncomeGreen.copy(alpha = 0.15f)
                                ) {
                                    Text("✓ OCR Extracted", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IncomeGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Merchant Name Field
                            OutlinedTextField(
                                value = editMerchant,
                                onValueChange = { editMerchant = it },
                                label = { Text("Merchant / Store Name", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Date & Time Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editDate,
                                    onValueChange = { editDate = it },
                                    label = { Text("Date", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editTime,
                                    onValueChange = { editTime = it },
                                    label = { Text("Time (e.g. 14:22)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Receipt Number (Invoice #)
                            OutlinedTextField(
                                value = editReceiptNumber,
                                onValueChange = { editReceiptNumber = it },
                                label = { Text("Invoice / Receipt # (Optional)", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Category Selector Chips
                            Text("Category", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(categories) { cat ->
                                    FilterChip(
                                        selected = editCategory == cat,
                                        onClick = { editCategory = cat },
                                        label = {
                                            Text(
                                                text = cat,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Payment Method Dropdown
                            PaymentMethodDropdown(
                                selectedMethod = editPaymentMethod,
                                onMethodSelected = { editPaymentMethod = it },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = GlassBorderColor)
                            Spacer(modifier = Modifier.height(10.dp))

                            // --- ITEM LIST HEADER ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECEIPT ITEMS (${receiptItems.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextSecondary,
                                    letterSpacing = 1.sp
                                )

                                TextButton(
                                    onClick = {
                                        newItemName = ""
                                        newItemQty = "1"
                                        newItemUnitPrice = ""
                                        showAddItemDialog = true
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("+ Add Item", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldDarkPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Item Rows
                            receiptItems.forEachIndexed { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SlateDarkSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SlateDarkTextPrimary
                                            )
                                            Text(
                                                text = "${if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity} × $currencySymbol${String.format(Locale.US, "%.2f", item.unitPrice)}",
                                                fontSize = 11.sp,
                                                color = SlateDarkTextSecondary
                                            )
                                        }

                                        Text(
                                            text = "$currencySymbol${String.format(Locale.US, "%.2f", item.totalPrice)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateDarkTextPrimary
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        IconButton(
                                            onClick = {
                                                receiptItems = receiptItems.toMutableList().also { it.removeAt(index) }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = ExpenseRed.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = GlassBorderColor)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Breakdown Fields (Subtotal, Tax, Discount, Total)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editSubtotal,
                                    onValueChange = { editSubtotal = it },
                                    label = { Text("Subtotal", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editTax,
                                    onValueChange = { editTax = it },
                                    label = { Text("Tax / GST", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editDiscount,
                                    onValueChange = { editDiscount = it },
                                    label = { Text("Discount", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Grand Total Amount Field
                            OutlinedTextField(
                                value = editTotal,
                                onValueChange = { editTotal = it },
                                label = { Text("GRAND TOTAL ($currencySymbol)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldDarkPrimary,
                                    unfocusedBorderColor = GlassBorderColor
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    // Total Mismatch Warning Card
                    if (showMismatchWarning) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoalAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GoalAmber.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GoalAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Receipt Totals Warning",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = GoalAmber
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Sum of items (${currencySymbol}${String.format(Locale.US, "%.2f", receiptItems.sumOf { it.totalPrice })}) differs from Grand Total ($currencySymbol$editTotal).",
                                    fontSize = 11.sp,
                                    color = SlateDarkTextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showMismatchWarning = false },
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text("Edit Receipt", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { handleSaveExecution(force = true) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                                    ) {
                                        Text("Save Anyway", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Actions: [Save Expense], [Scan Again]
                    Button(
                        onClick = { handleSaveExecution(force = false) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_receipt_save_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Scanned Expense", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            selectedBitmap = null
                            selectedImageUri = null
                            tempCameraUri = null
                            extractedRawText = null
                            receiptItems = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan Another Receipt", color = SlateDarkTextSecondary, fontSize = 12.sp)
                    }
                } else {
                    // Empty Initial State
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GlassCardBg,
                        border = BorderStroke(1.5.dp, GlassBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "No receipt selected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )

                            Text(
                                text = "Take a photo or select a receipt image to begin.",
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons: [ Take Photo ] and [ Choose From Gallery ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onTakePhotoClick() },
                            modifier = Modifier.weight(1f).height(46.dp).testTag("btn_take_photo"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { onChooseGalleryClick() },
                            modifier = Modifier.weight(1f).height(46.dp).testTag("btn_choose_gallery"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    if (onOpenManualAdd != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                onDismiss()
                                onOpenManualAdd()
                            }
                        ) {
                            Text("Or Enter Transaction Manually →", color = EmeraldDarkPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // --- ADD ITEM MANUALLY DIALOG ---
    if (showAddItemDialog) {
        val qtyVal = newItemQty.toDoubleOrNull() ?: 1.0
        val unitPriceVal = newItemUnitPrice.toDoubleOrNull() ?: 0.0
        val calculatedTotal = qtyVal * unitPriceVal

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Receipt Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("Item Name") },
                        placeholder = { Text("e.g. Organic Milk, Bread") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newItemQty,
                            onValueChange = { newItemQty = it },
                            label = { Text("Quantity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = newItemUnitPrice,
                            onValueChange = { newItemUnitPrice = it },
                            label = { Text("Unit Price ($currencySymbol)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlateDarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Calculated Total:", fontSize = 12.sp, color = SlateDarkTextSecondary)
                            Text("$currencySymbol${String.format(Locale.US, "%.2f", calculatedTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemName.isNotBlank() && calculatedTotal > 0) {
                            val newItem = ReceiptItemEntity(
                                transactionId = 0,
                                name = newItemName.trim(),
                                quantity = qtyVal,
                                unitPrice = unitPriceVal,
                                totalPrice = calculatedTotal
                            )
                            receiptItems = receiptItems + newItem
                            showAddItemDialog = false
                        } else {
                            Toast.makeText(context, "Please enter item name and price", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Text("Add to Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
