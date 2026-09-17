package com.example.data.upi

import java.util.regex.Pattern

/**
 * Robust, deterministic pattern matcher for extracting UPI transaction details
 * from Android notification titles and text emitted by UPI applications and Indian banks.
 */
object UpiNotificationParser {

    val MONITORED_PACKAGES = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "com.dreamplug.androidapp" to "CRED",
        "in.org.npci.upiapp" to "BHIM",
        "com.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.whatsapp" to "WhatsApp Pay",
        "com.naviapp" to "Navi UPI",
        // Indian Banking Apps
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.sbi.lotusintouch" to "SBI YONO",
        "com.csam.icici.bank.imobile" to "iMobile Pay",
        "com.axis.mobile" to "Axis Mobile",
        "com.msf.kbank.mobile" to "Kotak Bank"
    )

    // Regex for matching currency amounts: ₹350, Rs. 350.00, Rs 500, INR 1,200.50, debited by 250
    private val AMOUNT_REGEX = Pattern.compile(
        """(?:(?:₹|Rs\.?|INR)\s*|(?:(?:debited|credited)\s+(?:by|for|with)\s*(?:₹|Rs\.?|INR)?\s*)|(?:(?:paid|sent)\s+(?:₹|Rs\.?|INR)?\s*))([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for matching 12-digit UPI reference numbers / UTR
    private val UTR_REGEX = Pattern.compile(
        """\b(?:UPI\s*Ref(?:\s*No|\s*ID)?|UTR|Ref\s*No|Ref|rrn|UPI)[\s:/]*([0-9]{12})\b""",
        Pattern.CASE_INSENSITIVE
    )

    // Standalone 12-digit sequence when preceded by slash or hyphen in bank SMS
    private val STANDALONE_UTR_REGEX = Pattern.compile(
        """[/:\-]([0-9]{12})[/:\-\s]"""
    )

    // Regex for matching UPI VPAs: example@okhdfcbank
    private val VPA_REGEX = Pattern.compile(
        """\b([a-zA-Z0-9][a-zA-Z0-9.\-_+]{1,255}@[a-zA-Z0-9.\-_]{2,64})\b"""
    )

    /**
     * Checks whether a package is a supported UPI or banking app.
     */
    fun isSupportedPackage(packageName: String): Boolean {
        return MONITORED_PACKAGES.containsKey(packageName)
    }

    /**
     * Parses notification title and text, returning an ExtractedUpiPayment if
     * a valid debit/credit transaction is detected.
     */
    fun parse(packageName: String, title: String?, text: String?): ExtractedUpiPayment? {
        val appName = MONITORED_PACKAGES[packageName] ?: "UPI"
        val combined = "${title.orEmpty()} ${text.orEmpty()}".trim()
        if (combined.isBlank()) return null

        // Ignore OTP, login notifications, requests to pay, promotional banners
        if (isNonTransactionNotification(combined)) return null

        // Detect if transaction is debit or credit
        val isDebit = isDebitTransaction(combined)

        // 1. Extract Amount
        val amount = extractAmount(combined) ?: return null
        if (amount <= 0.0) return null

        // 2. Extract UTR / Reference Number
        val utr = extractUtr(combined)

        // 3. Extract VPA (if present)
        val vpa = extractVpa(combined)

        // 4. Extract Payee / Merchant Name
        val payee = extractPayee(combined, title, text, vpa)

        // 5. Smart Category Prediction
        val category = predictCategory(payee, combined)

        return ExtractedUpiPayment(
            amount = amount,
            payeeName = payee,
            upiId = vpa,
            upiTransactionId = utr,
            sourceApp = appName,
            timestamp = System.currentTimeMillis(),
            detectedCategory = category,
            rawText = combined,
            isDebit = isDebit
        )
    }

    private fun isNonTransactionNotification(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("otp") ||
                lower.contains("verification code") ||
                lower.contains("login alert") ||
                lower.contains("requested money") ||
                lower.contains("request from") ||
                lower.contains("payment request") ||
                lower.contains("offer") ||
                lower.contains("cashback available") ||
                lower.contains("discount on") ||
                lower.contains("reminder")
    }

    private fun isDebitTransaction(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.contains("received") || lower.contains("credited") || lower.contains("refund")) {
            return false
        }
        return true
    }

    fun extractAmount(text: String): Double? {
        val matcher = AMOUNT_REGEX.matcher(text)
        if (matcher.find()) {
            val rawAmount = matcher.group(1)?.replace(",", "") ?: return null
            return rawAmount.toDoubleOrNull()
        }
        return null
    }

    fun extractUtr(text: String): String? {
        val matcher = UTR_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val standalone = STANDALONE_UTR_REGEX.matcher(text)
        if (standalone.find()) {
            return standalone.group(1)
        }
        return null
    }

    fun extractVpa(text: String): String? {
        val matcher = VPA_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun extractPayee(combined: String, title: String?, text: String?, vpa: String?): String {
        val patterns = listOf(
            // "UPI/424598123456/Uber"
            Pattern.compile("""(?:UPI|Ref|rrn)[/:][0-9]+[/:\s]+([^/.,\n]+)""", Pattern.CASE_INSENSITIVE),
            // "Paid to XYZ" / "Paid ₹350 to XYZ"
            Pattern.compile("""(?:paid|payment of\s+[₹Rs\.]*[0-9,.]*|sent\s+[₹Rs\.]*[0-9,.]*)\s+to\s+([^.,\n]+)""", Pattern.CASE_INSENSITIVE),
            // "Money sent to XYZ"
            Pattern.compile("""money sent to\s+([^.,\n]+)""", Pattern.CASE_INSENSITIVE),
            // "debited ... via UPI to XYZ" / "UPI/UTR/.../XYZ"
            Pattern.compile("""(?:via upi to|to VPA|to)\s+([^.,(\n]+)""", Pattern.CASE_INSENSITIVE),
            // "Payment at XYZ"
            Pattern.compile("""payment at\s+([^.,\n]+)""", Pattern.CASE_INSENSITIVE),
            // "Transfer to XYZ"
            Pattern.compile("""transfer to\s+([^.,\n]+)""", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(combined)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: ""
                val cleaned = cleanPayeeName(candidate)
                if (cleaned.isNotBlank()) return cleaned
            }
        }

        // Fallback to VPA prefix
        if (!vpa.isNullOrBlank()) {
            val prefix = vpa.substringBefore("@").replace(".", " ")
            return prefix.replaceFirstChar { it.uppercase() }
        }

        // Fallback to title if title has a name
        if (!title.isNullOrBlank() && !title.contains("₹") && !title.contains("Paid", ignoreCase = true)) {
            return title.trim()
        }

        return "Merchant"
    }

    private fun cleanPayeeName(raw: String): String {
        return raw.replace(Regex("""\s+(?:successfully|successful|via|from|upi|ref|utr|for|debited).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[0-9]{10,}"""), "") // Remove phone numbers or account numbers
            .replace(Regex("""[^\w\s&'-]"""), "") // Remove special symbols
            .trim()
            .takeIf { it.length in 2..50 } ?: ""
    }

    fun predictCategory(payee: String, fullText: String): String {
        val text = "$payee $fullText".lowercase()

        return when {
            // Food & Dining
            containsAny(text, "swiggy", "zomato", "starbucks", "mcdonald", "kfc", "domino", "pizza", "burger", "chai", "tea", "cafe", "restaurant", "food", "bakery", "bakes", "hotel", "biryani", "dhaba", "sweets", "kitchen", "canteen") -> "Food & Dining"

            // Transportation
            containsAny(text, "uber", "ola", "rapido", "metro", "petrol", "fuel", "diesel", "indian oil", "bharat petroleum", "shell", "hpcl", "iocl", "irctc", "rail", "flight", "indigo", "air india", "toll", "fastag", "parking", "auto") -> "Transportation"

            // Shopping & Groceries
            containsAny(text, "amazon", "flipkart", "myntra", "meesho", "zara", "h&m", "ajio", "nykaa", "bigbasket", "blinkit", "zepto", "instamart", "freshmart", "supermarket", "grocery", "provision", "mart", "store", "mall", "fashion", "retail") -> "Shopping"

            // Bills & Utilities
            containsAny(text, "bescom", "tneb", "electricity", "water", "gas", "cylinder", "airtel", "jio", "vi", "vodafone", "bsnl", "broadband", "wi-fi", "wifi", "fiber", "recharge", "dth", "tata sky", "sun direct", "dish tv", "postpaid", "bill") -> "Bills & Utilities"

            // Entertainment
            containsAny(text, "bookmyshow", "pvr", "inox", "cinema", "movies", "theatre", "netflix", "prime video", "spotify", "hotstar", "youtube", "gaming", "steam", "playstation") -> "Entertainment"

            // Healthcare & Medicine
            containsAny(text, "apollo", "medplus", "pharmacy", "chemist", "hospital", "clinic", "doctor", "dental", "diagnostics", "lab", "1mg", "pharmeasy", "netmeds", "health") -> "Healthcare"

            // Investments & Transfers
            containsAny(text, "zerodha", "groww", "angelone", "upstox", "mutual fund", "sip", "deposit", "investment", "gold", "share") -> "Investments"

            else -> "Other"
        }
    }

    private fun containsAny(source: String, vararg keywords: String): Boolean {
        return keywords.any { source.contains(it) }
    }
}
