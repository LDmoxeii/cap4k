package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import org.springframework.context.PayloadApplicationEvent

class DefaultEventHandlerDispatcher(
    private val registry: Cap4kEventHandlerRegistry,
) : EventHandlerDispatcher {
    override fun dispatch(eventPayload: Any) {
        val applicationEvent = PayloadApplicationEvent(this, eventPayload)
        registry.handlersFor(eventPayload.javaClass).forEach { handler ->
            handler.listener.onApplicationEvent(applicationEvent)
        }
    }
}
