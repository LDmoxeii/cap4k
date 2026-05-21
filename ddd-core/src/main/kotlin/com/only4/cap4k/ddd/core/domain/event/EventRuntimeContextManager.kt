package com.only4.cap4k.ddd.core.domain.event

import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext

/**
 * Public facade for clearing event runtime context owned by ddd-core internals.
 */
object EventRuntimeContextManager {

    @JvmStatic
    fun reset() {
        EventRuntimeContext.reset()
    }
}
