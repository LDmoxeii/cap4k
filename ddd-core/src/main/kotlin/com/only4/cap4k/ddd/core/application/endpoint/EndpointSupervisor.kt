package com.only4.cap4k.ddd.core.application.endpoint

import com.only4.cap4k.contract.EndpointRequest
import jakarta.validation.ConstraintViolationException
import java.util.concurrent.CompletionStage

interface EndpointSupervisor {
    /** @throws ConstraintViolationException when endpoint request validation fails. */
    fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE

    /** Schedules the blocking endpoint handler and reports every failure through the returned stage. */
    fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> sendAsync(request: REQUEST): CompletionStage<RESPONSE>

    companion object {
        @JvmStatic
        val instance: EndpointSupervisor
            get() = EndpointSupervisorSupport.instance
    }
}
