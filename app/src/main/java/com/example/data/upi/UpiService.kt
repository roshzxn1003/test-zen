package com.example.data.upi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * Parsed UPI payment information. Mirrors the NPCI UPI Linking Specification
 * query parameters used by every UPI app on Android.
 */
data class UpiPaymentInfo(
    val payeeAddress: String,      // pa  - VPA, e.g. "someone@okhdfcbank"
    val payeeName: String = "",    // pn  - display name / merchant
    val amount: String = "",       // am  - optional, only when the QR carries it
    val currency: String = "INR",  // cu
    val note: String = "",         // tn  - transaction note
    val txnRef: String = "",       // tr  - transaction reference id (only when provided)
    val merchantCode: String = ""  // mc  - optional merchant category code
)

enum class UpiPaymentStatus {
    INITIATED,
    PENDING,
    FAILED,
    CANCELLED,
    SUCCESSFUL
}

/**
 * Best-effort result of returning from a UPI app. UPI apps do NOT return a
 * reliable success/failure signal, so [returnedTxnRef] is only filled when a
 * supported app echoes one back and [userFlowState] is driven by the explicit
 * user confirmation, never assumed.
 */
data class UpiIntentResult(
    val launched: Boolean,
    val cancelled: Boolean,
    val returnedTxnRef: String?,
    val status: UpiPaymentStatus = UpiPaymentStatus.INITIATED,
    val message: String = ""
) {
    val needsConfirmation: Boolean get() = launched && !cancelled
}

/**
 * Standard UPI deep-link integration (NPCI spec). Zenith only initiates a
 * payment to a VPA through an installed UPI app; it never touches the user's
 * UPI PIN, OTP or credentials, and it never reads another app's history.
 */
object UpiService {

    private const val UPI_SCHEME = "upi"
    private const val UPI_AUTHORITY = "pay"

