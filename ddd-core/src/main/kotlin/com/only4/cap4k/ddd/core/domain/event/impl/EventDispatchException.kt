package com.only4.cap4k.ddd.core.domain.event.impl

data class EventDispatchDiagnostic(
    val scopeType: String,
    val domainAttachmentCount: Int,
    val integrationAttachmentCount: Int,
    val listenerBeanName: String? = null,
    val listenerClassName: String? = null,
    val listenerMethodName: String? = null,
)

class EventDispatchException(
    val eventPayloadClass: Class<*>,
    val diagnosticContext: EventDispatchDiagnostic?,
    failures: List<EventSubscriberFailure>,
) : RuntimeException(
    buildMessage(eventPayloadClass, requireFailures(failures)),
    requireFailures(failures).first().cause,
) {
    val failures: List<EventSubscriberFailure> =
        java.util.Collections.unmodifiableList(requireFailures(failures).toList())

    init {
        this.failures.drop(1).forEach { addSuppressed(it.cause) }
    }

    companion object {
        internal fun snapshot(scope: EventRuntimeScope?): EventDispatchDiagnostic? =
            scope?.let {
                EventDispatchDiagnostic(
                    scopeType = it.type.name,
                    domainAttachmentCount = it.domainAttachments.values.sumOf { attachments -> attachments.size },
                    integrationAttachmentCount = it.integrationAttachments.size,
                    listenerBeanName = it.listenerBeanName,
                    listenerClassName = it.listenerClass?.name,
                    listenerMethodName = it.listenerMethod?.name,
                )
            }

        private fun requireFailures(failures: List<EventSubscriberFailure>): List<EventSubscriberFailure> {
            require(failures.isNotEmpty()) { "EventDispatchException requires at least one failure" }
            return failures
        }

        private fun buildMessage(
            eventPayloadClass: Class<*>,
            failures: List<EventSubscriberFailure>
        ): String {
            val subscriberClasses = failures.joinToString(", ") { it.subscriberClass.name }
            return "Event dispatch failed for ${eventPayloadClass.name}; failing subscribers: $subscriberClasses"
        }
    }
}
