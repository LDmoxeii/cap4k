package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import java.time.Instant

/**
 * Aggregates the live RocketMQ publisher and subscriber facts into one provider
 * registration. A healthy component never masks a degraded or recovering one.
 */
class RocketMqProviderStateCoordinator(
    private val delegate: RuntimeProviderStateReporter,
) {
    private var published: PublishedFact? = null
    private val facts = Component.entries.associateWith {
        ComponentFact(
            state = RuntimeProviderState.RECOVERING,
            category = "${it.id}-enrolled",
            observedAt = Instant.now(),
        )
    }.toMutableMap()

    val publisher: RuntimeProviderStateReporter = ComponentReporter(Component.PUBLISHER)
    val subscriber: RuntimeProviderStateReporter = ComponentReporter(Component.SUBSCRIBER)

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
        val aggregate = PublishedFact(
            state = fact.state,
            category = listOfNotNull(component.id, fact.category).joinToString(":"),
        )
        if (published == aggregate) return
        published = aggregate
        delegate.report(aggregate.state, aggregate.category, fact.observedAt)
    }

    private inner class ComponentReporter(
        private val component: Component,
    ) : RuntimeProviderStateReporter {
        override val providerId: String = delegate.providerId

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            this@RocketMqProviderStateCoordinator.report(component, state, category, observedAt)
        }

        override fun close() = Unit
    }

    private data class ComponentFact(
        val state: RuntimeProviderState,
        val category: String?,
        val observedAt: Instant,
    )

    private data class PublishedFact(
        val state: RuntimeProviderState,
        val category: String,
    )

    private enum class Component(val id: String) {
        PUBLISHER("publisher"),
        SUBSCRIBER("subscriber"),
    }

    private companion object {
        fun severity(state: RuntimeProviderState): Int = when (state) {
            RuntimeProviderState.HEALTHY -> 0
            RuntimeProviderState.RECOVERING -> 1
            RuntimeProviderState.DEGRADED -> 2
        }
    }
}
