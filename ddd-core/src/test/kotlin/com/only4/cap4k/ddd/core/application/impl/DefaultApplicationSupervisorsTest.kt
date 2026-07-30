package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.capability.impl.DefaultCapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandInterceptor
import com.only4.cap4k.ddd.core.application.command.impl.DefaultCommandSupervisor
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.impl.DefaultQuerySupervisor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultApplicationSupervisorsTest {
    @Test
    fun `command dispatcher invokes matching interceptors around handler`() {
        val calls = mutableListOf<String>()
        val handler = object : CommandHandler<TestCommand, String> {
            override fun handle(command: TestCommand): String {
                calls += "handle:${command.value}"
                return "result:${command.value}"
            }
        }
        val interceptor = object : CommandInterceptor<TestCommand, String> {
            override fun beforeCommand(command: TestCommand) {
                calls += "before:${command.value}"
            }

            override fun afterCommand(command: TestCommand, result: String) {
                calls += "after:$result"
            }
        }
        val unitOfWork = RecordingUnitOfWork()
        val supervisor = DefaultCommandSupervisor(listOf(handler), listOf(interceptor), null) { unitOfWork }
            .apply { init() }

        assertEquals("result:ok", supervisor.send(TestCommand("ok")))
        assertEquals(listOf("before:ok", "handle:ok", "after:result:ok"), calls)
        assertEquals(1, unitOfWork.executionCount)
    }

    @Test
    fun `query and capability use independent dispatchers`() {
        val querySupervisor = DefaultQuerySupervisor(
            handlers = listOf(object : QueryHandler<TestQuery, String> {
                override fun handle(query: TestQuery): String = "query:${query.value}"
            }),
            interceptors = emptyList(),
            validator = null,
        ).apply { init() }
        val capabilitySupervisor = DefaultCapabilitySupervisor(
            handlers = listOf(object : CapabilityHandler<TestCapability, String> {
                override fun call(request: TestCapability): String = "capability:${request.value}"
            }),
            interceptors = emptyList(),
            validator = null,
        ).apply { init() }

        assertEquals("query:ok", querySupervisor.ask(TestQuery("ok")))
        assertEquals("capability:ok", capabilitySupervisor.call(TestCapability("ok")))
    }

    @Test
    fun `dispatcher resolves handler generic through an intermediate interface`() {
        val supervisor = DefaultCommandSupervisor(
            handlers = listOf(IndirectCommandHandler()),
            interceptors = emptyList(),
            validator = null,
            unitOfWorkProvider = { RecordingUnitOfWork() },
        ).apply { init() }

        assertEquals("indirect:ok", supervisor.send(TestCommand("ok")))
    }

    @Test
    fun `duplicate handlers fail during initialization`() {
        val error = assertThrows<IllegalStateException> {
            DefaultCommandSupervisor(
                handlers = listOf(FirstCommandHandler(), SecondCommandHandler()),
                interceptors = emptyList(),
                validator = null,
                unitOfWorkProvider = { RecordingUnitOfWork() },
            ).init()
        }

        assertTrue(error.message.orEmpty().contains("Multiple command handlers"))
        assertTrue(error.message.orEmpty().contains(TestCommand::class.java.name))
    }

    data class TestCommand(val value: String) : Command<String>

    data class TestQuery(val value: String) : Query<String>

    data class TestCapability(val value: String) : CapabilityCall<String>

    interface IntermediateCommandHandler<C : Command<R>, R : Any> : CommandHandler<C, R>

    class IndirectCommandHandler : IntermediateCommandHandler<TestCommand, String> {
        override fun handle(command: TestCommand): String = "indirect:${command.value}"
    }

    class FirstCommandHandler : CommandHandler<TestCommand, String> {
        override fun handle(command: TestCommand): String = command.value
    }

    class SecondCommandHandler : CommandHandler<TestCommand, String> {
        override fun handle(command: TestCommand): String = command.value
    }

    private class RecordingUnitOfWork : UnitOfWork {
        override val active: Boolean = false
        var executionCount: Int = 0

        override fun <RESULT> execute(block: () -> RESULT): RESULT {
            executionCount += 1
            return block()
        }

        override fun persist(entity: Any, intent: PersistIntent) = Unit

        override fun remove(entity: Any) = Unit

        override fun flush() = Unit
    }
}
