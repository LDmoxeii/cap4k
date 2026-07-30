package com.only4.cap4k.ddd.core.application.capability

/**
 * Category-specific interception for one concrete [CapabilityCall] type.
 */
interface CapabilityInterceptor<CALL : CapabilityCall<RESULT>, RESULT : Any> {
    fun beforeCall(request: CALL) = Unit

    fun afterCall(request: CALL, result: RESULT) = Unit
}
