package com.only4.cap4k.ddd.core.domain.event

/** Synchronously dispatches one concrete cap4k event payload to its local method handlers. */
fun interface EventHandlerDispatcher {
    fun dispatch(eventPayload: Any)
}
