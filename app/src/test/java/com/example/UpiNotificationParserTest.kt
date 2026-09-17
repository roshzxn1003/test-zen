package com.example

import com.example.data.upi.ExtractedUpiPayment
import com.example.data.upi.UpiNotificationParser
import org.junit.Assert.*
import org.junit.Test

class UpiNotificationParserTest {

    @Test
    fun testGooglePayPaymentNotification() {
        val parsed = UpiNotificationParser.parse(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Paid ₹350.00",
            text = "Paid ₹350.00 to Swiggy. UPI Ref: 424598123456"
        )
        assertNotNull(parsed)
        assertEquals(350.0, parsed!!.amount, 0.01)
        assertEquals("Swiggy", parsed.payeeName)
        assertEquals("424598123456", parsed.upiTransactionId)
        assertEquals("Google Pay", parsed.sourceApp)
        assertEquals("Food & Dining", parsed.detectedCategory)
        assertTrue(parsed.isDebit)
    }

    @Test
    fun testPhonePePaymentNotification() {
        val parsed = UpiNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "Payment Successful",
            text = "Payment of ₹500 to Indian Oil successful. Debited from State Bank of India - 1234."
        )
        assertNotNull(parsed)
        assertEquals(500.0, parsed!!.amount, 0.01)
        assertEquals("Indian Oil", parsed.payeeName)
        assertEquals("PhonePe", parsed.sourceApp)
        assertEquals("Transportation", parsed.detectedCategory)
    }

    @Test
    fun testPaytmPaymentNotification() {
        val parsed = UpiNotificationParser.parse(
            packageName = "net.one97.paytm",
            title = "Money Sent",
            text = "Money sent: ₹1,250 sent to BigBasket. UPI Ref: 987654321012"
        )
        assertNotNull(parsed)
        assertEquals(1250.0, parsed!!.amount, 0.01)
        assertEquals("BigBasket", parsed.payeeName)
        assertEquals("987654321012", parsed.upiTransactionId)
        assertEquals("Paytm", parsed.sourceApp)
        assertEquals("Shopping", parsed.detectedCategory)
    }

    @Test
    fun testCredPaymentNotification() {
        val parsed = UpiNotificationParser.parse(
            packageName = "com.dreamplug.androidapp",
            title = "Payment Successful",
            text = "₹350 paid to Starbucks via CRED UPI. UPI Ref: 112233445566"
        )
        assertNotNull(parsed)
        assertEquals(350.0, parsed!!.amount, 0.01)
        assertEquals("Starbucks", parsed.payeeName)
        assertEquals("112233445566", parsed.upiTransactionId)
        assertEquals("CRED", parsed.sourceApp)
        assertEquals("Food & Dining", parsed.detectedCategory)
    }

    @Test
    fun testHdfcBankNotification() {
        val parsed = UpiNotificationParser.parse(
            packageName = "com.snapwork.hdfc",
            title = "HDFC Bank Alert",
            text = "Alert: Rs 450.00 debited from a/c **1234 on 06-09-26 via UPI to Zomato (UPI Ref: 424598123456)"
        )
        assertNotNull(parsed)
        assertEquals(450.0, parsed!!.amount, 0.01)
        assertEquals("Zomato", parsed.payeeName)
        assertEquals("424598123456", parsed.upiTransactionId)
        assertEquals("HDFC Bank", parsed.sourceApp)
        assertEquals("Food & Dining", parsed.detectedCategory)
    }

    @Test
    fun testSbiNotification() {
        val parsed = UpiNotificationParser.parse(
            packageName = "com.sbi.lotusintouch",
            title = "SBI Alert",
            text = "Dear SBI User, A/C 1234 debited by 250.0 on 06Sep26 by UPI/424598123456/Uber."
        )
        assertNotNull(parsed)
        assertEquals(250.0, parsed!!.amount, 0.01)
        assertEquals("424598123456", parsed.upiTransactionId)
        assertEquals("SBI YONO", parsed.sourceApp)
    }

    @Test
    fun testNonTransactionNotificationsAreIgnored() {
        val otpNotif = UpiNotificationParser.parse(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Google Pay",
            text = "Your OTP for Google Pay verification is 849201. Do not share this with anyone."
        )
        assertNull(otpNotif)

        val offerNotif = UpiNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "Special Offer",
            text = "Get 10% cashback on your next electricity bill payment."
        )
        assertNull(offerNotif)

        val requestNotif = UpiNotificationParser.parse(
            packageName = "net.one97.paytm",
            title = "Payment Request",
            text = "Ramesh has requested ₹500 from you on Paytm."
        )
        assertNull(requestNotif)
    }

    @Test
    fun testDeduplicationKey() {
        val payment1 = ExtractedUpiPayment(
            amount = 350.0,
            payeeName = "Swiggy",
            upiTransactionId = "424598123456",
            sourceApp = "Google Pay"
        )

        val payment2 = ExtractedUpiPayment(
            amount = 350.0,
            payeeName = "SWIGGY",
            upiTransactionId = "424598123456",
            sourceApp = "HDFC Bank"
        )

        assertEquals(payment1.deduplicationKey, payment2.deduplicationKey)
        assertEquals("utr_424598123456", payment1.deduplicationKey)
    }
}
