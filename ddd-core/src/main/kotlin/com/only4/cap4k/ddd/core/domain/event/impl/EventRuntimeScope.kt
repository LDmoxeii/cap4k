package com.only4.cap4k.ddd.core.domain.event.impl

import java.util.IdentityHashMap

class EventRuntimeScope internal constructor(
    internal val type: EventRuntimeScopeType,
) {
    internal val domainAttachments: MutableMap<Any, MutableList<EventAttachment<Any>>> = IdentityHashMap()
    internal val integrationAttachments: MutableList<EventAttachment<Any>> = mutableListOf()

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
}
