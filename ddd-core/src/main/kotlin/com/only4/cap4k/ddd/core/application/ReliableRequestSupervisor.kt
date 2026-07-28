package com.only4.cap4k.ddd.core.application

import java.time.LocalDateTime

/**
 * Optional provider for persisted, delayed and result-addressable requests.
 */
interface ReliableRequestSupervisor {
    fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> schedule(
        request: REQUEST,
        schedule: LocalDateTime,
    ): String

    fun <R : Any> result(requestId: String): R?

    companion object {
        @JvmStatic
        val instance: ReliableRequestSupervisor
            get() = RequestSupervisorSupport.reliable
    }
}
