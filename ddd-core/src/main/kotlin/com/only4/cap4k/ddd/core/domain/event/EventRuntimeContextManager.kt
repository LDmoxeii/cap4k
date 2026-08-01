package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext

/**
 * Public facade for event runtime lifecycle and diagnostics owned by ddd-core internals.
 */
object EventRuntimeContextManager {
    @JvmStatic
    fun beginUnitOfWork() {
        EventRuntimeContext.beginUnitOfWork()
    }

    @JvmStatic
    fun endUnitOfWork() {
        EventRuntimeContext.endUnitOfWork()
    }

    @JvmStatic
    fun diagnosticCausalPath(): List<String> = EventRuntimeContext.diagnosticCausalPath()

    @JvmStatic
    fun pendingIntegrationAttachmentCount(): Int =
        EventRuntimeContext.currentUnitOfWorkOrNull()?.integrationAttachments?.size ?: 0

    @JvmStatic
    fun reset() {
        EventRuntimeContext.reset()
    }
}
