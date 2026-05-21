package com.only4.cap4k.ddd.core.domain.event.impl

import java.util.IdentityHashMap

internal class EventRuntimeScope(
    val type: EventRuntimeScopeType,
) {
    val domainAttachments: MutableMap<Any, MutableList<EventAttachment<Any>>> = IdentityHashMap()
    val integrationAttachments: MutableList<EventAttachment<Any>> = mutableListOf()

    fun attachDomain(entity: Any, attachment: EventAttachment<Any>) {
        domainAttachments.getOrPut(entity) { mutableListOf() }.add(attachment)
    }

    fun attachIntegration(attachment: EventAttachment<Any>) {
        integrationAttachments.add(attachment)
    }

    fun clearAttachments() {
        domainAttachments.clear()
        integrationAttachments.clear()
    }
}
