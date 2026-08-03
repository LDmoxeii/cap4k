package com.only4.cap4k.ddd.core.application.invocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
            InvocationKind.DOMAIN_EVENT_HANDLER to setOf(
                InvocationKind.COMMAND,
                InvocationKind.QUERY,
                InvocationKind.CAPABILITY,
            ),
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

    @Test
    fun `scope waits for every tracked task before completing`() {
        val manager = DefaultInvocationScopeManager()
        val scope = manager.enter(InvocationKind.DOMAIN_EVENT_HANDLER)
        val first = CompletableFuture<String>()
        val second = CompletableFuture<String>()
        manager.track(first)
        manager.track(second)
        val allTasksCompleted = CountDownLatch(1)
        val completer = Thread {
            first.complete("first")
            Thread.sleep(100)
            second.complete("second")
            allTasksCompleted.countDown()
        }.apply { start() }

        assertEquals("handler-result", scope.complete { "handler-result" })
        assertEquals(true, allTasksCompleted.await(1, TimeUnit.SECONDS))
        scope.close()
        completer.join(1_000)
        assertNull(manager.current())
    }

    @Test
    fun `body failure stays primary and managed task failures are suppressed`() {
        val manager = DefaultInvocationScopeManager()
        val scope = manager.enter(InvocationKind.COMMAND)
        val taskFailure = IllegalArgumentException("task")
        manager.track(CompletableFuture.failedFuture<String>(taskFailure))
        val bodyFailure = IllegalStateException("body")

        val thrown = try {
            assertThrows<IllegalStateException> {
                scope.complete<String> { throw bodyFailure }
            }
        } finally {
            scope.close()
        }

        assertEquals(bodyFailure, thrown)
        assertEquals(listOf(taskFailure), thrown.suppressed.toList())
        assertNull(manager.current())
    }

    @Test
    fun `nested scopes converge only their own tasks`() {
        val manager = DefaultInvocationScopeManager()
        val outer = manager.enter(InvocationKind.DOMAIN_EVENT_HANDLER)
        val outerTask = CompletableFuture<String>()
        manager.track(outerTask)
        val inner = manager.enter(InvocationKind.CAPABILITY)
        val innerTask = CompletableFuture<String>()
        manager.track(innerTask)
        innerTask.complete("inner")
        assertEquals("inner-result", inner.complete { "inner-result" })
        inner.close()
        outerTask.complete("outer")
        assertEquals("outer-result", outer.complete { "outer-result" })
        outer.close()

        assertNull(manager.current())
    }
}
