package com.example.data.upi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLDecoder
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
    val merchantCode: String = "",
    val rawUri: String? = null
) {
    /**
     * Returns true if the QR code explicitly contained a non-zero payment amount (Dynamic QR).
     * Returns false for Static QRs where the amount must be entered by the user.
     */
    val isDynamic: Boolean
        get() = amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
}

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

        // Payee name (sanitized for NPCI compliance: max 50 chars, no illegal punctuation)
        if (info.payeeName.isNotBlank()) {
            val cleanPn = info.payeeName.trim()
                .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == '.' }
                .take(50)
            if (cleanPn.isNotBlank()) {
                builder.appendQueryParameter(
                    "pn",
                    cleanPn
                )
            }
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

        // Transaction note (sanitized for NPCI compliance: max 80 chars)
        if (info.note.isNotBlank()) {
            val cleanNote = info.note.trim()
                .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == '.' }
                .take(80)
            if (cleanNote.isNotBlank()) {
                builder.appendQueryParameter(
                    "tn",
                    cleanNote
                )
            }
        }

        // Transaction reference: ONLY include if it came from the merchant QR or verified merchant!
        // DO NOT generate random synthetic tr for P2P transactions as NPCI will reject them!
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

    /**
     * Builds a payment URI for a scanned QR code, preserving all original
     * cryptographic signatures (sign=...), orgid, mode, and merchant parameters.
     */
    fun buildQrPaymentUri(rawUri: String, amount: Double?): Uri {
        val parsedUri = Uri.parse(rawUri)
        if (amount == null || amount <= 0.0) {
            return parsedUri
        }

        val normalizedAmount = normalizeAmount(amount.toString()) ?: String.format(Locale.US, "%.2f", amount)
        val builder = parsedUri.buildUpon()
        builder.clearQuery()
        var amFound = false
        for (param in parsedUri.queryParameterNames) {
            if (param.equals("am", ignoreCase = true)) {
                builder.appendQueryParameter(param, normalizedAmount)
                amFound = true
            } else {
                parsedUri.getQueryParameter(param)?.let {
                    builder.appendQueryParameter(param, it)
                }
            }
        }
        if (!amFound) {
            builder.appendQueryParameter("am", normalizedAmount)
        }
        return builder.build()
    }

    // ------------------------------------------------------------
    // GENERIC PAYMENT INTENT
    // ------------------------------------------------------------

    /**
     * Creates a UPI payment Intent using an already built or preserved URI.
     */
    fun buildPayIntentWithUri(
        uri: Uri,
        targetPackage: String? = null,
        useChooser: Boolean = true
    ): Intent {
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
        return buildPayIntentWithUri(uri, targetPackage, useChooser)
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
    // QR CODE PARSER (Standard UPI, BharatQR EMVCo & Web-wrapped)
    // ------------------------------------------------------------

    /**
     * Parses TLV (Tag-Length-Value) encoded strings used in EMVCo and BharatQR specifications.
     */
    fun parseEmvCoTlv(data: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var index = 0
        while (index + 4 <= data.length) {
            val tag = data.substring(index, index + 2)
            val length = data.substring(index + 2, index + 4).toIntOrNull() ?: break
            index += 4
            if (index + length > data.length) break
            val value = data.substring(index, index + length)
            map[tag] = value
            index += length
        }
        return map
    }

    /**
     * Parses BharatQR (EMVCo specification) format commonly printed on Indian POS
     * terminals, card swipe machines, and retail soundboxes (starts with 000201...).
     */
    fun parseBharatQr(raw: String): UpiPaymentInfo? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("000201")) return null

        val tags = parseEmvCoTlv(trimmed)
        if (tags.isEmpty()) return null

        var vpa: String? = null
        var merchantCode = tags["52"]?.trim().orEmpty()

        // In NPCI BharatQR, tags 26 to 51 define merchant account information for UPI
        for (tagKey in listOf("26", "27", "28", "29", "30", "31")) {
            val merchantData = tags[tagKey] ?: continue
            val subTags = parseEmvCoTlv(merchantData)
            val candidateVpa = subTags["01"]
            if (!candidateVpa.isNullOrBlank() && isValidVpa(candidateVpa)) {
                vpa = candidateVpa
                if (merchantCode.isBlank()) {
                    merchantCode = subTags["02"]?.trim().orEmpty()
                }
                break
            }
            for (subVal in subTags.values) {
                if (isValidVpa(subVal)) {
                    vpa = subVal
                    break
                }
            }
            if (vpa != null) break
        }

        if (vpa == null) {
            for (value in tags.values) {
                val candidate = value.split(Regex("[\\s:?&=;,/|]")).firstOrNull { isValidVpa(it.trim()) }
                if (candidate != null) {
                    vpa = candidate.trim()
                    break
                }
            }
        }

        if (vpa.isNullOrBlank() || !isValidVpa(vpa)) return null

        val merchantName = tags["59"]?.trim().orEmpty()
        val amount = tags["54"]?.trim().orEmpty()
        val currency = if (tags["53"] == "356") "INR" else "INR"

        var note = ""
        var ref = ""
        tags["62"]?.let { addl ->
            val addlTags = parseEmvCoTlv(addl)
            ref = addlTags["05"] ?: addlTags["01"] ?: ""
            note = addlTags["08"] ?: addlTags["03"] ?: ""
        }

        val cleanName = merchantName.ifBlank { "UPI Merchant" }
        val rawUri = "upi://pay?pa=$vpa&pn=${Uri.encode(cleanName)}${if (amount.isNotBlank()) "&am=$amount" else ""}&cu=$currency"

        return UpiPaymentInfo(
            payeeAddress = vpa,
            payeeName = cleanName,
            amount = amount,
            currency = currency,
            note = note,
            txnRef = ref,
            merchantCode = merchantCode,
            rawUri = rawUri
        )
    }

    /**
     * Parses standard upi://pay URLs with query parameters.
     */
    fun parseUpiUrl(upiUrlString: String): UpiPaymentInfo? {
        val upiIndex = upiUrlString.indexOf("upi://pay", ignoreCase = true)
        if (upiIndex < 0) return null
        return try {
            val uriString = upiUrlString.substring(upiIndex)
            val uri = Uri.parse(uriString)
            val queryParamMap = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { name ->
                uri.getQueryParameter(name)?.let { value ->
                    queryParamMap[name.lowercase(Locale.ROOT)] = value
                }
            }

            val pa = queryParamMap["pa"]
            if (!pa.isNullOrBlank() && isValidVpa(pa.trim())) {
                val pn = (queryParamMap["pn"] ?: "").replace("+", " ").trim()
                val am = (queryParamMap["am"] ?: "").replace(",", "").replace("₹", "").replace("Rs.", "").trim()
                val cu = queryParamMap["cu"] ?: "INR"
                val tn = (queryParamMap["tn"] ?: "").replace("+", " ").trim()
                val tr = queryParamMap["tr"] ?: ""
                val mc = queryParamMap["mc"] ?: ""
                UpiPaymentInfo(
                    payeeAddress = pa.trim(),
                    payeeName = pn,
                    amount = am,
                    currency = cu,
                    note = tn,
                    txnRef = tr,
                    merchantCode = mc,
                    rawUri = uriString
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses web-wrapped URLs (e.g. https://upiqr.in/..., https://pay.google.com/...)
     * that contain UPI parameters or percent-encoded UPI intents.
     */
    fun parseWebWrappedUpi(url: String): UpiPaymentInfo? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return try {
            val uri = Uri.parse(trimmed)
            for (paramName in uri.queryParameterNames) {
                val paramVal = uri.getQueryParameter(paramName) ?: continue
                val decoded = try { URLDecoder.decode(paramVal, "UTF-8") } catch (e: Exception) { paramVal }
                if (decoded.contains("upi://pay", ignoreCase = true)) {
                    val embedded = parseUpiUrl(decoded)
                    if (embedded != null) return embedded
                }
            }

            val pa = uri.getQueryParameter("pa")
            if (!pa.isNullOrBlank() && isValidVpa(pa.trim())) {
                val pn = (uri.getQueryParameter("pn") ?: "").replace("+", " ").trim()
                val am = uri.getQueryParameter("am")?.replace(",", "")?.replace("₹", "")?.replace("Rs.", "")?.trim() ?: ""
                val cu = uri.getQueryParameter("cu") ?: "INR"
                val tn = (uri.getQueryParameter("tn") ?: "").replace("+", " ").trim()
                val tr = uri.getQueryParameter("tr") ?: ""
                val mc = uri.getQueryParameter("mc") ?: ""
                UpiPaymentInfo(
                    payeeAddress = pa.trim(),
                    payeeName = pn,
                    amount = am,
                    currency = cu,
                    note = tn,
                    txnRef = tr,
                    merchantCode = mc,
                    rawUri = "upi://pay?pa=${pa.trim()}&pn=${Uri.encode(pn)}${if (am.isNotBlank()) "&am=$am" else ""}&cu=$cu"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses raw QR content supporting:
     * 1. BharatQR (EMVCo standard)
     * 2. Standard upi://pay?... URLs
     * 3. Web-wrapped payment URLs (https://...)
     * 4. Embedded UPI links inside text
     * 5. Bare VPA
     */
    fun parseQrPayload(
        raw: String
    ): UpiPaymentInfo? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        // 1. BharatQR (EMVCo format starting with 000201)
        val bharatQrResult = parseBharatQr(trimmed)
        if (bharatQrResult != null) return bharatQrResult

        // 2. Standard UPI URI (upi://pay?...)
        val upiResult = parseUpiUrl(trimmed)
        if (upiResult != null) return upiResult

        // 3. Web-wrapped UPI URL (https://...)
        val webResult = parseWebWrappedUpi(trimmed)
        if (webResult != null) return webResult

        // 4. Search for VPA inside plain text
        if (trimmed.contains("@")) {
            val tokens = trimmed.split(Regex("[\\s:?&=;,/|]"))
            val candidate = tokens.firstOrNull { isValidVpa(it.trim()) }
            if (candidate != null) {
                return UpiPaymentInfo(
                    payeeAddress = candidate.trim(),
                    payeeName = "UPI Merchant"
                )
            }
        }

        // 5. Bare VPA
        return if (isValidVpa(trimmed)) {
            UpiPaymentInfo(payeeAddress = trimmed)
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
    // INSTALLED STATUS HELPERS
    // ------------------------------------------------------------

    fun isPhonePeInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PHONEPE_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isPaytmInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PAYTM_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isBhimInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(BHIM_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------
    // UPI SCANNER INTENT LAUNCHER
    // ------------------------------------------------------------

    /**
     * Builds an Intent to launch a UPI app's scanner or payment interface.
     *
     * Supports:
     * - PhonePe: phonepe://scan deep link or launch intent
     * - Paytm: paytmmp://pay_flow?featuretype=scanner or launch intent
     * - Google Pay: upi://pay with GPay package or launch intent
     * - BHIM: upi://pay with BHIM package or launch intent
     * - Generic / Chooser: upi://pay chooser
     */
    fun buildUpiScannerIntent(
        context: Context,
        targetPackage: String? = null
    ): Intent {
        val pm = context.packageManager

        when (targetPackage) {
            PHONEPE_PACKAGE -> {
                // Try PhonePe scanner deep link
                try {
                    val scanUri = Uri.parse("phonepe://scan")
                    val scanIntent = Intent(Intent.ACTION_VIEW, scanUri).apply {
                        setPackage(PHONEPE_PACKAGE)
                    }
                    if (scanIntent.resolveActivity(pm) != null) {
                        return scanIntent
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                try {
                    val qrUri = Uri.parse("phonepe://qr")
                    val qrIntent = Intent(Intent.ACTION_VIEW, qrUri).apply {
                        setPackage(PHONEPE_PACKAGE)
                    }
                    if (qrIntent.resolveActivity(pm) != null) {
                        return qrIntent
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                pm.getLaunchIntentForPackage(PHONEPE_PACKAGE)?.let { return it }
            }
            PAYTM_PACKAGE -> {
                // Try Paytm scanner deep link
                try {
                    val paytmScanUri = Uri.parse("paytmmp://pay_flow?featuretype=scanner")
                    val paytmScanIntent = Intent(Intent.ACTION_VIEW, paytmScanUri).apply {
                        setPackage(PAYTM_PACKAGE)
                    }
                    if (paytmScanIntent.resolveActivity(pm) != null) {
                        return paytmScanIntent
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                try {
                    val paytmWalletScanUri = Uri.parse("paytmmp://cash_wallet?featuretype=scanner")
                    val paytmWalletScanIntent = Intent(Intent.ACTION_VIEW, paytmWalletScanUri).apply {
                        setPackage(PAYTM_PACKAGE)
                    }
                    if (paytmWalletScanIntent.resolveActivity(pm) != null) {
                        return paytmWalletScanIntent
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                pm.getLaunchIntentForPackage(PAYTM_PACKAGE)?.let { return it }
            }
            GOOGLE_PAY_PACKAGE -> {
                // Google Pay: try upi://pay with GPay package or launch intent
                try {
                    val gpayIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")).apply {
                        setPackage(GOOGLE_PAY_PACKAGE)
                    }
                    if (gpayIntent.resolveActivity(pm) != null) {
                        return gpayIntent
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                pm.getLaunchIntentForPackage(GOOGLE_PAY_PACKAGE)?.let { return it }
            }
            BHIM_PACKAGE -> {
                try {
                    val bhimIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")).apply {
                        setPackage(BHIM_PACKAGE)
                    }
                    if (bhimIntent.resolveActivity(pm) != null) {
                        return bhimIntent
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                pm.getLaunchIntentForPackage(BHIM_PACKAGE)?.let { return it }
            }
            else -> {
                if (!targetPackage.isNullOrBlank()) {
                    try {
                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")).apply {
                            setPackage(targetPackage)
                        }
                        if (appIntent.resolveActivity(pm) != null) {
                            return appIntent
                        }
                    } catch (e: Exception) {
                        // Fallback
                    }

                    pm.getLaunchIntentForPackage(targetPackage)?.let { return it }
                }
            }
        }

        // Generic fallback: upi://pay chooser
        val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
        return Intent.createChooser(genericIntent, "Open UPI App Scanner")
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