package com.only4.cap4k.ddd.core.domain.event

/**
 * Low-latency wake-up boundary for durable Event delivery.
 *
 * Calling [wake] never grants delivery ownership. The implementation must still
 * claim a due record through the durable ownership substrate before dispatch.
 */
fun interface ReliableEventCoordinator {
    fun wake()
}
