package com.example.data.upi

import com.example.data.models.FinanceScope

/**
 * Represents a payment transaction extracted automatically from
 * a UPI payment notification, UPI intent response, or payment screenshot.
 */
data class ExtractedUpiPayment(
    val amount: Double,
    val payeeName: String,
    val upiId: String? = null,
    val upiTransactionId: String? = null, // 12-digit UTR reference number
    val sourceApp: String, // e.g. "Google Pay", "PhonePe", "Paytm", "HDFC Bank"
    val timestamp: Long = System.currentTimeMillis(),
    val detectedCategory: String = "Other",
    val detectedScope: FinanceScope = FinanceScope.PERSONAL,
    val rawText: String = "",
    val isDebit: Boolean = true
) {
    /**
     * Unique fingerprint for deduplicating identical notifications
     * received simultaneously from both the UPI app and the bank.
     */
    val deduplicationKey: String
        get() = when {
            !upiTransactionId.isNullOrBlank() -> "utr_$upiTransactionId"
            else -> "tx_${amount}_${payeeName.lowercase().trim()}_${timestamp / 180000}" // 3-minute window
        }
}
