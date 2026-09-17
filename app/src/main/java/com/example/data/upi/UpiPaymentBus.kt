package com.example.data.upi

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus that broadcasts automatically detected UPI payments to
 * CashFlowViewModel, UI dialogs, and the sync engine.
 */
object UpiPaymentBus {
    private val _detectedPayments = MutableSharedFlow<ExtractedUpiPayment>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val detectedPayments: SharedFlow<ExtractedUpiPayment> = _detectedPayments.asSharedFlow()

    fun post(payment: ExtractedUpiPayment) {
        _detectedPayments.tryEmit(payment)
    }
}
