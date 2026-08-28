package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.outlined.*
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
import androidx.core.content.ContextCompat
import com.example.data.ai.ParsedVoiceExpense
import com.example.data.models.TransactionType
import com.example.ui.theme.*
import java.util.*

enum class VoiceModalState {
    IDLE,
    LISTENING,
    PROCESSING,
    RESULT,
    SUCCESS,
    ERROR
}

data class VoiceLanguageOption(
    val code: String,
    val sttLocale: String,
    val label: String,
    val flag: String,
    val description: String
)

@Composable
fun VoiceAiModal(
    isProcessing: Boolean,
    parsedExpense: ParsedVoiceExpense?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onProcessPrompt: (String) -> Unit,
    onProcessAudio: (String) -> Unit,
    onConfirmSave: (title: String, amount: Double, category: String, paymentMethod: String) -> Unit,
    onOpenManualAdd: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val languageOptions = remember {
        listOf(
            VoiceLanguageOption("ta-IN", "ta-IN", "தமிழ்", "🇮🇳", "Tamil"),
            VoiceLanguageOption("en-IN", "en-IN", "English", "🇺🇸", "English")
        )
    }

    var selectedLanguageCode by remember { mutableStateOf("ta-IN") }
    var modalState by remember { mutableStateOf(VoiceModalState.IDLE) }
    var recognizedSpokenText by remember { mutableStateOf("") }
    var rawInputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPermissionDenied by remember { mutableStateOf(false) }
    var liveAudioLevel by remember { mutableFloatStateOf(0f) }

    // Editable fields for confirmation
    var editTitle by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editCategory by remember { mutableStateOf("Food & Dining") }
    var editPaymentMethod by remember { mutableStateOf("UPI") }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Pulse animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Stop and cleanup speech recognizer safely without firing stale error callbacks
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

    // Process spoken or entered query
    fun handleProcessQuery(query: String) {
        if (query.isBlank()) return
        stopListeningSafely()
        modalState = VoiceModalState.PROCESSING
        onProcessPrompt(query)
    }

    // System Voice Dialog Launcher (fallback / standard intent)
    val systemVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val topSpoken = spokenResults?.firstOrNull()
            if (!topSpoken.isNullOrBlank()) {
                recognizedSpokenText = topSpoken
                handleProcessQuery(topSpoken)
            } else {
                errorMessage = "Could not hear clearly, please try again."
                modalState = VoiceModalState.ERROR
                Toast.makeText(context, "Could not hear clearly, please try again.", Toast.LENGTH_SHORT).show()
            }
        } else {
            errorMessage = "Could not hear clearly, please try again."
            modalState = VoiceModalState.ERROR
            Toast.makeText(context, "Could not hear clearly, please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchSystemVoiceIntent(langCode: String = selectedLanguageCode) {
        val targetOption = languageOptions.find { it.code == langCode } ?: languageOptions.first()
        val sttLocale = targetOption.sttLocale
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLocale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${targetOption.label}...")
            }
            systemVoiceLauncher.launch(intent)
        } catch (e: Exception) {
            errorMessage = "Could not hear clearly, please try again."
            modalState = VoiceModalState.ERROR
            Toast.makeText(context, "Could not hear clearly, please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for audio recording
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isPermissionDenied = false
            errorMessage = null
            modalState = VoiceModalState.IDLE
        } else {
            isPermissionDenied = true
            errorMessage = "Microphone permission is required for voice entry."
            modalState = VoiceModalState.ERROR
        }
    }

    fun startListening(langCode: String = selectedLanguageCode) {
        stopListeningSafely()
        errorMessage = null
        isPermissionDenied = false
        recognizedSpokenText = ""

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            isPermissionDenied = true
            errorMessage = "Microphone permission is required for voice entry."
            modalState = VoiceModalState.ERROR
            try {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            launchSystemVoiceIntent(langCode)
            return
        }

        val targetOption = languageOptions.find { it.code == langCode } ?: languageOptions.first()
        val sttLocale = targetOption.sttLocale

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLocale)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ta-IN", "en-IN", "en-US"))
                
                // Real-time audio capture settings
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${targetOption.label}...")
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
                    modalState = VoiceModalState.PROCESSING
                }
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Could not hear clearly, please try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Could not hear clearly, please try again."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please try again."
                        SpeechRecognizer.ERROR_NETWORK -> "Network issue. Please retry or type."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            isPermissionDenied = true
                            "Microphone permission is required for voice entry."
                        }
                        else -> "Could not hear clearly, please try again."
                    }
                    errorMessage = msg
                    modalState = VoiceModalState.ERROR
                    Toast.makeText(context, "Could not hear clearly, please try again.", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        recognizedSpokenText = text
                        handleProcessQuery(text)
                    } else {
                        errorMessage = "Could not hear clearly, please try again."
                        modalState = VoiceModalState.ERROR
                        Toast.makeText(context, "Could not hear clearly, please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
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
            launchSystemVoiceIntent(langCode)
        }
    }

    // Sync state with ViewModel
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            modalState = VoiceModalState.PROCESSING
        }
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
    }

    fun speakAloud(text: String) {
        val ttsEngine = tts ?: return
        if (!isTtsReady) return
        val targetLocale = if (selectedLanguageCode == "ta-IN") Locale.forLanguageTag("ta-IN") else Locale.US
        ttsEngine.language = targetLocale
        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZENITH_VOICE_OUT")
    }

    fun readOutTransaction(title: String, amount: Double, type: TransactionType, paymentMethod: String) {
        val isTamil = selectedLanguageCode == "ta-IN"
        val amtInt = if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format(Locale.US, "%.2f", amount)
        val textToSpeak = if (isTamil) {
            if (type == TransactionType.EXPENSE) {
                "$title $amtInt ரூபாய் $paymentMethod மூலம் செலவு பதிவு செய்யப்பட்டது."
            } else {
                "$title $amtInt ரூபாய் வருமானம் பதிவு செய்யப்பட்டது."
            }
        } else {
            if (type == TransactionType.EXPENSE) {
                "Added $title for $amtInt rupees via $paymentMethod."
            } else {
                "Recorded $title income of $amtInt rupees via $paymentMethod."
            }
        }
        speakAloud(textToSpeak)
    }

    LaunchedEffect(parsedExpense) {
        if (parsedExpense != null) {
            editTitle = parsedExpense.title
            editAmount = if (parsedExpense.amount > 0) {
                if (parsedExpense.amount % 1.0 == 0.0) String.format(Locale.US, "%.0f", parsedExpense.amount)
                else String.format(Locale.US, "%.2f", parsedExpense.amount)
            } else ""
            editType = parsedExpense.type
            editCategory = parsedExpense.category
            editPaymentMethod = parsedExpense.paymentMethod
            modalState = VoiceModalState.RESULT

            // Automatically Read Aloud the parsed transaction result
            readOutTransaction(parsedExpense.title, parsedExpense.amount, parsedExpense.type, parsedExpense.paymentMethod)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopListeningSafely()
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val examplePhrases = remember(selectedLanguageCode) {
        when (selectedLanguageCode) {
            "ta-IN" -> listOf(
                "இன்று படத்திற்கு 250 செலவு",
                "10 தக்காளி 50 ரூபாய்",
                "2 கிலோ வெங்காயம் 60 ரூபாய்",
                "ஒரு டீ 15 ரூபாய்",
                "பெட்ரோல் 500 ரூபாய்",
                "சம்பளம் 35000 வந்தது"
            )
            "tanglish" -> listOf(
                "Innaiku movie ki 250 selavu aachu",
                "Pathu thakkali",
                "2 kg vengayam 60 rs",
                "Rendu biryani 400 gpay",
                "Oru tea 15 rupees",
                "500 petrol phonepe"
            )
            else -> listOf(
                "Innaiku movie ki 250 selavu aachu",
                "Spent 250 on lunch",
                "Add 5 apples",
                "500 petrol via UPI",
                "Paid 1200 for groceries",
                "Got salary 35000"
            )
        }
    }

    val categoryList = listOf(
        "Food & Dining",
        "Shopping",
        "Transportation",
        "Bills & Utilities",
        "Entertainment",
        "Healthcare",
        "Salary & Income",
        "Education",
        "Housing & Rent",
        "Other"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateDarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF06B6D4)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Voice Entry",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "Tamil • English",
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
                        modifier = Modifier.size(32.dp).testTag("close_voice_modal")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateDarkTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Language Switcher Row (50/50 Split: Tamil & English) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateDarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languageOptions.forEach { lang ->
                        val isSelected = selectedLanguageCode == lang.code
                        val isListening = isSelected && modalState == VoiceModalState.LISTENING

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EmeraldDarkPrimary else Color.Transparent,
                            border = if (isListening) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, CyanDarkSecondary)
                            } else null,
                            modifier = Modifier
                                .weight(1f) // 50/50 Balanced Split
                                .height(38.dp)
                                .clickable {
                                    if (selectedLanguageCode != lang.code) {
                                        selectedLanguageCode = lang.code
                                        // Immediately update active recognition language without requiring modal restart
                                        if (modalState == VoiceModalState.LISTENING) {
                                            startListening(lang.code)
                                        }
                                    }
                                }
                                .testTag("btn_lang_${lang.code}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isListening) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(CyanDarkSecondary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "${lang.flag} ${lang.label}",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else SlateDarkTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content based on modalState
                when (modalState) {
                    VoiceModalState.IDLE, VoiceModalState.LISTENING, VoiceModalState.ERROR -> {
                        // --- Microphone Button ---
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(
                                    if (modalState == VoiceModalState.LISTENING) {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                CyanDarkSecondary.copy(alpha = 0.4f),
                                                Color.Transparent
                                            )
                                        )
                                    } else {
                                        Brush.radialGradient(
                                            colors = listOf(
                                                EmeraldDarkPrimary.copy(alpha = 0.2f),
                                                Color.Transparent
                                            )
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .scale(if (modalState == VoiceModalState.LISTENING) pulseScale else 1f)
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
                                            if (recognizedSpokenText.isNotBlank()) {
                                                handleProcessQuery(recognizedSpokenText)
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        if (modalState == VoiceModalState.LISTENING) {
                            Spacer(modifier = Modifier.height(10.dp))
                            // Live Audio Waveform Equalizer
                            Row(
                                modifier = Modifier.height(26.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val multipliers = listOf(0.4f, 0.7f, 1.0f, 1.3f, 1.0f, 0.7f, 0.4f)
                                multipliers.forEach { factor ->
                                    val barHeight = (8.dp + (18.dp * liveAudioLevel * factor)).coerceIn(4.dp, 24.dp)
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = when (modalState) {
                                VoiceModalState.LISTENING -> "Listening in ${languageOptions.find { it.code == selectedLanguageCode }?.label ?: "Tamil"}..."
                                VoiceModalState.ERROR -> errorMessage ?: "Could not hear clearly, please try again."
                                else -> when (selectedLanguageCode) {
                                    "ta-IN" -> "Tap microphone to speak (எ.கா. \"10 தக்காளி\" அல்லது \"150 டீ\")"
                                    else -> "Tap microphone to speak (e.g. \"Spent 250 on lunch via UPI\")"
                                }
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (modalState == VoiceModalState.LISTENING) CyanDarkSecondary else if (modalState == VoiceModalState.ERROR) ExpenseRed else SlateDarkTextPrimary
                        )

                        if (recognizedSpokenText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SlateDarkSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "\"$recognizedSpokenText\"",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Action buttons on ERROR state
                        if (modalState == VoiceModalState.ERROR) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isPermissionDenied) {
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                                    ) {
                                        Text("Allow Access", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { startListening() },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry", fontSize = 12.sp)
                                    }
                                }

                                if (onOpenManualAdd != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onOpenManualAdd()
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Enter Manually", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Direct Text / Sentence Input Bar ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateDarkSurfaceVariant, RoundedCornerShape(14.dp))
                                .border(1.dp, GlassBorderColor, RoundedCornerShape(14.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = rawInputText,
                                onValueChange = { rawInputText = it },
                                placeholder = { Text("or type e.g. Pathu thakkali / 5 apples", fontSize = 12.sp, color = SlateDarkTextSecondary) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboardController?.hide()
                                    if (rawInputText.isNotBlank()) {
                                        handleProcessQuery(rawInputText)
                                    }
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("voice_manual_text_input")
                            )

                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    if (rawInputText.isNotBlank()) {
                                        handleProcessQuery(rawInputText)
                                    }
                                },
                                modifier = Modifier.size(36.dp).testTag("btn_parse_text")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Parse",
                                    tint = EmeraldDarkPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // --- Example Voice Phrases ---
                        Text(
                            text = "Example Tamil & Voice Commands",
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
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
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

                    VoiceModalState.PROCESSING -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            color = EmeraldDarkPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Extracting Item, Quantity & Amount...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Processing single transaction with Zenith NLP Engine",
                            fontSize = 11.sp,
                            color = SlateDarkTextSecondary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    VoiceModalState.RESULT -> {
                        // --- Confirmation Card with Extracted Item + Quantity & Editable Fields ---
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SlateDarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TRANSACTION UNDERSTOOD",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldDarkPrimary,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (parsedExpense?.quantity != null || !parsedExpense?.item.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = EmeraldDarkPrimary.copy(alpha = 0.2f),
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "Single-Turn Parsed",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldDarkPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                // --- Extracted Item & Quantity Highlight Card ---
                                if (!parsedExpense?.item.isNullOrBlank() || parsedExpense?.quantity != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = EmeraldDarkPrimary.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldDarkPrimary.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Icon(
                                                    Icons.Default.Inventory2,
                                                    contentDescription = null,
                                                    tint = EmeraldDarkPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Item: ${parsedExpense?.item ?: "Item"}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = EmeraldDarkPrimary.copy(alpha = 0.25f),
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                val qVal = parsedExpense?.quantity ?: 1.0
                                                val qStr = if (qVal % 1.0 == 0.0) "${qVal.toInt()}" else "$qVal"
                                                Text(
                                                    text = "Qty: $qStr ${parsedExpense?.unit ?: "pcs"}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = EmeraldDarkPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Type Switcher (Expense / Income)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateDarkSurface, RoundedCornerShape(10.dp))
                                        .padding(3.dp)
                                ) {
                                    val isExp = editType == TransactionType.EXPENSE
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .background(if (isExp) ExpenseRed else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable { editType = TransactionType.EXPENSE },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Expense", fontWeight = FontWeight.Bold, color = if (isExp) Color.White else SlateDarkTextSecondary, fontSize = 12.sp)
                                    }

                                    val isInc = editType == TransactionType.INCOME
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .background(if (isInc) IncomeGreen else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable { editType = TransactionType.INCOME },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Income", fontWeight = FontWeight.Bold, color = if (isInc) Color.White else SlateDarkTextSecondary, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Amount Field
                                OutlinedTextField(
                                    value = editAmount,
                                    onValueChange = { editAmount = it },
                                    label = { Text("Amount ($currencySymbol)") },
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("voice_edit_amount")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Title Field
                                OutlinedTextField(
                                    value = editTitle,
                                    onValueChange = { editTitle = it },
                                    label = { Text("Title / Description") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("voice_edit_title")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Category Selector - Horizontally Scrollable Chip List
                                Text("Category", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateDarkTextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(categoryList) { cat ->
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

                                Spacer(modifier = Modifier.height(12.dp))

                                // Payment Method Dropdown
                                PaymentMethodDropdown(
                                    selectedMethod = editPaymentMethod,
                                    onMethodSelected = { editPaymentMethod = it },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // --- Read Aloud Speaker Button ---
                                OutlinedButton(
                                    onClick = {
                                        val amt = editAmount.toDoubleOrNull() ?: 0.0
                                        readOutTransaction(editTitle, amt, editType, editPaymentMethod)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanDarkSecondary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedLanguageCode == "ta-IN") "🔊 உரக்கக் கேட்கவும் (Read Aloud)" else "🔊 Read Aloud Summary",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val parsedAmount = editAmount.toDoubleOrNull() ?: 0.0
                        val isSaveEnabled = parsedAmount > 0 && editTitle.isNotBlank()

                        // Action Buttons: [Speak Again] & [Save Transaction]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    modalState = VoiceModalState.IDLE
                                    startListening()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Speak Again",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (isSaveEnabled) {
                                        val finalTitle = editTitle.trim()
                                        onConfirmSave(finalTitle, parsedAmount, editCategory, editPaymentMethod)
                                        Toast.makeText(context, "Added $currencySymbol$parsedAmount for $finalTitle", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Please enter a valid amount and title", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("confirm_voice_save_btn"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary),
                                enabled = isSaveEnabled
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Save Transaction",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    VoiceModalState.SUCCESS -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = IncomeGreen,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Transaction Saved!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SlateDarkTextPrimary)
                    }
                }
            }
        }
    }
}
