package com.example.data.upi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Parsed UPI payment information.
 *
 * Standard UPI parameters:
 * pa = Payee VPA
 * pn = Payee name
 * am = Amount
 * cu = Currency
 * tn = Transaction note
 * tr = Transaction reference
 * mc = Merchant category code
 */
data class UpiPaymentInfo(
    val payeeAddress: String,
    val payeeName: String = "",
    val amount: String = "",
    val currency: String = "INR",
    val note: String = "",
    val txnRef: String = "",
    val merchantCode: String = ""
)

/**
 * Possible states of a UPI payment.
 */
enum class UpiPaymentStatus {
    INITIATED,
    PENDING,
    FAILED,
    CANCELLED,
    SUCCESSFUL
}

/**
 * Result returned after coming back from a UPI application.
 */
data class UpiIntentResult(
    val launched: Boolean,
    val cancelled: Boolean,
    val returnedTxnRef: String?,
    val status: UpiPaymentStatus = UpiPaymentStatus.INITIATED,
    val message: String = ""
) {
    val needsConfirmation: Boolean
        get() = launched && !cancelled
}

/**
 * UPI payment service.
 *
 * This class:
 * - Parses UPI QR data
 * - Creates standard upi://pay URLs
 * - Opens installed UPI apps
 * - Supports Google Pay
 * - Supports PhonePe, Paytm and BHIM detection
 * - Handles the response from UPI applications
 *
 * It NEVER handles the user's UPI PIN, OTP or bank credentials.
 */
object UpiService {

    private const val UPI_SCHEME = "upi"
    private const val UPI_AUTHORITY = "pay"

    /**
     * VPA validation.
     *
     * Examples:
     * someone@okhdfcbank
     * 9876543210@paytm
     * merchant@oksbi
     */
    private val VPA_REGEX = Regex(
        "^[a-zA-Z0-9][a-zA-Z0-9.\\-_+]{1,255}@[a-zA-Z0-9.\\-_]{2,64}$"
    )

    // ------------------------------------------------------------
    // VPA VALIDATION
    // ------------------------------------------------------------

    fun isValidVpa(vpa: String): Boolean {
        return VPA_REGEX.matches(vpa.trim())
    }

    // ------------------------------------------------------------
    // AMOUNT NORMALIZATION
    // ------------------------------------------------------------

    /**
     * Converts the entered amount into a standard 2-decimal format.
     *
     * Examples:
     *
     * "2"       -> "2.00"
     * "2.5"     -> "2.50"
     * "10"      -> "10.00"
     * "100.75"  -> "100.75"
     */
    private fun normalizeAmount(amount: String): String? {

        val cleaned = amount.trim()

        if (cleaned.isBlank()) {
            return null
        }

        return try {

            val value = BigDecimal(cleaned)

            // Amount must be greater than zero
            if (value <= BigDecimal.ZERO) {
                null
            } else {

                value
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
            }

        } catch (e: NumberFormatException) {
            null
        }
    }

    // ------------------------------------------------------------
    // BUILD UPI PAYMENT URI
    // ------------------------------------------------------------

    /**
     * Builds:
     *
     * upi://pay?pa=...&pn=...&am=2.00&cu=INR
     */
    fun buildPaymentUri(info: UpiPaymentInfo): Uri {

        val builder = Uri.Builder()
            .scheme(UPI_SCHEME)
            .authority(UPI_AUTHORITY)
            .appendQueryParameter(
                "pa",
                info.payeeAddress.trim()
            )

        // Payee name
        if (info.payeeName.isNotBlank()) {

            builder.appendQueryParameter(
                "pn",
                info.payeeName.trim()
            )
        }

        // Amount
        if (info.amount.isNotBlank()) {

            val normalizedAmount = normalizeAmount(info.amount)

            if (normalizedAmount != null) {

                builder.appendQueryParameter(
                    "am",
                    normalizedAmount
                )
            }
        }

        // Currency
        builder.appendQueryParameter(
            "cu",
            if (info.currency.isBlank()) {
                "INR"
            } else {
                info.currency.trim()
            }
        )

        // Transaction note
        if (info.note.isNotBlank()) {

            builder.appendQueryParameter(
                "tn",
                info.note.trim()
            )
        }

        // Transaction reference
        if (info.txnRef.isNotBlank()) {

            builder.appendQueryParameter(
                "tr",
                info.txnRef.trim()
            )
        }

        // Merchant code
        if (info.merchantCode.isNotBlank()) {

            builder.appendQueryParameter(
                "mc",
                info.merchantCode.trim()
            )
        }

        return builder.build()
    }

