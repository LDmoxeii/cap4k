package com.only4.cap4k.ddd.core.application.capability

import jakarta.validation.ConstraintViolationException

/**
 * Synchronously invokes adapter-owned external capabilities.
 */
interface CapabilitySupervisor {
    /**
     * @throws ConstraintViolationException when capability-call validation fails
     */
    fun <CALL : CapabilityCall<RESULT>, RESULT : Any> call(request: CALL): RESULT

    companion object {
        @JvmStatic
        val instance: CapabilitySupervisor
            get() = CapabilitySupervisorSupport.instance
    }
}
