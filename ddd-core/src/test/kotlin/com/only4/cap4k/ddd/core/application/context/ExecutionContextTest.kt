package com.only4.cap4k.ddd.core.application.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import java.util.concurrent.Executor

class ExecutionContextTest {
    private val actorKey = ExecutionContextKey("actor", Actor::class.java)

    @Test
    fun `snapshot builder requires explicit replacement and stays immutable`() {
        val builder = ExecutionContextSnapshot.builder().put(actorKey, Actor("alice"))

        val duplicate = assertThrows<IllegalStateException> {
            builder.put(actorKey, Actor("bob"))
        }
        assertTrue(duplicate.message.orEmpty().contains("replace"))
        assertThrows<IllegalStateException> {
            builder.put(ExecutionContextKey("actor", Trace::class.java), Trace("trace"))
        }

        val first = builder.build()
        val second = first.toBuilder().replace(actorKey, Actor("bob")).build()
        assertEquals("alice", first[actorKey]?.name)
        assertEquals("bob", second[actorKey]?.name)
        assertTrue(first.toString().contains("actor"))
        assertTrue(!first.toString().contains("alice"))
    }

    @Test
    fun `execution context scopes enforce LIFO close and restore previous snapshot`() {
        val manager = DefaultExecutionContextManager()
        val firstSnapshot = ExecutionContextSnapshot.builder().put(actorKey, Actor("first")).build()
        val secondSnapshot = ExecutionContextSnapshot.builder().put(actorKey, Actor("second")).build()
        val first = manager.install(firstSnapshot)
        val second = manager.install(secondSnapshot)

        assertEquals("second", manager.current()[actorKey]?.name)
        assertThrows<IllegalStateException> { first.close() }
        assertEquals("second", manager.current()[actorKey]?.name)
        second.close()
        assertSame(firstSnapshot, manager.current())
        first.close()
        assertSame(ExecutionContextSnapshot.EMPTY, manager.current())
    }

    @Test
    fun `codec registry uses current version and distinguishes reliable from external unknown elements`() {
        val registry = ExecutionContextCodecRegistry(
            listOf(
                ActorCodec(1),
                ActorCodec(2),
            ),
        )
        val snapshot = ExecutionContextSnapshot.builder().put(actorKey, Actor("alice")).build()

        val encoded = registry.encode(snapshot, ExecutionContextBoundary.RELIABLE_COMMAND)
        assertEquals(listOf(EncodedExecutionContextElement("actor", 2, "v2:alice")), encoded)
        assertEquals(snapshot, registry.decodeReliable(encoded, ExecutionContextBoundary.RELIABLE_COMMAND))

        val unknown = EncodedExecutionContextElement("future", 1, "value")
        assertThrows<ExecutionContextDecodingException> {
            registry.decodeReliable(listOf(unknown), ExecutionContextBoundary.RELIABLE_COMMAND)
        }
        assertSame(
            ExecutionContextSnapshot.EMPTY,
            registry.decodeExternal(listOf(unknown), ExecutionContextBoundary.RPC),
        )
    }

    @Test
    fun `codec registry rejects duplicate incompatible unsupported and malformed elements`() {
        assertThrows<IllegalArgumentException> {
            ExecutionContextCodecRegistry(listOf(ActorCodec(1), TraceCodecWithActorName))
        }
        val registry = ExecutionContextCodecRegistry(listOf(ActorCodec(1)))
        val encoded = EncodedExecutionContextElement("actor", 1, "v1:alice")

        assertThrows<ExecutionContextDecodingException> {
            registry.decodeReliable(listOf(encoded, encoded), ExecutionContextBoundary.RELIABLE_COMMAND)
        }
        assertThrows<ExecutionContextDecodingException> {
            registry.decodeReliable(
                listOf(EncodedExecutionContextElement("actor", 99, "alice")),
                ExecutionContextBoundary.RELIABLE_COMMAND,
            )
        }
        assertThrows<ExecutionContextDecodingException> {
            registry.decodeReliable(
                listOf(EncodedExecutionContextElement("actor", 1, "broken")),
                ExecutionContextBoundary.RELIABLE_COMMAND,
            )
        }
        assertThrows<ExecutionContextDecodingException> {
            registry.decodeExternal(listOf(encoded), ExecutionContextBoundary.INTEGRATION_EVENT)
        }
    }

    @Test
    fun `propagation wrappers capture at wrapping time and restore worker context`() {
        val manager = DefaultExecutionContextManager()
        val propagation = ExecutionContextPropagation(manager, manager)
        val snapshot = ExecutionContextSnapshot.builder().put(actorKey, Actor("alice")).build()
        val scope = manager.install(snapshot)
        val runnable = propagation.wrap(Runnable { assertEquals("alice", manager.current()[actorKey]?.name) })
        val callable = propagation.wrap(Callable { manager.current()[actorKey]?.name })
        var decoratedActor: String? = null
        val directExecutor = Executor { it.run() }
        val decorated = propagation.decorate(directExecutor)
        scope.close()

        runnable.run()
        assertEquals("alice", callable.call())
        val secondScope = manager.install(snapshot)
        decorated.execute { decoratedActor = manager.current()[actorKey]?.name }
        secondScope.close()
        assertEquals("alice", decoratedActor)
        assertSame(ExecutionContextSnapshot.EMPTY, manager.current())
    }

    private data class Actor(val name: String) : ExecutionContextElement

    private data class Trace(val value: String) : ExecutionContextElement

    private inner class ActorCodec(
        override val version: Int,
    ) : ExecutionContextElementCodec<Actor> {
        override val key: ExecutionContextKey<Actor> = actorKey
        override val boundaries: Set<ExecutionContextBoundary> = setOf(
            ExecutionContextBoundary.RELIABLE_COMMAND,
            ExecutionContextBoundary.RPC,
        )

        override fun encode(element: Actor): String = "v$version:${element.name}"

        override fun decode(value: String): Actor {
            require(value.startsWith("v$version:"))
            return Actor(value.substringAfter(':'))
        }
    }

    private object TraceCodecWithActorName : ExecutionContextElementCodec<Trace> {
        override val key = ExecutionContextKey("actor", Trace::class.java)
        override val version: Int = 2
        override val boundaries: Set<ExecutionContextBoundary> = setOf(ExecutionContextBoundary.RPC)
        override fun encode(element: Trace): String = element.value
        override fun decode(value: String): Trace = Trace(value)
    }
}