    // ------------------------------------------------------------
    // GENERIC PAYMENT INTENT
    // ------------------------------------------------------------

    /**
     * Creates a UPI payment Intent.
     *
     * If targetPackage is empty:
     *     Shows the Android UPI app chooser.
     *
     * If targetPackage is provided:
     *     Opens that specific UPI application.
     */
    fun buildPayIntent(
        info: UpiPaymentInfo,
        targetPackage: String? = null,
        useChooser: Boolean = true
    ): Intent {

        val uri = buildPaymentUri(info)

        return if (targetPackage.isNullOrBlank()) {

            val baseIntent = Intent(
                Intent.ACTION_VIEW,
                uri
            )

            if (useChooser) {

                Intent.createChooser(
                    baseIntent,
                    "Pay with any UPI App"
                )

            } else {

                baseIntent
            }

        } else {

            Intent(
                Intent.ACTION_VIEW,
                uri
            ).setPackage(targetPackage)
        }
    }

    // ------------------------------------------------------------
    // GENERIC UPI CHOOSER
    // ------------------------------------------------------------

    /**
     * Opens the Android UPI app chooser.
     */
    fun buildGenericChooserIntent(
        info: UpiPaymentInfo,
        title: String = "Pay with any UPI App"
    ): Intent {

        val uri = buildPaymentUri(info)

        val intent = Intent(
            Intent.ACTION_VIEW,
            uri
        )

        return Intent.createChooser(
            intent,
            title
        )
    }

    // ------------------------------------------------------------
    // CHECK INSTALLED UPI APPS
    // ------------------------------------------------------------

