package com.only4.cap4k.ddd.core.application.invocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InvocationScopeTest {
    @Test
    fun `scope manager is a strict LIFO stack`() {
        val manager = DefaultInvocationScopeManager()
        val command = manager.enter(InvocationKind.COMMAND)
        val capability = manager.enter(InvocationKind.CAPABILITY)

        assertEquals(InvocationKind.CAPABILITY, manager.current())
        assertThrows<IllegalStateException> { command.close() }
        capability.close()
        assertEquals(InvocationKind.COMMAND, manager.current())
        command.close()
        assertNull(manager.current())
    }

    @Test
    fun `policy enforces the complete invocation matrix`() {
        val allowed = mapOf(
            InvocationKind.COMMAND to setOf(InvocationKind.COMMAND, InvocationKind.CAPABILITY),
            InvocationKind.QUERY to setOf(InvocationKind.QUERY, InvocationKind.CAPABILITY),
            InvocationKind.CAPABILITY to setOf(InvocationKind.CAPABILITY),
            InvocationKind.DOMAIN_EVENT_HANDLER to setOf(InvocationKind.COMMAND, InvocationKind.CAPABILITY),
        )

        InvocationKind.entries.forEach { current ->
            InvocationKind.entries
                .filter { it != InvocationKind.DOMAIN_EVENT_HANDLER }
                .forEach { requested ->
                    val manager = DefaultInvocationScopeManager()
                    val policy = InvocationPolicy(manager)
                    val scope = manager.enter(current)
                    try {
                        if (requested in allowed.getValue(current)) {
                            policy.check(requested)
                        } else {
                            val failure = assertThrows<InvocationNotAllowedException> {
                                policy.check(requested)
                            }
                            assertEquals(current, failure.currentKind)
                            assertEquals(requested, failure.requestedKind)
                        }
                    } finally {
                        scope.close()
                    }
                }
        }
    }

    @Test
    fun `only nested async Query adds a stricter scheduling rule`() {
        val manager = DefaultInvocationScopeManager()
        val policy = InvocationPolicy(manager)
        val scope = manager.enter(InvocationKind.QUERY)

        val failure = try {
            assertThrows<InvocationNotAllowedException> {
                policy.check(InvocationKind.QUERY, asynchronous = true)
            }
        } finally {
            scope.close()
        }

        assertEquals(InvocationKind.QUERY, failure.currentKind)
        assertEquals(InvocationKind.QUERY, failure.requestedKind)
        assertEquals(true, failure.asynchronous)
    }
}
