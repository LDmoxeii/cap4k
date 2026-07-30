package com.only4.cap4k.ddd.core.application.capability

/**
 * Adapter-owned implementation of one concrete [CapabilityCall].
 */
fun interface CapabilityHandler<CALL : CapabilityCall<RESULT>, RESULT : Any> {
    fun call(request: CALL): RESULT
}