    /**
     * Returns all installed applications that can handle:
     *
     * upi://pay
     */
    fun installedUpiApps(
        context: Context
    ): List<UpiApp> {

        val probe = Intent(
            Intent.ACTION_VIEW
        ).setData(
            Uri.parse("upi://pay")
        )

        return try {

            context.packageManager
                .queryIntentActivities(
                    probe,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                .sortedBy {
                    it.activityInfo.packageName
                }
                .map { resolveInfo ->

                    val packageName =
                        resolveInfo.activityInfo.packageName

                    val label = try {

                        context.packageManager
                            .getApplicationLabel(
                                context.packageManager
                                    .getApplicationInfo(
                                        packageName,
                                        0
                                    )
                            )
                            .toString()

                    } catch (e: Exception) {

                        packageName
                    }

                    UpiApp(
                        packageName = packageName,
                        label = label
                    )
                }

        } catch (e: Exception) {

            emptyList()
        }
    }

    /**
     * Checks whether at least one UPI application is installed.
     */
    fun isAnyUpiAppInstalled(
        context: Context
    ): Boolean {

        return installedUpiApps(context).isNotEmpty()
    }

    // ------------------------------------------------------------
    // QR CODE PARSER
    // ------------------------------------------------------------

    /**
     * Parses raw QR content.
     *
     * Supports:
     *
     * 1. upi://pay?... URLs
     * 2. Text containing a UPI URL
     * 3. Bare VPA
     *
     * Examples:
     *
     * upi://pay?pa=merchant@okaxis&pn=Merchant
     *
     * merchant@okaxis
     */
    fun parseQrPayload(
        raw: String
    ): UpiPaymentInfo? {

        val trimmed = raw.trim()

        if (trimmed.isBlank()) {
            return null
        }

        // --------------------------------------------------------
        // 1. UPI URL
        // --------------------------------------------------------

        val upiIndex = trimmed.indexOf(
            "upi://pay",
            ignoreCase = true
        )

        if (upiIndex >= 0) {

            try {

                val upiUriString =
                    trimmed.substring(upiIndex)

                val uri =
                    Uri.parse(upiUriString)

                val queryParamMap =
                    mutableMapOf<String, String>()

                uri.queryParameterNames.forEach { name ->

                    uri.getQueryParameter(name)?.let { value ->

                        queryParamMap[
                            name.lowercase(Locale.ROOT)
                        ] = value
                    }
                }

                val pa =
                    queryParamMap["pa"]

                if (
                    !pa.isNullOrBlank() &&
                    isValidVpa(pa)
                ) {

                    return UpiPaymentInfo(

                        payeeAddress =
                            pa.trim(),

                        payeeName =
                            queryParamMap["pn"] ?: "",

                        amount =
                            queryParamMap["am"] ?: "",

                        currency =
                            queryParamMap["cu"]
                                ?: "INR",

                        note =
                            queryParamMap["tn"]
                                ?: "",

                        txnRef =
                            queryParamMap["tr"]
                                ?: "",

                        merchantCode =
                            queryParamMap["mc"]
                                ?: ""
                    )
                }

            } catch (e: Exception) {

                // Continue to VPA fallback
            }
        }

        // --------------------------------------------------------
        // 2. SEARCH FOR VPA INSIDE TEXT
        // --------------------------------------------------------

        if (trimmed.contains("@")) {

            val tokens =
                trimmed.split(
                    Regex("[\\s:?&=;,/|]")
                )

            val candidate =
                tokens.firstOrNull {

                    isValidVpa(
                        it.trim()
                    )
                }

            if (candidate != null) {

                return UpiPaymentInfo(
                    payeeAddress =
                        candidate.trim(),

                    payeeName =
                        "UPI Merchant"
                )
            }
        }

        // --------------------------------------------------------
        // 3. BARE VPA
        // --------------------------------------------------------

        return if (
            isValidVpa(trimmed)
        ) {

            UpiPaymentInfo(
                payeeAddress =
                    trimmed
            )

        } else {

            null
        }
    }

    // ------------------------------------------------------------
    // HANDLE UPI RESULT
    // ------------------------------------------------------------

    /**
     * Handles the result returned from a UPI application.
     *
     * IMPORTANT:
     *
     * UPI applications do not always return a reliable result.
     * Therefore the app should not assume SUCCESS just because
     * the UPI application was opened.
     */
    fun mapResult(
        resultCode: Int,
        data: Intent?
    ): UpiIntentResult {

        // User returned without response data
        if (data == null) {

            return UpiIntentResult(

                launched = true,

                cancelled = false,

                returnedTxnRef = null,

                status =
                    UpiPaymentStatus.INITIATED,

                message =
                    "Returned from UPI app. " +
                    "Please confirm whether the payment was completed."
            )
        }

        // --------------------------------------------------------
        // UPI RESPONSE STRING
        // --------------------------------------------------------

        val responseStr =
            data.getStringExtra("response")
                ?: ""

        val params =
            responseStr
                .split("&")
                .mapNotNull {

                    val parts =
                        it.split(
                            "=",
                            limit = 2
                        )

                    if (
                        parts.size == 2
                    ) {

                        parts[0]
                            .lowercase(Locale.ROOT) to
                                parts[1]

                    } else {

                        null
                    }
                }
                .toMap()

        val statusStr =
            params["status"]
                ?.lowercase(Locale.ROOT)
                ?: ""

        // --------------------------------------------------------
        // STATUS
        // --------------------------------------------------------

        val status =
            when {

                statusStr == "success" ->
                    UpiPaymentStatus.SUCCESSFUL

                statusStr == "submitted" ->
                    UpiPaymentStatus.PENDING

                statusStr == "failed" ||
                        statusStr == "failure" ->
                    UpiPaymentStatus.FAILED

                resultCode == Activity.RESULT_CANCELED ->
                    UpiPaymentStatus.CANCELLED

                else ->
                    UpiPaymentStatus.INITIATED
            }

        // --------------------------------------------------------
        // MESSAGE
        // --------------------------------------------------------

        val message =
            when (status) {

                UpiPaymentStatus.SUCCESSFUL ->
                    "Payment verified successfully!"

                UpiPaymentStatus.PENDING ->
                    "Payment submitted and pending bank confirmation."

                UpiPaymentStatus.FAILED ->
                    "Payment was reported as failed by the UPI app."

                UpiPaymentStatus.CANCELLED ->
                    "Payment was cancelled."

                else ->
                    "Returned from UPI app. " +
                    "Please confirm whether the payment was completed."
            }

        // --------------------------------------------------------
        // TRANSACTION REFERENCE
        // --------------------------------------------------------

        val txnRef =
            params["txnref"]
                ?: data.extras
                    ?.getString("upitxnid")
                ?: data.extras
                    ?.getString("txnRef")
                ?: data.getStringExtra(
                    "upitxnid"
                )
                ?: data.getStringExtra(
                    "txnRef"
                )

        return UpiIntentResult(

            launched = true,

            cancelled =
                status ==
                        UpiPaymentStatus.CANCELLED,

            returnedTxnRef =
                txnRef?.takeIf {
                    it.isNotBlank()
                },

            status = status,

            message = message
        )
    }

    // ------------------------------------------------------------
    // UPI APP PACKAGE NAMES
    // ------------------------------------------------------------

    const val GOOGLE_PAY_PACKAGE =
        "com.google.android.apps.nbu.paisa.user"

    const val PHONEPE_PACKAGE =
        "com.phonepe.app"

    const val PAYTM_PACKAGE =
        "net.one97.paytm"

    const val BHIM_PACKAGE =
        "in.org.npci.upiapp"

    // ------------------------------------------------------------
    // GOOGLE PAY
    // ------------------------------------------------------------

    /**
     * Checks whether Google Pay is installed.
     */
    fun isGooglePayInstalled(
        context: Context
    ): Boolean {

        return try {

            context.packageManager
                .getPackageInfo(
                    GOOGLE_PAY_PACKAGE,
                    0
                )

            true

        } catch (e: Exception) {

            false
        }
    }

    /**
     * Builds a Google Pay Intent.
     *
     * If Google Pay is installed:
     *     Opens Google Pay directly.
     *
     * Otherwise:
     *     Opens the normal UPI chooser.
     */
    fun buildGooglePayIntent(
        info: UpiPaymentInfo,
        context: Context? = null
    ): Intent {

        val uri =
            buildPaymentUri(info)

        val isGPayAvailable =
            context?.let {
                isGooglePayInstalled(it)
            } ?: true

        return if (isGPayAvailable) {

            Intent(
                Intent.ACTION_VIEW,
                uri
            ).setPackage(
                GOOGLE_PAY_PACKAGE
            )

        } else {

            Intent(
                Intent.ACTION_VIEW,
                uri
            )
        }
    }

    // ------------------------------------------------------------
    // PHONEPE
    // ------------------------------------------------------------

    fun buildPhonePeIntent(
        info: UpiPaymentInfo,
        context: Context? = null
    ): Intent {

        val uri =
            buildPaymentUri(info)

        val isInstalled =
            context?.let {

                try {

                    it.packageManager
                        .getPackageInfo(
                            PHONEPE_PACKAGE,
                            0
                        )

                    true

                } catch (e: Exception) {

                    false
                }

            } ?: true

        return if (isInstalled) {

            Intent(
                Intent.ACTION_VIEW,
                uri
            ).setPackage(
                PHONEPE_PACKAGE
            )

        } else {

            Intent(
                Intent.ACTION_VIEW,
                uri
            )
        }
    }

    // ------------------------------------------------------------
    // PAYTM
    // ------------------------------------------------------------

    fun buildPaytmIntent(
        info: UpiPaymentInfo,
        context: Context? = null
    ): Intent {

        val uri =
            buildPaymentUri(info)

        val isInstalled =
            context?.let {

                try {

                    it.packageManager
                        .getPackageInfo(
                            PAYTM_PACKAGE,
                            0
                        )

                    true

                } catch (e: Exception) {

                    false
                }

            } ?: true

        return if (isInstalled) {

            Intent(
                Intent.ACTION_VIEW,
                uri
            ).setPackage(
                PAYTM_PACKAGE
            )

        } else {

            Intent(
                Intent.ACTION_VIEW,
                uri
            )
        }
    }

    // ------------------------------------------------------------
    // PLAY STORE FALLBACK
    // ------------------------------------------------------------

    /**
     * Opens the Play Store page of a UPI app.
     */
    fun marketUri(
        packageName: String
    ): Uri {

        return Uri.parse(
            "market://details?id=$packageName"
        )
    }

    // ------------------------------------------------------------
    // SYSTEM SETTINGS
    // ------------------------------------------------------------

    fun systemSettingsIntent(): Intent {

        return Intent(
            Settings.ACTION_SETTINGS
        )
    }

    // ------------------------------------------------------------
    // DEBUG HELPER
    // ------------------------------------------------------------

    /**
     * Useful for debugging.
     *
     * Example output:
     *
     * UPI URI:
     * upi://pay?pa=xxx%40ybl&pn=ARUN&am=2.00&cu=INR
     */
    fun getDebugPaymentUri(
        info: UpiPaymentInfo
    ): String {

        return buildPaymentUri(info).toString()
    }
}

/**
 * Represents an installed UPI application.
 */
data class UpiApp(
    val packageName: String,
    val label: String
)