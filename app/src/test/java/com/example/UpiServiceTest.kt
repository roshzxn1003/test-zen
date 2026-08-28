package com.example

import android.content.Intent
import com.example.data.upi.UpiPaymentInfo
import com.example.data.upi.UpiPaymentStatus
import com.example.data.upi.UpiService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpiServiceTest {

    // ------------------------------------------------------------
    // VPA VALIDATION
    // ------------------------------------------------------------

    @Test
    fun testValidVpaFormats() {

        assertTrue(
            UpiService.isValidVpa(
                "someone@okhdfcbank"
            )
        )

        assertTrue(
            UpiService.isValidVpa(
                "9876543210@paytm"
            )
        )

        assertTrue(
            UpiService.isValidVpa(
                "user.name@okaxis"
            )
        )

        assertTrue(
            UpiService.isValidVpa(
                "store_name-1@icici"
            )
        )

        assertTrue(
            UpiService.isValidVpa(
                "merchant@apl"
            )
        )

        assertTrue(
            UpiService.isValidVpa(
                "john+zenith@upi"
            )
        )

        // Invalid VPAs
        assertFalse(
            UpiService.isValidVpa("")
        )

        assertFalse(
            UpiService.isValidVpa(
                "invalid-vpa-without-at"
            )
        )

        assertFalse(
            UpiService.isValidVpa(
                "@bank"
            )
        )

        assertFalse(
            UpiService.isValidVpa(
                "user@"
            )
        )
    }

    // ------------------------------------------------------------
    // STANDARD UPI QR PARSING
    // ------------------------------------------------------------

    @Test
    fun testStandardUpiQrParsing() {

        val payload =
            "upi://pay?" +
            "pa=merchant@okhdfcbank" +
            "&pn=SuperMarket" +
            "&am=250.50" +
            "&cu=INR" +
            "&tn=Groceries" +
            "&tr=TXN12345"

        val parsed =
            UpiService.parseQrPayload(payload)

        assertNotNull(parsed)

        assertEquals(
            "merchant@okhdfcbank",
            parsed?.payeeAddress
        )

        assertEquals(
            "SuperMarket",
            parsed?.payeeName
        )

        assertEquals(
            "250.50",
            parsed?.amount
        )

        assertEquals(
            "INR",
            parsed?.currency
        )

        assertEquals(
            "Groceries",
            parsed?.note
        )

        assertEquals(
            "TXN12345",
            parsed?.txnRef
        )
    }

    // ------------------------------------------------------------
    // UPPERCASE / MIXED CASE QR
    // ------------------------------------------------------------

    @Test
    fun testUppercaseAndMixedCaseUpiQrParsing() {

        val payload =
            "UPI://PAY?" +
            "PA=store@paytm" +
            "&PN=Retail+Store" +
            "&AM=100.00" +
            "&CU=INR" +
            "&TN=Payment"

        val parsed =
            UpiService.parseQrPayload(payload)

        assertNotNull(parsed)

        assertEquals(
            "store@paytm",
            parsed?.payeeAddress
        )

        assertEquals(
            "Retail Store",
            parsed?.payeeName
        )

        assertEquals(
            "100.00",
            parsed?.amount
        )

        assertEquals(
            "INR",
            parsed?.currency
        )
    }

    // ------------------------------------------------------------
    // BARE VPA
    // ------------------------------------------------------------

    @Test
    fun testBareVpaParsing() {

        val parsed =
            UpiService.parseQrPayload(
                "grocery.store@okaxis"
            )

        assertNotNull(parsed)

        assertEquals(
            "grocery.store@okaxis",
            parsed?.payeeAddress
        )

        assertEquals(
            "",
            parsed?.amount
        )
    }

    // ------------------------------------------------------------
    // EMBEDDED UPI URL
    // ------------------------------------------------------------

    @Test
    fun testEmbeddedUpiUrlInText() {

        val payload =
            "Please pay using this link: " +
            "upi://pay?" +
            "pa=canteen@icici" +
            "&am=40.00" +
            "&pn=Campus+Canteen"

        val parsed =
            UpiService.parseQrPayload(payload)

        assertNotNull(parsed)

        assertEquals(
            "canteen@icici",
            parsed?.payeeAddress
        )

        assertEquals(
            "40.00",
            parsed?.amount
        )

        assertEquals(
            "Campus Canteen",
            parsed?.payeeName
        )
    }

    // ------------------------------------------------------------
    // INVALID PAYLOAD
    // ------------------------------------------------------------

    @Test
    fun testInvalidPayloadReturnsNull() {

        assertNull(
            UpiService.parseQrPayload("")
        )

        assertNull(
            UpiService.parseQrPayload("   ")
        )

        assertNull(
            UpiService.parseQrPayload(
                "https://google.com"
            )
        )

        assertNull(
            UpiService.parseQrPayload(
                "Hello World 123"
            )
        )
    }

    // ------------------------------------------------------------
    // BUILD PAYMENT URI
    // ------------------------------------------------------------

    @Test
    fun testBuildPaymentUri() {

        val info =
            UpiPaymentInfo(
                payeeAddress =
                    "friend@okhdfcbank",

                payeeName =
                    "Friend",

                amount =
                    "500",

                note =
                    "Dinner split"
            )

        val uri =
            UpiService.buildPaymentUri(info)

        // Basic URI checks
        assertEquals(
            "upi",
            uri.scheme
        )

        assertEquals(
            "pay",
            uri.authority
        )

        // Payee
        assertEquals(
            "friend@okhdfcbank",
            uri.getQueryParameter("pa")
        )

        // Name
        assertEquals(
            "Friend",
            uri.getQueryParameter("pn")
        )

        // IMPORTANT:
        // 500 is normalized to 500.00
        assertEquals(
            "500.00",
            uri.getQueryParameter("am")
        )

        // Currency
        assertEquals(
            "INR",
            uri.getQueryParameter("cu")
        )

        // Note
        assertEquals(
            "Dinner split",
            uri.getQueryParameter("tn")
        )
    }

    // ------------------------------------------------------------
    // AMOUNT NORMALIZATION
    // ------------------------------------------------------------

    @Test
    fun testAmountNormalization() {

        // Integer amount
        val info1 =
            UpiPaymentInfo(
                payeeAddress =
                    "test@okaxis",
                amount =
                    "2"
            )

        val uri1 =
            UpiService.buildPaymentUri(info1)

        assertEquals(
            "2.00",
            uri1.getQueryParameter("am")
        )

        // One decimal
        val info2 =
            UpiPaymentInfo(
                payeeAddress =
                    "test@okaxis",
                amount =
                    "2.5"
            )

        val uri2 =
            UpiService.buildPaymentUri(info2)

        assertEquals(
            "2.50",
            uri2.getQueryParameter("am")
        )

        // Already two decimals
        val info3 =
            UpiPaymentInfo(
                payeeAddress =
                    "test@okaxis",
                amount =
                    "25.75"
            )

        val uri3 =
            UpiService.buildPaymentUri(info3)

        assertEquals(
            "25.75",
            uri3.getQueryParameter("am")
        )

        // Large amount
        val info4 =
            UpiPaymentInfo(
                payeeAddress =
                    "test@okaxis",
                amount =
                    "1000"
            )

        val uri4 =
            UpiService.buildPaymentUri(info4)

        assertEquals(
            "1000.00",
            uri4.getQueryParameter("am")
        )
    }

    // ------------------------------------------------------------
    // ZERO / NEGATIVE AMOUNT
    // ------------------------------------------------------------

    @Test
    fun testInvalidAmountIsNotAdded() {

        val zeroInfo =
            UpiPaymentInfo(
                payeeAddress =
                    "test@okaxis",
                amount =
                    "0"
            )

        val zeroUri =
            UpiService.buildPaymentUri(zeroInfo)

        assertNull(
            zeroUri.getQueryParameter("am")
        )

        val negativeInfo =
            UpiPaymentInfo(
                payeeAddress =
                    "test@okaxis",
                amount =
                    "-10"
            )

        val negativeUri =
            UpiService.buildPaymentUri(
                negativeInfo
            )

        assertNull(
            negativeUri.getQueryParameter("am")
        )
    }

    // ------------------------------------------------------------
    // EMPTY AMOUNT
    // ------------------------------------------------------------

    @Test
    fun testEmptyAmountIsAllowed() {

        val info =
            UpiPaymentInfo(
                payeeAddress =
                    "test@okaxis",
                amount =
                    ""
            )

        val uri =
            UpiService.buildPaymentUri(info)

        assertNull(
            uri.getQueryParameter("am")
        )

        assertEquals(
            "test@okaxis",
            uri.getQueryParameter("pa")
        )

        assertEquals(
            "INR",
            uri.getQueryParameter("cu")
        )
    }

    // ------------------------------------------------------------
    // PAYMENT URI WITH ALL PARAMETERS
    // ------------------------------------------------------------

    @Test
    fun testBuildPaymentUriWithAllParameters() {

        val info =
            UpiPaymentInfo(
                payeeAddress =
                    "merchant@okaxis",

                payeeName =
                    "Test Merchant",

                amount =
                    "99.5",

                currency =
                    "INR",

                note =
                    "Test payment",

                txnRef =
                    "REF123",

                merchantCode =
                    "5411"
            )

        val uri =
            UpiService.buildPaymentUri(info)

        assertEquals(
            "merchant@okaxis",
            uri.getQueryParameter("pa")
        )

        assertEquals(
            "Test Merchant",
            uri.getQueryParameter("pn")
        )

        assertEquals(
            "99.50",
            uri.getQueryParameter("am")
        )

        assertEquals(
            "INR",
            uri.getQueryParameter("cu")
        )

        assertEquals(
            "Test payment",
            uri.getQueryParameter("tn")
        )

        assertEquals(
            "REF123",
            uri.getQueryParameter("tr")
        )

        assertEquals(
            "5411",
            uri.getQueryParameter("mc")
        )
    }

    // ------------------------------------------------------------
    // MAP RESULT - NULL DATA
    // ------------------------------------------------------------

    @Test
    fun testMapResultDoesNotLockoutUser() {

        val result =
            UpiService.mapResult(
                0,
                null
            )

        assertTrue(
            result.launched
        )

        assertFalse(
            result.cancelled
        )

        assertEquals(
            UpiPaymentStatus.INITIATED,
            result.status
        )

        assertNull(
            result.returnedTxnRef
        )
    }

    // ------------------------------------------------------------
    // MAP RESULT - SUCCESS
    // ------------------------------------------------------------

    @Test
    fun testMapResultSuccess() {

        val successIntent =
            Intent().apply {

                putExtra(
                    "response",
                    "txnId=123" +
                    "&responseCode=00" +
                    "&Status=SUCCESS" +
                    "&txnRef=REF999"
                )
            }

        val result =
            UpiService.mapResult(
                -1,
                successIntent
            )

        assertTrue(
            result.launched
        )

        assertFalse(
            result.cancelled
        )

        assertEquals(
            UpiPaymentStatus.SUCCESSFUL,
            result.status
        )

        assertEquals(
            "REF999",
            result.returnedTxnRef
        )
    }

    // ------------------------------------------------------------
    // MAP RESULT - PENDING
    // ------------------------------------------------------------

    @Test
    fun testMapResultPending() {

        val pendingIntent =
            Intent().apply {

                putExtra(
                    "response",
                    "txnId=123" +
                    "&Status=SUBMITTED" +
                    "&txnRef=PENDING123"
                )
            }

        val result =
            UpiService.mapResult(
                -1,
                pendingIntent
            )

        assertTrue(
            result.launched
        )

        assertEquals(
            UpiPaymentStatus.PENDING,
            result.status
        )

        assertEquals(
            "PENDING123",
            result.returnedTxnRef
        )
    }

    // ------------------------------------------------------------
    // MAP RESULT - FAILED
    // ------------------------------------------------------------

    @Test
    fun testMapResultFailed() {

        val failedIntent =
            Intent().apply {

                putExtra(
                    "response",
                    "txnId=123" +
                    "&Status=FAILED" +
                    "&txnRef=FAIL123"
                )
            }

        val result =
            UpiService.mapResult(
                -1,
                failedIntent
            )

        assertTrue(
            result.launched
        )

        assertEquals(
            UpiPaymentStatus.FAILED,
            result.status
        )

        assertEquals(
            "FAIL123",
            result.returnedTxnRef
        )
    }

    // ------------------------------------------------------------
    // MAP RESULT - CANCELLED
    // ------------------------------------------------------------

    @Test
    fun testMapResultCancelled() {

        val cancelledIntent =
            Intent()

        val result =
            UpiService.mapResult(
                android.app.Activity.RESULT_CANCELED,
                cancelledIntent
            )

        assertTrue(
            result.launched
        )

        assertTrue(
            result.cancelled
        )

        assertEquals(
            UpiPaymentStatus.CANCELLED,
            result.status
        )
    }

    // ------------------------------------------------------------
    // GOOGLE PAY PACKAGE
    // ------------------------------------------------------------

    @Test
    fun testGooglePayPackageName() {

        assertEquals(
            "com.google.android.apps.nbu.paisa.user",
            UpiService.GOOGLE_PAY_PACKAGE
        )
    }

    // ------------------------------------------------------------
    // PHONEPE PACKAGE
    // ------------------------------------------------------------

    @Test
    fun testPhonePePackageName() {

        assertEquals(
            "com.phonepe.app",
            UpiService.PHONEPE_PACKAGE
        )
    }

    // ------------------------------------------------------------
    // PAYTM PACKAGE
    // ------------------------------------------------------------

    @Test
    fun testPaytmPackageName() {

        assertEquals(
            "net.one97.paytm",
            UpiService.PAYTM_PACKAGE
        )
    }

    // ------------------------------------------------------------
    // BHIM PACKAGE
    // ------------------------------------------------------------

    @Test
    fun testBhimPackageName() {

        assertEquals(
            "in.org.npci.upiapp",
            UpiService.BHIM_PACKAGE
        )
    }

    // ------------------------------------------------------------
    // DEBUG PAYMENT URI
    // ------------------------------------------------------------

    @Test
    fun testDebugPaymentUri() {

        val info =
            UpiPaymentInfo(
                payeeAddress =
                    "9159222103-2@ybl",

                payeeName =
                    "ARUN ROSHAN GJ",

                amount =
                    "2"
            )

        val debugUri =
            UpiService.getDebugPaymentUri(
                info
            )

        assertTrue(
            debugUri.startsWith(
                "upi://pay"
            )
        )

        assertTrue(
            debugUri.contains(
                "am=2.00"
            )
        )

        assertTrue(
            debugUri.contains(
                "cu=INR"
            )
        )
    }
}