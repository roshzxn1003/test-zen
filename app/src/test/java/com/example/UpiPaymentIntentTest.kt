package com.example

import com.example.data.upi.UpiPaymentInfo
import com.example.data.upi.UpiService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpiPaymentIntentTest {

    @Test
    fun testP2pPaymentUriDoesNotIncludeSyntheticTr() {
        val info = UpiPaymentInfo(
            payeeAddress = "user@oksbi",
            payeeName = "Arun Kumar",
            amount = "250.00",
            currency = "INR",
            note = "Split bill"
        )
        val uri = UpiService.buildPaymentUri(info)

        assertEquals("upi", uri.scheme)
        assertEquals("pay", uri.authority)
        assertEquals("user@oksbi", uri.getQueryParameter("pa"))
        assertEquals("Arun Kumar", uri.getQueryParameter("pn"))
        assertEquals("250.00", uri.getQueryParameter("am"))
        assertEquals("INR", uri.getQueryParameter("cu"))
        assertEquals("Split bill", uri.getQueryParameter("tn"))
        // tr MUST be absent for P2P transactions to avoid NPCI rejection
        assertNull(uri.getQueryParameter("tr"))
        assertNull(uri.getQueryParameter("mc"))
    }

    @Test
    fun testPayeeNameAndNoteSanitization() {
        val info = UpiPaymentInfo(
            payeeAddress = "store@paytm",
            payeeName = "Dinner & Drinks / Food #1!",
            amount = "120.50",
            note = "Party @ John's House (100% fun)"
        )
        val uri = UpiService.buildPaymentUri(info)

        val pn = uri.getQueryParameter("pn")
        assertNotNull(pn)
        // Punctuation like &, /, #, ! should be stripped to avoid PSP risk blocks
        assertFalse(pn!!.contains("&"))
        assertFalse(pn.contains("/"))
        assertFalse(pn.contains("#"))

        val tn = uri.getQueryParameter("tn")
        assertNotNull(tn)
        assertFalse(tn!!.contains("@"))
        assertFalse(tn.contains("%"))
    }

    @Test
    fun testPreservesMerchantQrCryptographicSignatureAndMetadata() {
        val storeQr = "upi://pay?pa=supermarket@hdfcbank&pn=FreshMart&mc=5411&tr=ORD9988776655&mode=02&orgid=180001&sign=MEYCIQC...=="

        val parsed = UpiService.parseQrPayload(storeQr)
        assertNotNull(parsed)
        assertEquals("FreshMart", parsed?.payeeName)
        assertEquals("supermarket@hdfcbank", parsed?.payeeAddress)
        assertEquals(storeQr, parsed?.rawUri)

        // Now build the payment URI with a user-entered amount of 450.00
        val paymentUri = UpiService.buildQrPaymentUri(parsed!!.rawUri!!, 450.00)

        assertEquals("supermarket@hdfcbank", paymentUri.getQueryParameter("pa"))
        assertEquals("FreshMart", paymentUri.getQueryParameter("pn"))
        assertEquals("5411", paymentUri.getQueryParameter("mc"))
        assertEquals("ORD9988776655", paymentUri.getQueryParameter("tr"))
        assertEquals("02", paymentUri.getQueryParameter("mode"))
        assertEquals("180001", paymentUri.getQueryParameter("orgid"))
        assertEquals("MEYCIQC...==", paymentUri.getQueryParameter("sign"))
        assertEquals("450.00", paymentUri.getQueryParameter("am"))
    }

    @Test
    fun testBuildUpiScannerIntentGenericChooser() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val intent = UpiService.buildUpiScannerIntent(context, null)
        assertNotNull(intent)
        assertEquals(android.content.Intent.ACTION_CHOOSER, intent.action)
        val targetIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(android.content.Intent.EXTRA_INTENT)
        }
        assertNotNull(targetIntent)
        assertEquals(android.content.Intent.ACTION_VIEW, targetIntent?.action)
        assertEquals("upi://pay", targetIntent?.dataString)
    }

    @Test
    fun testBuildUpiScannerIntentForGooglePay() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val intent = UpiService.buildUpiScannerIntent(context, UpiService.GOOGLE_PAY_PACKAGE)
        assertNotNull(intent)
        // Without package installed in robolectric, falls back to chooser or package intent
        assertNotNull(intent.action)
    }

    @Test
    fun testAppInstallationHelpersDoNotCrash() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertFalse(UpiService.isGooglePayInstalled(context))
        assertFalse(UpiService.isPhonePeInstalled(context))
        assertFalse(UpiService.isPaytmInstalled(context))
        assertFalse(UpiService.isBhimInstalled(context))
    }
}
