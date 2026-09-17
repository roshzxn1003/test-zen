package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.FamilyMemberEntity
import com.example.data.models.FinanceScope
import com.example.data.upi.UpiService
import com.example.ui.theme.*

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

    val installedUpiApps = remember { UpiService.installedUpiApps(context) }
    val isGPayInstalled = remember { UpiService.isGooglePayInstalled(context) }
    val isPhonePeInstalled = remember { UpiService.isPhonePeInstalled(context) }
    val isPaytmInstalled = remember { UpiService.isPaytmInstalled(context) }
    val isBhimInstalled = remember { UpiService.isBhimInstalled(context) }

    fun launchScanner(targetPackage: String?) {
        try {
            val intent = UpiService.buildUpiScannerIntent(context, targetPackage)
            context.startActivity(intent)
            onDismiss()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open UPI app scanner: ${e.message}", Toast.LENGTH_SHORT).show()
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
                // Header
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
                                .background(PastelCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = PastelCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Scan UPI QR",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateDarkTextPrimary
                            )
                            Text(
                                text = "Redirect to your UPI app's scanner",
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

                // Primary 1-tap Chooser Button
                Button(
                    onClick = { launchScanner(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_launch_upi_chooser"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PastelCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open UPI Scanner (Choose App)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "OR SCAN DIRECTLY WITH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = SlateDarkTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Direct Installed Apps List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Google Pay
                    UpiAppRedirectTile(
                        appName = "Google Pay",
                        badge = if (isGPayInstalled) "Installed" else "Available",
                        icon = Icons.Default.Payment,
                        accentColor = Color(0xFF4285F4),
                        onClick = { launchScanner(UpiService.GOOGLE_PAY_PACKAGE) }
                    )

                    // PhonePe
                    UpiAppRedirectTile(
                        appName = "PhonePe",
                        badge = if (isPhonePeInstalled) "Installed" else "Available",
                        icon = Icons.Default.QrCodeScanner,
                        accentColor = Color(0xFF5F259F),
                        onClick = { launchScanner(UpiService.PHONEPE_PACKAGE) }
                    )

                    // Paytm
                    UpiAppRedirectTile(
                        appName = "Paytm",
                        badge = if (isPaytmInstalled) "Installed" else "Available",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = Color(0xFF00BAF2),
                        onClick = { launchScanner(UpiService.PAYTM_PACKAGE) }
                    )

                    // BHIM
                    if (isBhimInstalled) {
                        UpiAppRedirectTile(
                            appName = "BHIM UPI",
                            badge = "Installed",
                            icon = Icons.Default.AccountBalance,
                            accentColor = Color(0xFF00897B),
                            onClick = { launchScanner(UpiService.BHIM_PACKAGE) }
                        )
                    }

                    // Any other installed UPI apps from package manager query
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
                            onClick = { launchScanner(customApp.packageName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SlateDarkTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Opens your UPI scanner directly. Once paid, Zenith automatically logs the transaction.",
                            fontSize = 11.sp,
                            color = SlateDarkTextSecondary
                        )
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
                        text = "Tap to open scanner",
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
