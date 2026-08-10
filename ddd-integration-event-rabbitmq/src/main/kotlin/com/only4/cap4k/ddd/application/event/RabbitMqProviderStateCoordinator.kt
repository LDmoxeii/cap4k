package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import java.time.Instant

/** Aggregates RabbitMQ component evidence without allowing one healthy path to mask another failure. */
class RabbitMqProviderStateCoordinator(
    private val delegate: RuntimeProviderStateReporter,
) {
    private val facts = Component.entries.associateWith { component ->
        ComponentFact(
            state = RuntimeProviderState.RECOVERING,
            category = "${component.id}-enrolled",
            observedAt = Instant.now(),
        )
    }.toMutableMap()

    val publisher: RuntimeProviderStateReporter = ComponentReporter(Component.PUBLISHER)
    val subscriber: RuntimeProviderStateReporter = ComponentReporter(Component.SUBSCRIBER)
    val topology: RuntimeProviderStateReporter = ComponentReporter(Component.TOPOLOGY)

    init {
        publishAggregate()
    }

    @Synchronized
    private fun report(
        component: Component,
        state: RuntimeProviderState,
        category: String?,
        observedAt: Instant,
    ) {
        facts[component] = ComponentFact(state, category, observedAt)
        publishAggregate()
    }

    private fun publishAggregate() {
        val (component, fact) = facts.entries
            .maxWithOrNull(
                compareBy<Map.Entry<Component, ComponentFact>>(
                    { severity(it.value.state) },
                    { it.value.observedAt },
                )
            )
            ?: return
        delegate.report(
            state = fact.state,
            category = listOfNotNull(component.id, fact.category).joinToString(":"),
            observedAt = fact.observedAt,
        )
    }

    private inner class ComponentReporter(
        private val component: Component,
    ) : RuntimeProviderStateReporter {
        override val providerId: String = delegate.providerId

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            this@RabbitMqProviderStateCoordinator.report(component, state, category, observedAt)
        }

        override fun close() = Unit
    }

    private data class ComponentFact(
        val state: RuntimeProviderState,
        val category: String?,
        val observedAt: Instant,
    )

    private enum class Component(val id: String) {
        PUBLISHER("publisher"),
        SUBSCRIBER("subscriber"),
        TOPOLOGY("topology"),
    }

    private companion object {
        fun severity(state: RuntimeProviderState): Int = when (state) {
            RuntimeProviderState.HEALTHY -> 0
            RuntimeProviderState.RECOVERING -> 1
            RuntimeProviderState.DEGRADED -> 2
        }
    }
}