    private val VPA_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9.\\-_]{1,}@[a-zA-Z]{2,}$")

    fun isValidVpa(vpa: String): Boolean = VPA_REGEX.matches(vpa.trim())

    /**
     * Builds the `upi://pay` deep-link URI from the NPCI parameter set.
     */
    fun buildPaymentUri(info: UpiPaymentInfo): Uri {
        val builder = Uri.Builder()
            .scheme(UPI_SCHEME)
            .authority(UPI_AUTHORITY)
            .appendQueryParameter("pa", info.payeeAddress)
        if (info.payeeName.isNotBlank()) builder.appendQueryParameter("pn", info.payeeName)
        if (info.amount.isNotBlank()) builder.appendQueryParameter("am", info.amount)
        if (info.currency.isNotBlank()) builder.appendQueryParameter("cu", info.currency)
        if (info.note.isNotBlank()) builder.appendQueryParameter("tn", info.note)
        if (info.txnRef.isNotBlank()) builder.appendQueryParameter("tr", info.txnRef)
        if (info.merchantCode.isNotBlank()) builder.appendQueryParameter("mc", info.merchantCode)
        return builder.build()
    }

    /**
     * Launch intent for [UpiPaymentInfo]. When [targetPackage] is blank,
     * it generates a generic `upi://pay` Intent wrapped in `Intent.createChooser`
     * so the Android system displays the native app selector tray for any installed UPI app.
     */
    fun buildPayIntent(info: UpiPaymentInfo, targetPackage: String? = null, useChooser: Boolean = true): Intent? {
        val uri = buildPaymentUri(info)
        return if (targetPackage.isNullOrBlank()) {
            val baseIntent = Intent(Intent.ACTION_VIEW, uri)
            if (useChooser) Intent.createChooser(baseIntent, "Pay with any UPI App") else baseIntent
        } else {
            Intent(Intent.ACTION_VIEW, uri).setPackage(targetPackage)
        }
    }

    /**
     * Explicit generic intent chooser builder for NPCI `upi://pay` requests.
     */
    fun buildGenericChooserIntent(info: UpiPaymentInfo, title: String = "Pay with any UPI App"): Intent {
        val uri = buildPaymentUri(info)
        return Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), title)
    }

    /**
     * Installed apps that can handle `upi://pay` (requires a `<queries>` entry
     * in the manifest for Android 11+).
     */
    fun installedUpiApps(context: Context): List<UpiApp> {
        val probe = Intent(Intent.ACTION_VIEW).setData(Uri.parse("upi://pay"))
        return try {
            context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .sortedBy { it.activityInfo.packageName }
                .map { resolve ->
                    val label = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(resolve.activityInfo.packageName, 0)
                        ).toString()
                    } catch (e: Exception) {
                        resolve.activityInfo.packageName
                    }
                    UpiApp(packageName = resolve.activityInfo.packageName, label = label)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isAnyUpiAppInstalled(context: Context): Boolean = installedUpiApps(context).isNotEmpty()

    /**
     * Parses a raw QR payload into [UpiPaymentInfo]. Supports a full
     * `upi://pay?pa=...&pn=...` URI or a bare VPA.
     */
    fun parseQrPayload(raw: String): UpiPaymentInfo? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("upi://") || trimmed.startsWith("UPI://")) {
            val uri = Uri.parse(trimmed)
            val pa = uri.getQueryParameter("pa") ?: return null
            return UpiPaymentInfo(
                payeeAddress = pa,
                payeeName = uri.getQueryParameter("pn") ?: "",
                amount = uri.getQueryParameter("am") ?: "",
                currency = uri.getQueryParameter("cu") ?: "INR",
                note = uri.getQueryParameter("tn") ?: "",
                txnRef = uri.getQueryParameter("tr") ?: "",
                merchantCode = uri.getQueryParameter("mc") ?: ""
            )
        }

        return if (isValidVpa(trimmed)) UpiPaymentInfo(payeeAddress = trimmed) else null
    }

    /**
     * Best-effort reading of the ActivityResult after a UPI app returns.
     * Never treats result codes as proof of payment success.
     */
    fun mapResult(resultCode: Int, data: Intent?): UpiIntentResult {
        if (data == null) {
            return UpiIntentResult(
                launched = true,
                cancelled = resultCode == Activity.RESULT_CANCELED,
                returnedTxnRef = null,
                status = if (resultCode == Activity.RESULT_CANCELED) UpiPaymentStatus.CANCELLED else UpiPaymentStatus.FAILED,
                message = if (resultCode == Activity.RESULT_CANCELED) "Payment was cancelled." else "Payment failed. No successful payment was confirmed."
            )
        }

        val responseStr = data.getStringExtra("response") ?: ""
        val params = responseStr.split("&").mapNotNull {
            val parts = it.split("=")
            if (parts.size == 2) parts[0].lowercase() to parts[1] else null
        }.toMap()
        
        val statusStr = params["status"]?.lowercase() ?: ""
        
        val status = when {
            statusStr == "success" -> UpiPaymentStatus.SUCCESSFUL
            statusStr == "submitted" -> UpiPaymentStatus.PENDING
            statusStr == "failed" || statusStr == "failure" -> UpiPaymentStatus.FAILED
            resultCode == Activity.RESULT_CANCELED -> UpiPaymentStatus.CANCELLED
            else -> UpiPaymentStatus.FAILED
        }
        
        val message = when (status) {
            UpiPaymentStatus.SUCCESSFUL -> "Payment verified successfully."
            UpiPaymentStatus.PENDING -> "Your payment was initiated. We're waiting for confirmation."
            UpiPaymentStatus.CANCELLED -> "Payment was cancelled."
            UpiPaymentStatus.FAILED -> "Payment failed. No successful payment was confirmed."
            else -> "Payment initiated."
        }
        
        val txnRef = params["txnref"] 
            ?: data.extras?.getString("upitxnid")
            ?: data.extras?.getString("txnRef")
            ?: data.getStringExtra("upitxnid")
            ?: data.getStringExtra("txnRef")

        return UpiIntentResult(
            launched = true,
            cancelled = status == UpiPaymentStatus.CANCELLED,
            returnedTxnRef = txnRef?.takeIf { it.isNotBlank() },
            status = status,
            message = message
        )
    }

    const val GOOGLE_PAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"
    const val PHONEPE_PACKAGE = "com.phonepe.app"
    const val PAYTM_PACKAGE = "net.one97.paytm"
    const val BHIM_PACKAGE = "in.org.npci.upiapp"

    fun isGooglePayInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GOOGLE_PAY_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Builds a direct Intent for Google Pay (Tez) if installed, or falls back to system UPI chooser.
     */
    fun buildGooglePayIntent(info: UpiPaymentInfo, context: Context? = null): Intent {
        val uri = buildPaymentUri(info)
        val isGPayAvailable = context?.let { isGooglePayInstalled(it) } ?: true
        return if (isGPayAvailable) {
            Intent(Intent.ACTION_VIEW, uri).setPackage(GOOGLE_PAY_PACKAGE)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
    }

    /** Play Store fallback URI for a UPI app, when none is installed. */
    fun marketUri(packageName: String): Uri =
        Uri.parse("market://details?id=$packageName")

    /** System settings page to grant access to the app (manifest <queries>). */
    fun systemSettingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)
}

data class UpiApp(
    val packageName: String,
    val label: String
)
