package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.upi.UpiApp
import com.example.data.upi.UpiIntentResult
import com.example.data.upi.UpiPaymentInfo
import com.example.data.upi.UpiPaymentStatus
import com.example.data.upi.UpiService
import com.example.ui.theme.*
import java.util.Locale
import java.util.UUID

/**
 * Request to start a UPI payment. The parent form sets this after validation.
 */
data class UpiPayRequest(
    val amount: Double,
    val purpose: String,
    val vpa: String,
    val targetPackage: String? = null
)

/**
 * Shared "pay via UPI" flow used by every UPI entry point: app picker,
 * direct intent launch (such as Google Pay), and the explicit "Payment completed?" confirmation.
 * Never assumes success — [onSaveConfirmed] is only called after the user
 * confirms in the UPI app. The returned txn reference is passed through when
 * a supported UPI app echoes one back; otherwise null.
 */
@Composable
fun UpiPaymentFlow(
    payRequest: UpiPayRequest?,
    currencySymbol: String,
    onSaveConfirmed: (upiTransactionId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }
    var showPaymentConfirm by remember { mutableStateOf(false) }
    var upiIntentResult by remember { mutableStateOf<UpiIntentResult?>(null) }
    var returnedTxnRef by remember { mutableStateOf<String?>(null) }

    val upiApps = remember { UpiService.installedUpiApps(context) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val mapped = UpiService.mapResult(result.resultCode, result.data)
        upiIntentResult = mapped
        if (mapped.launched) {
            returnedTxnRef = mapped.returnedTxnRef
            showPaymentConfirm = true
        } else {
            Toast.makeText(context, "Payment flow cancelled or could not be opened.", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    fun launchPayment(targetPackage: String?) {
        val request = payRequest ?: return
        val info = UpiPaymentInfo(
            payeeAddress = request.vpa.trim(),
            payeeName = request.purpose.trim(),
            amount = String.format(Locale.US, "%.2f", request.amount),
            currency = "INR",
            note = request.purpose.trim(),
            txnRef = "ZNTH-" + UUID.randomUUID().toString().take(12)
        )
        val intent = UpiService.buildPayIntent(info, targetPackage)
        if (intent == null) {
            Toast.makeText(context, "No UPI app found on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        showAppPicker = false
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No UPI app available to handle this payment.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(payRequest) {
        if (payRequest != null) {
            returnedTxnRef = null
            if (!payRequest.targetPackage.isNullOrBlank()) {
                showAppPicker = false
                launchPayment(payRequest.targetPackage)
            } else {
                showAppPicker = true
            }
        }
    }

    // --- UPI APP PICKER ---
    if (showAppPicker) {
        val request = payRequest
        if (request != null) {
            Dialog(onDismissRequest = { showAppPicker = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SlateDarkSurface,
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Pay with",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateDarkTextPrimary
                        )
                        Text(
                            text = "Select a UPI app installed on this device.",
                            fontSize = 12.sp,
                            color = SlateDarkTextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (upiApps.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GoalAmber.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, GoalAmber.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "No UPI app found on this device.",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateDarkTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Install Google Pay, PhonePe or BHIM to pay via UPI. You can still record this as a manual UPI transaction.",
                                        fontSize = 11.sp,
                                        color = SlateDarkTextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            val store = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=upi%20payment"))
                                            try { context.startActivity(store) } catch (e: Exception) { }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                                    ) {
                                        Text("Open Play Store", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF06B6D4).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { launchPayment(null) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "All UPI apps",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateDarkTextPrimary
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text("Choose app", fontSize = 11.sp, color = SlateDarkTextSecondary)
                                    }
                                }
                                upiApps.forEach { app: UpiApp ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SlateDarkSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { launchPayment(app.packageName) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = EmeraldDarkPrimary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = app.label,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SlateDarkTextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- PAYMENT CONFIRMATION ---
    if (showPaymentConfirm) {
        val request = payRequest
        if (request != null) {
            Dialog(onDismissRequest = { }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SlateDarkSurface,
                    border = BorderStroke(1.dp, GlassBorderColor),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val statusTitle = when (upiIntentResult?.status) {
                            UpiPaymentStatus.SUCCESSFUL -> "Payment Successful"
                            UpiPaymentStatus.PENDING -> "Payment Pending"
                            UpiPaymentStatus.FAILED -> "Payment Failed"
                            UpiPaymentStatus.CANCELLED -> "Payment Cancelled"
                            else -> "Payment Initiated"
                        }
                        
                        val statusDesc = upiIntentResult?.message ?: "Your payment was initiated. We're waiting for confirmation."
                        val statusColor = when (upiIntentResult?.status) {
                            UpiPaymentStatus.SUCCESSFUL -> EmeraldDarkPrimary
                            UpiPaymentStatus.PENDING -> GoalAmber
                            UpiPaymentStatus.FAILED,
                            UpiPaymentStatus.CANCELLED -> ExpenseRed
                            else -> SlateDarkTextPrimary
                        }
                        
                        Text(
                            text = statusTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = statusDesc,
                            fontSize = 13.sp,
                            color = SlateDarkTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "$currencySymbol${String.format(Locale.US, "%.2f", request.amount)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF06B6D4)
                        )
                        if (request.purpose.isNotBlank()) {
                            Text(
                                text = request.purpose,
                                fontSize = 13.sp,
                                color = SlateDarkTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (request.vpa.isNotBlank()) {
                            Text(
                                text = request.vpa,
                                fontSize = 12.sp,
                                color = SlateDarkTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        
                        val isFailed = upiIntentResult?.status == UpiPaymentStatus.FAILED

                        Text(
                            text = "Save this payment to your ledger?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateDarkTextMuted,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                showPaymentConfirm = false
                                onSaveConfirmed(returnedTxnRef)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_upi_save"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Transaction", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                showPaymentConfirm = false
                                showAppPicker = false
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Don't Save / Cancel", fontSize = 13.sp, color = SlateDarkTextSecondary)
                        }
                    }
                }
            }
        }
    }
}
