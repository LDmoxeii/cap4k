package com.only4.cap4k.ddd.core.application.capability

import jakarta.validation.ConstraintViolationException
import java.util.concurrent.CompletionStage

/**
 * Invokes adapter-owned external capabilities.
 */
interface CapabilitySupervisor {
    /**
     * @throws ConstraintViolationException when capability-call validation fails
     */
    fun <CALL : CapabilityCall<RESULT>, RESULT : Any> call(request: CALL): RESULT

    /**
     * Schedules the same blocking Capability Handler through the bounded Capability executor.
     * Every failure is reported by the returned stage.
     */
    fun <CALL : CapabilityCall<RESULT>, RESULT : Any> callAsync(request: CALL): CompletionStage<RESULT>

    companion object {
        @JvmStatic
        val instance: CapabilitySupervisor
            get() = CapabilitySupervisorSupport.instance
    }
}
