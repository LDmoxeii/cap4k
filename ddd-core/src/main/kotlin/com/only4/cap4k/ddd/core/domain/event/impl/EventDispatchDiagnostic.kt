package com.only4.cap4k.ddd.core.domain.event.impl

data class EventDispatchDiagnostic(
    val scopeType: String,
    val domainAttachmentCount: Int,
    val integrationAttachmentCount: Int,
    val listenerBeanName: String? = null,
    val listenerClassName: String? = null,
    val listenerMethodName: String? = null,
    val causalPath: List<String> = emptyList(),
)

internal object EventDispatchDiagnostics {
    fun snapshot(scope: EventRuntimeScope?): EventDispatchDiagnostic? =
        scope?.let {
            EventDispatchDiagnostic(
                scopeType = it.type.name,
                domainAttachmentCount = it.domainAttachments.values.sumOf { attachments -> attachments.size },
                integrationAttachmentCount = it.integrationAttachments.size,
                listenerBeanName = it.listenerBeanName,
                listenerClassName = it.listenerClass?.name,
                listenerMethodName = it.listenerMethod?.name,
                causalPath = EventRuntimeContext.diagnosticCausalPath(),
            )
        }
}
