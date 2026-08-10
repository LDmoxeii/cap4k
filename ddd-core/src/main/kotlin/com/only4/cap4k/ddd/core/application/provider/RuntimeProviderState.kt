package com.only4.cap4k.ddd.core.application.provider

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Runtime-owned live health facts for one provider slot. */
enum class RuntimeProviderState {
    HEALTHY,
    DEGRADED,
    RECOVERING,
}

/** A safe, transport-neutral provider state snapshot. */
data class RuntimeProviderStateFact(
    val providerId: String,
    val state: RuntimeProviderState,
    val observedAt: Instant,
    val category: String? = null,
) {
    init {
        require(providerId.isNotBlank()) { "Runtime provider ID must not be blank" }
        require(category == null || category.isNotBlank()) {
            "Runtime provider state category must not be blank when present"
        }
    }
}

/** Read/write source of truth for live provider state; projections may consume snapshots. */
interface RuntimeProviderStateRegistry {
    fun register(providerId: String): RuntimeProviderStateReporter

    fun snapshot(): List<RuntimeProviderStateFact>
}

interface RuntimeProviderStateReporter : AutoCloseable {
    val providerId: String

    fun report(
        state: RuntimeProviderState,
        category: String? = null,
        observedAt: Instant = Instant.now(),
    )

    override fun close()
}

/** In-memory process-local registry; it deliberately owns no broker or retry behavior. */
class InMemoryRuntimeProviderStateRegistry : RuntimeProviderStateRegistry {
    private val registrations = ConcurrentHashMap<String, Registration>()

    override fun register(providerId: String): RuntimeProviderStateReporter {
        require(providerId.isNotBlank()) { "Runtime provider ID must not be blank" }
        val token = Any()
        val registration = Registration(
            token = token,
            fact = RuntimeProviderStateFact(
                providerId = providerId,
                state = RuntimeProviderState.RECOVERING,
                observedAt = Instant.now(),
                category = "enrolled",
            ),
        )
        check(registrations.putIfAbsent(providerId, registration) == null) {
            "Runtime provider '$providerId' is already registered"
        }
        return Reporter(providerId, token)
    }

    override fun snapshot(): List<RuntimeProviderStateFact> = registrations.values
        .map(Registration::fact)
        .sortedBy { it.providerId }

    private inner class Reporter(
        override val providerId: String,
        private val token: Any,
    ) : RuntimeProviderStateReporter {
        private var closed = false

        @Synchronized
        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            check(!closed) { "Runtime provider '$providerId' state reporter is closed" }
            registrations.compute(providerId) { _, current ->
                check(current?.token === token) {
                    "Runtime provider '$providerId' state reporter no longer owns its registration"
                }
                Registration(
                    token = token,
                    fact = RuntimeProviderStateFact(providerId, state, observedAt, category),
                )
            }
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            registrations.computeIfPresent(providerId) { _, current ->
                current.takeUnless { it.token === token }
            }
        }
    }

    private data class Registration(
        val token: Any,
        val fact: RuntimeProviderStateFact,
    )
}
