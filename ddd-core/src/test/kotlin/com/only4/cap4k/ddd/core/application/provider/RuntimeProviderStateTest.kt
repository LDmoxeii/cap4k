package com.only4.cap4k.ddd.core.application.provider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class RuntimeProviderStateTest {
    @Test
    fun `registry exposes immutable ordered facts and rejects duplicate registration`() {
        val registry = InMemoryRuntimeProviderStateRegistry()
        val second = registry.register("provider.z")
        val first = registry.register("provider.a")

        assertEquals(listOf("provider.a", "provider.z"), registry.snapshot().map { it.providerId })
        assertThrows<IllegalStateException> { registry.register("provider.a") }

        val oldSnapshot = registry.snapshot()
        val observedAt = Instant.parse("2026-08-10T00:00:00Z")
        first.report(RuntimeProviderState.HEALTHY, "ready", observedAt)

        assertEquals(RuntimeProviderState.RECOVERING, oldSnapshot.first().state)
        assertEquals(
            RuntimeProviderStateFact("provider.a", RuntimeProviderState.HEALTHY, observedAt, "ready"),
            registry.snapshot().first(),
        )
        second.close()
    }

    @Test
    fun `close is idempotent and a later registration cannot be removed by the old reporter`() {
        val registry = InMemoryRuntimeProviderStateRegistry()
        val oldReporter = registry.register("provider")

        oldReporter.close()
        oldReporter.close()
        val newReporter = registry.register("provider")
        newReporter.report(RuntimeProviderState.HEALTHY, "new-owner")

        assertEquals(RuntimeProviderState.HEALTHY, registry.snapshot().single().state)
        assertThrows<IllegalStateException> {
            oldReporter.report(RuntimeProviderState.DEGRADED, "stale-owner")
        }
        assertEquals("new-owner", registry.snapshot().single().category)
    }

    @Test
    fun `blank provider and category values are rejected`() {
        val registry = InMemoryRuntimeProviderStateRegistry()
        assertThrows<IllegalArgumentException> { registry.register(" ") }
        val reporter = registry.register("provider")
        assertThrows<IllegalArgumentException> {
            reporter.report(RuntimeProviderState.HEALTHY, " ")
        }
    }
}
