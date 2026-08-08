package com.only4.cap4k.ddd.core.domain.event

/** Executes one already-claimed reliable Event attempt. */
interface EventPublisher {
    fun publish(event: EventRecord, completion: Completion)

    interface Completion {
        fun onSuccess(event: EventRecord)
        fun onFailure(event: EventRecord, throwable: Throwable)
    }
}
