package com.only4.cap4k.ddd.core.domain.event.impl

import java.util.concurrent.ConcurrentHashMap

class Cap4kEventHandlerRegistry {
    private val handlers = ConcurrentHashMap<HandlerKey, RegisteredCap4kEventHandler>()

    fun register(
        descriptor: Cap4kEventHandlerDescriptor,
        listener: Cap4kApplicationListenerMethodAdapter,
    ) {
        val key = HandlerKey(descriptor.beanName, descriptor.method.toGenericString())
        val previous = handlers.putIfAbsent(key, RegisteredCap4kEventHandler(descriptor, listener))
        require(previous == null) {
            "Duplicate cap4k event Handler registration for ${descriptor.targetClass.name}#${descriptor.method.name} " +
                "bean=${descriptor.beanName}"
        }
    }

    fun handlersFor(eventPayloadClass: Class<*>): List<RegisteredCap4kEventHandler> =
        handlers.values
            .asSequence()
            .filter { handler -> handler.descriptor.eventPayloadClass == eventPayloadClass }
            .sortedBy { handler -> handler.descriptor.order }
            .toList()

    private data class HandlerKey(
        val beanName: String,
        val method: String,
    )
}

data class RegisteredCap4kEventHandler(
    val descriptor: Cap4kEventHandlerDescriptor,
    val listener: Cap4kApplicationListenerMethodAdapter,
)
