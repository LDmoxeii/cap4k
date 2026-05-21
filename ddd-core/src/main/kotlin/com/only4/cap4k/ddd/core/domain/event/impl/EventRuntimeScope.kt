package com.only4.cap4k.ddd.core.domain.event.impl

import java.util.IdentityHashMap
import java.lang.reflect.Method

internal class EventRuntimeScope(
    internal val type: EventRuntimeScopeType,
) {
    internal val domainAttachments: MutableMap<Any, MutableList<EventAttachment<Any>>> = IdentityHashMap()
    internal val integrationAttachments: MutableList<EventAttachment<Any>> = mutableListOf()
    internal var listenerBeanName: String? = null
    internal var listenerClass: Class<*>? = null
    internal var listenerMethod: Method? = null

    internal fun attachDomain(entity: Any, attachment: EventAttachment<Any>) {
        domainAttachments.getOrPut(entity) { mutableListOf() }.add(attachment)
    }

    internal fun attachIntegration(attachment: EventAttachment<Any>) {
        integrationAttachments.add(attachment)
    }

    internal fun clearAttachments() {
        domainAttachments.clear()
        integrationAttachments.clear()
    }

    internal fun captureListenerMetadata(): ListenerMetadata =
        ListenerMetadata(
            beanName = listenerBeanName,
            listenerClass = listenerClass,
            listenerMethod = listenerMethod,
        )

    internal fun restoreListenerMetadata(metadata: ListenerMetadata) {
        listenerBeanName = metadata.beanName
        listenerClass = metadata.listenerClass
        listenerMethod = metadata.listenerMethod
    }

    internal data class ListenerMetadata(
        val beanName: String?,
        val listenerClass: Class<*>?,
        val listenerMethod: Method?,
    )
}
