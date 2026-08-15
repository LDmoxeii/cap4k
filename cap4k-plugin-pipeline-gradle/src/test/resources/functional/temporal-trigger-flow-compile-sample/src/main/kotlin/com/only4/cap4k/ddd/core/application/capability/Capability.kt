package com.only4.cap4k.ddd.core.application.capability

interface CapabilityCall<R : Any>

interface CapabilitySupervisor {
    fun <R : Any> call(capability: CapabilityCall<R>): R
}
