package com.only4.cap4k.ddd.core.domain.event.impl

import java.lang.reflect.Method

class EventListenerInvocationException(
    val listenerBeanName: String,
    val listenerClass: Class<*>,
    val listenerMethod: Method,
    val eventPayloadClass: Class<*>?,
    val diagnosticContext: EventDispatchDiagnostic?,
    cause: Throwable,
) : RuntimeException(
    buildMessage(listenerBeanName, listenerClass, listenerMethod, eventPayloadClass, diagnosticContext),
    cause,
) {
    companion object {
        private fun buildMessage(
            listenerBeanName: String,
            listenerClass: Class<*>,
            listenerMethod: Method,
            eventPayloadClass: Class<*>?,
            diagnosticContext: EventDispatchDiagnostic?,
        ): String =
            buildString {
                append("Event listener invocation failed for ")
                append(listenerClass.name)
                append("#")
                append(listenerMethod.name)
                append(" bean=")
                append(listenerBeanName)
                eventPayloadClass?.let {
                    append(" payload=")
                    append(it.name)
                }
                diagnosticContext?.let {
                    append(" scope=")
                    append(it.scopeType)
                }
            }
    }
}
