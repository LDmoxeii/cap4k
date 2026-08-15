package com.only4.cap4k.ddd.core.application.endpoint

import com.only4.cap4k.ddd.core.ProviderSlot

object EndpointSupervisorSupport {
    private val slot = ProviderSlot<EndpointSupervisor>("endpoints", "cap4k-ddd-core-starter")

    val instance: EndpointSupervisor get() = slot.get()
    fun configure(supervisor: EndpointSupervisor) = slot.configure(supervisor)
    fun release(supervisor: EndpointSupervisor) = slot.release(supervisor)
}
