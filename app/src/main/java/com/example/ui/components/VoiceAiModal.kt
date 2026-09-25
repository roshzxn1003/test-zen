package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.ai.ParsedVoiceExpense
import com.example.data.models.TransactionType
import com.example.ui.theme.*
import com.example.ui.viewmodel.VoiceChatMessage
import java.util.*

enum class VoiceModalState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

@Composable
fun VoiceAiModal(
    isProcessing: Boolean,
    parsedExpense: ParsedVoiceExpense?,
    voiceChatMessages: List<VoiceChatMessage> = emptyList(),
    currencySymbol: String,
    onDismiss: () -> Unit,
    onProcessPrompt: (String) -> Unit,
    onProcessAudio: (String) -> Unit = {},
    onConfirmSave: (title: String, amount: Double, category: String, paymentMethod: String) -> Unit = { _, _, _, _ -> },
    onConfirmSaveWithType: ((title: String, amount: Double, type: TransactionType, category: String, paymentMethod: String) -> Unit)? = null,
    onConfirmSaveWithScope: ((title: String, amount: Double, type: TransactionType, category: String, paymentMethod: String, scope: com.example.data.models.FinanceScope) -> Unit)? = null,
    onConfirmMessageTransaction: ((messageId: String, title: String, amount: Double, type: TransactionType, category: String, paymentMethod: String) -> Unit)? = null,
    onClearHistory: (() -> Unit)? = null,
    onOpenManualAdd: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var modalState by remember { mutableStateOf(VoiceModalState.IDLE) }
    var recognizedSpokenText by remember { mutableStateOf("") }
    var bufferedSpeechText by remember { mutableStateOf("") }
    var rawInputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var liveAudioLevel by remember { mutableFloatStateOf(0f) }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Pulsing animation for listening microphone
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun stopListeningSafely() {
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
        liveAudioLevel = 0f
    }

    fun handleProcessQuery(query: String) {
        if (query.isBlank()) return
        stopListeningSafely()
        modalState = VoiceModalState.PROCESSING
        onProcessPrompt(query)
    }

    // System voice recognition fallback
    val systemVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val topSpoken = spokenResults?.firstOrNull()
            if (!topSpoken.isNullOrBlank()) {
                recognizedSpokenText = topSpoken
                bufferedSpeechText = topSpoken
                handleProcessQuery(topSpoken)
            } else {
                errorMessage = "Could not hear clearly. Please try again."
                modalState = VoiceModalState.ERROR
            }
        } else {
            errorMessage = "Voice recognition cancelled."
            modalState = VoiceModalState.ERROR
        }
    }

    fun launchSystemVoiceIntent() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak expense or income in English...")
            }
            systemVoiceLauncher.launch(intent)
        } catch (e: Exception) {
            errorMessage = "Voice recognition service unavailable."
            modalState = VoiceModalState.ERROR
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            errorMessage = null
            modalState = VoiceModalState.IDLE
        } else {
            errorMessage = "Microphone permission is required for voice entry."
            modalState = VoiceModalState.ERROR
        }
    }

    fun startListening() {
        stopListeningSafely()
        errorMessage = null
        recognizedSpokenText = ""
        bufferedSpeechText = ""

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            errorMessage = "Microphone permission is required."
            modalState = VoiceModalState.ERROR
            try {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            launchSystemVoiceIntent()
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "en-GB"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Extended listening window to prevent cutting off user prematurely
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak expense or income in English...")
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    modalState = VoiceModalState.LISTENING
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    liveAudioLevel = (rmsdB / 10f).coerceIn(0.1f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (bufferedSpeechText.isNotBlank()) {
                        modalState = VoiceModalState.PROCESSING
                    }
                }
                override fun onError(error: Int) {
                    // Resilient error handling: if speech was already buffered, recover it instead of dropping!
                    val fallbackText = bufferedSpeechText.ifBlank { recognizedSpokenText }
                    if (fallbackText.isNotBlank()) {
                        stopListeningSafely()
                        handleProcessQuery(fallbackText)
                        return
                    }

                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard. Tap mic to try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic to speak."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue. Please retry."
                        SpeechRecognizer.ERROR_NETWORK -> "Network issue. Please retry or type below."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Could not hear clearly. Tap mic to retry."
                    }
                    errorMessage = msg
                    modalState = VoiceModalState.ERROR
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.ifBlank { null } ?: bufferedSpeechText
                    if (text.isNotBlank()) {
                        recognizedSpokenText = text
                        bufferedSpeechText = text
                        handleProcessQuery(text)
                    } else {
                        errorMessage = "Could not hear clearly. Please try again."
                        modalState = VoiceModalState.ERROR
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        bufferedSpeechText = text
                        recognizedSpokenText = text
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer = recognizer
            recognizer.startListening(intent)
            modalState = VoiceModalState.LISTENING
        } catch (e: Exception) {
            e.printStackTrace()
            launchSystemVoiceIntent()
        }
    }

    // Sync processing state
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            modalState = VoiceModalState.PROCESSING
        } else if (modalState == VoiceModalState.PROCESSING) {
            modalState = VoiceModalState.IDLE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopListeningSafely()
        }
    }

    val examplePhrases = remember {
        listOf(
            "Spent 350 for lunch via UPI",
            "Innaiku movie ki 250 selavu",
            "Paid 1200 electricity bill cash",
            "Veetu vaadagai 15000 family vault",
            "500 petrol PhonePe",
            "Appavukku marundhu vanginen 450",
            "Received 50000 salary in bank",
            "Kadaila groceries 1200 selavu",
            "Got 200 cashback on Google Pay",
            "Dinner with roommates 800 split"
        )
    }

    val expenseCategories = remember {
        listOf(
            "Food & Dining",
            "Transportation",
            "Shopping",
            "Bills & Utilities",
            "Entertainment",
            "Housing & Rent",
            "Healthcare",
            "Education",
            "Other"
        )
    }

    val incomeCategories = remember {
        listOf(
            "Salary & Income",
            "Freelance / Business",
            "Investments",
            "Other"
        )
    }

    val paymentMethods = remember {
        listOf("UPI", "Cash", "Credit Card", "Debit Card", "Bank Transfer")
    }

    Dialog(
        onDismissRequest = {
            stopListeningSafely()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF06B6D4)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Voice Transaction Entry",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "English • Expense & Income Logging",
                                fontSize = 11.sp,
                                color = EmeraldDarkPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            stopListeningSafely()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_voice_modal")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateDarkTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card View: Parsed Transaction (if available) OR Listening View
                if (parsedExpense != null) {
                    // --- PARSED TRANSACTION CONFIRMATION CARD ---
                    var editTitle by remember(parsedExpense) { mutableStateOf(parsedExpense.title) }
                    var editAmount by remember(parsedExpense) {
                        mutableStateOf(
                            if (parsedExpense.amount % 1.0 == 0.0) "${parsedExpense.amount.toInt()}" else "${parsedExpense.amount}"
                        )
                    }
                    var editType by remember(parsedExpense) { mutableStateOf(parsedExpense.type) }
                    var editScope by remember(parsedExpense) { mutableStateOf(parsedExpense.scope) }
                    var editCategory by remember(parsedExpense) { mutableStateOf(parsedExpense.category) }
                    var editPaymentMethod by remember(parsedExpense) { mutableStateOf(parsedExpense.paymentMethod) }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SlateDarkSurfaceVariant,
                        border = BorderStroke(1.dp, if (editType == TransactionType.INCOME) IncomeGreen.copy(alpha = 0.5f) else PastelRose.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header & Type Selector Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CONFIRM TRANSACTION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextSecondary
                                )

                                // Income / Expense Chips
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (editType == TransactionType.EXPENSE) PastelRose else Color.Transparent,
                                        border = BorderStroke(1.dp, if (editType == TransactionType.EXPENSE) PastelRose else SlateDarkTextSecondary.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            editType = TransactionType.EXPENSE
                                            if (!expenseCategories.contains(editCategory)) {
                                                editCategory = "Food & Dining"
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "Expense",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (editType == TransactionType.EXPENSE) Color.White else SlateDarkTextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (editType == TransactionType.INCOME) IncomeGreen else Color.Transparent,
                                        border = BorderStroke(1.dp, if (editType == TransactionType.INCOME) IncomeGreen else SlateDarkTextSecondary.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            editType = TransactionType.INCOME
                                            if (!incomeCategories.contains(editCategory)) {
                                                editCategory = "Salary & Income"
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "Income",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (editType == TransactionType.INCOME) Color.White else SlateDarkTextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Scope Selector Toggle (Personal vs Family Vault)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "FINANCE SCOPE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateDarkTextSecondary
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (editScope == com.example.data.models.FinanceScope.PERSONAL) EmeraldDarkPrimary else Color.Transparent,
                                        border = BorderStroke(1.dp, if (editScope == com.example.data.models.FinanceScope.PERSONAL) EmeraldDarkPrimary else SlateDarkTextSecondary.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            editScope = com.example.data.models.FinanceScope.PERSONAL
                                        }
                                    ) {
                                        Text(
                                            text = "Personal",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (editScope == com.example.data.models.FinanceScope.PERSONAL) Color.White else SlateDarkTextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (editScope == com.example.data.models.FinanceScope.FAMILY) GoldAccent else Color.Transparent,
                                        border = BorderStroke(1.dp, if (editScope == com.example.data.models.FinanceScope.FAMILY) GoldAccent else SlateDarkTextSecondary.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            editScope = com.example.data.models.FinanceScope.FAMILY
                                        }
                                    ) {
                                        Text(
                                            text = "Family Vault",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (editScope == com.example.data.models.FinanceScope.FAMILY) Color.Black else SlateDarkTextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Amount Input Field
                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { editAmount = it },
                                label = { Text("Amount ($currencySymbol)", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (editType == TransactionType.INCOME) IncomeGreen else PastelRose,
                                    unfocusedBorderColor = GlassBorderColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Title Input Field
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Title / Description", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanDarkSecondary,
                                    unfocusedBorderColor = GlassBorderColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Category Selector
                            Text("Category", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            val activeCategories = if (editType == TransactionType.INCOME) incomeCategories else expenseCategories
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(activeCategories) { cat ->
                                    val isSelected = editCategory == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { editCategory = cat },
                                        label = { Text(cat, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (editType == TransactionType.INCOME) IncomeGreen.copy(alpha = 0.25f) else PastelRose.copy(alpha = 0.25f),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Payment Method Selector
                            Text("Payment Method", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(paymentMethods) { method ->
                                    val isSelected = editPaymentMethod.equals(method, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { editPaymentMethod = method },
                                        label = { Text(method, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanDarkSecondary.copy(alpha = 0.25f),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons: Save & Redo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        startListening()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, GlassBorderColor),
                                    modifier = Modifier
                                        .weight(0.38f)
                                        .height(42.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = SlateDarkTextSecondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-speak", fontSize = 11.sp, color = SlateDarkTextSecondary)
                                }

                                Button(
                                    onClick = {
                                        val amt = editAmount.toDoubleOrNull() ?: parsedExpense.amount
                                        val finalTitle = editTitle.ifBlank { parsedExpense.title }
                                        val finalCat = editCategory.ifBlank { parsedExpense.category }
                                        val finalMethod = editPaymentMethod.ifBlank { parsedExpense.paymentMethod }

                                        if (onConfirmSaveWithScope != null) {
                                            onConfirmSaveWithScope(finalTitle, amt, editType, finalCat, finalMethod, editScope)
                                        } else if (onConfirmSaveWithType != null) {
                                            onConfirmSaveWithType(finalTitle, amt, editType, finalCat, finalMethod)
                                        } else {
                                            onConfirmSave(finalTitle, amt, finalCat, finalMethod)
                                        }
                                        Toast.makeText(context, "Saved $currencySymbol$amt for $finalTitle", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (editType == TransactionType.INCOME) IncomeGreen else EmeraldDarkPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(0.62f)
                                        .height(42.dp)
                                        .testTag("btn_confirm_voice_save")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Transaction", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    // --- LISTENING / PROMPT VIEW ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Central Glowing Microphone Button with multi-layer animated wave
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            if (modalState == VoiceModalState.LISTENING) {
                                // Outer pulsing wave
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(CyanDarkSecondary.copy(alpha = 0.20f))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .scale(if (modalState == VoiceModalState.LISTENING) (pulseScale * 0.96f) else 1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (modalState == VoiceModalState.LISTENING) {
                                            Brush.linearGradient(listOf(CyanDarkSecondary, Color(0xFF6366F1)))
                                        } else {
                                            Brush.linearGradient(listOf(EmeraldDarkPrimary, Color(0xFF059669)))
                                        }
                                    )
                                    .clickable {
                                        if (modalState == VoiceModalState.LISTENING) {
                                            val query = recognizedSpokenText.ifBlank { bufferedSpeechText }
                                            if (query.isNotBlank()) {
                                                handleProcessQuery(query)
                                            } else {
                                                stopListeningSafely()
                                                modalState = VoiceModalState.IDLE
                                            }
                                        } else {
                                            startListening()
                                        }
                                    }
                                    .testTag("mic_listen_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (modalState == VoiceModalState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Microphone",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status / Waveform / State feedback
                        when (modalState) {
                            VoiceModalState.LISTENING -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CyanDarkSecondary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, CyanDarkSecondary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(CyanDarkSecondary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Active Listening • Extended pause window active",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CyanDarkSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Dynamic 9-bar reactive soundwave equalizer
                                Row(
                                    modifier = Modifier.height(26.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val multipliers = listOf(0.3f, 0.6f, 0.9f, 1.2f, 1.5f, 1.2f, 0.9f, 0.6f, 0.3f)
                                    multipliers.forEach { factor ->
                                        val barHeight = (6.dp + (20.dp * liveAudioLevel * factor)).coerceIn(5.dp, 26.dp)
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(barHeight)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(CyanDarkSecondary, EmeraldDarkPrimary)
                                                    )
                                                )
                                        )
                                    }
                                }

                                val speechPreview = recognizedSpokenText.ifBlank { bufferedSpeechText }
                                if (speechPreview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SlateDarkSurfaceVariant,
                                        border = BorderStroke(1.dp, GlassBorderColor),
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        Text(
                                            text = "\"$speechPreview\"",
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Manual submission button while listening
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            stopListeningSafely()
                                            modalState = VoiceModalState.IDLE
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, GlassBorderColor),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp, color = SlateDarkTextSecondary)
                                    }

                                    Button(
                                        onClick = {
                                            val query = recognizedSpokenText.ifBlank { bufferedSpeechText }
                                            if (query.isNotBlank()) {
                                                handleProcessQuery(query)
                                            } else {
                                                stopListeningSafely()
                                                modalState = VoiceModalState.IDLE
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanDarkSecondary),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Done Speaking ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            VoiceModalState.PROCESSING -> {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SlateDarkSurfaceVariant,
                                    border = BorderStroke(1.dp, GlassBorderColor),
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(
                                            color = EmeraldDarkPrimary,
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Gemini AI is parsing financial entities...",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Extracting title, amount, category and transaction scope",
                                            fontSize = 11.sp,
                                            color = SlateDarkTextSecondary
                                        )
                                        val preview = recognizedSpokenText.ifBlank { bufferedSpeechText.ifBlank { rawInputText } }
                                        if (preview.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "\"$preview\"",
                                                fontSize = 11.sp,
                                                color = CyanDarkSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            VoiceModalState.ERROR -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ExpenseRed.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.4f)),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = ExpenseRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = errorMessage ?: "Could not hear clearly. Tap mic to retry.",
                                            fontSize = 12.sp,
                                            color = ExpenseRed,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            VoiceModalState.IDLE -> {
                                Text(
                                    text = "Tap mic to speak your transaction",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlateDarkTextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Supports English & Tanglish • Extended pause window active",
                                    fontSize = 11.sp,
                                    color = SlateDarkTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Example Suggestion Chips
                    Text(
                        text = "Quick Examples",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateDarkTextSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(examplePhrases) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SlateDarkSurfaceVariant,
                                border = BorderStroke(1.dp, GlassBorderColor),
                                modifier = Modifier.clickable {
                                    handleProcessQuery(prompt)
                                }
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 11.sp,
                                    color = SlateDarkTextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Manual Text Input Fallback
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateDarkSurfaceVariant, RoundedCornerShape(14.dp))
                        .border(1.dp, GlassBorderColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        placeholder = {
                            Text(
                                text = "Type transaction (e.g. Lunch 250 UPI)...",
                                fontSize = 11.sp,
                                color = SlateDarkTextSecondary
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            keyboardController?.hide()
                            if (rawInputText.isNotBlank()) {
                                val query = rawInputText
                                rawInputText = ""
                                handleProcessQuery(query)
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_manual_text_input")
                    )

                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            if (rawInputText.isNotBlank()) {
                                val query = rawInputText
                                rawInputText = ""
                                handleProcessQuery(query)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_parse_text")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = EmeraldDarkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
