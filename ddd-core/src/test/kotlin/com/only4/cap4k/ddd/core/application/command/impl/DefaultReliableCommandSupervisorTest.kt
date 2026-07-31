package com.only4.cap4k.ddd.core.application.command.impl

import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandTransaction
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class DefaultReliableCommandSupervisorTest {
    @Test
    fun `reliable registration requires an active command unit of work`() {
        val repository = RecordingCommandRecordRepository()
        val supervisor = TestReliableCommandSupervisor(
            commandSupervisor = UnusedCommandSupervisor,
            repository = repository,
            unitOfWork = StaticUnitOfWork(active = false),
            transaction = RecordingTransaction(active = true),
        )

        val failure = assertThrows<IllegalStateException> {
            supervisor.schedule(TestCommand("blocked"), LocalDateTime.now())
        }

        assertTrue(failure.message.orEmpty().contains("active Command Unit of Work"))
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `reliable registration requires the physical source transaction`() {
        val repository = RecordingCommandRecordRepository()
        val supervisor = TestReliableCommandSupervisor(
            commandSupervisor = UnusedCommandSupervisor,
            repository = repository,
            unitOfWork = StaticUnitOfWork(active = true),
            transaction = RecordingTransaction(active = false),
        )

        val failure = assertThrows<IllegalStateException> {
            supervisor.schedule(TestCommand("blocked"), LocalDateTime.now())
        }

        assertTrue(failure.message.orEmpty().contains("physical transaction"))
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `source transaction rollback never wakes the worker`() {
        val transaction = RecordingTransaction(active = true)
        val repository = RecordingCommandRecordRepository()
        val supervisor = TestReliableCommandSupervisor(
            commandSupervisor = UnusedCommandSupervisor,
            repository = repository,
            unitOfWork = StaticUnitOfWork(active = true),
            transaction = transaction,
        )

        supervisor.schedule(TestCommand("rollback"), LocalDateTime.now())

        assertEquals(1, repository.saveCount)
        assertEquals(0, supervisor.workerSignals)
        transaction.rollback()
        assertEquals(0, supervisor.workerSignals)
    }

    @Test
    fun `worker is woken only after source transaction commit`() {
        val transaction = RecordingTransaction(active = true)
        val supervisor = TestReliableCommandSupervisor(
            commandSupervisor = UnusedCommandSupervisor,
            repository = RecordingCommandRecordRepository(),
            unitOfWork = StaticUnitOfWork(active = true),
            transaction = transaction,
        )

        supervisor.schedule(TestCommand("commit"), LocalDateTime.now())

        assertEquals(0, supervisor.workerSignals)
        transaction.commit()
        assertEquals(1, supervisor.workerSignals)
    }

    @Test
    fun `worker dispatches the reliable command through a fresh command unit of work`() {
        val workerUnitOfWork = RecordingUnitOfWork()
        val invocationScopes = DefaultInvocationScopeManager()
        var handlerObservedActiveUnitOfWork = false
        val commandSupervisor = DefaultCommandSupervisor(
            handlers = listOf(object : CommandHandler<TestCommand, String> {
                override fun handle(command: TestCommand): String {
                    handlerObservedActiveUnitOfWork = workerUnitOfWork.active
                    return "handled:${command.value}"
                }
            }),
            interceptors = emptyList(),
            validator = null,
            unitOfWorkProvider = { workerUnitOfWork },
            invocationPolicy = InvocationPolicy(invocationScopes),
            invocationScopeManager = invocationScopes,
        ).apply { init() }
        val repository = RecordingCommandRecordRepository()
        val record = repository.create().apply {
            init(
                command = TestCommand("worker"),
                serviceName = "test-service",
                commandType = TestCommand::class.java.name,
                scheduleAt = LocalDateTime.now(),
                expireAfter = Duration.ofMinutes(1),
                retryTimes = 1,
            )
            beginCommand(LocalDateTime.now())
        }
        val supervisor = TestReliableCommandSupervisor(
            commandSupervisor = commandSupervisor,
            repository = repository,
            unitOfWork = workerUnitOfWork,
            transaction = RecordingTransaction(active = true),
        )

        val result = supervisor.executeWorker(record.command, record)

        assertEquals("handled:worker", result)
        assertEquals(1, workerUnitOfWork.executionCount)
        assertTrue(handlerObservedActiveUnitOfWork)
        assertFalse(workerUnitOfWork.active)
        assertTrue(record.isExecuted)
    }

    @Test
    fun `capability cannot register a reliable command even when caller runs inside a command uow`() {
        val repository = RecordingCommandRecordRepository()
        val supervisor = TestReliableCommandSupervisor(
            commandSupervisor = UnusedCommandSupervisor,
            repository = repository,
            unitOfWork = StaticUnitOfWork(active = true),
            transaction = RecordingTransaction(active = true),
            invocationScopeAccessor = InvocationScopeAccessor { InvocationKind.CAPABILITY },
        )

        val failure = assertThrows<IllegalStateException> {
            supervisor.schedule(TestCommand("blocked"), LocalDateTime.now())
        }

        assertTrue(failure.message.orEmpty().contains("COMMAND or DOMAIN_EVENT_HANDLER"))
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `registration context is restored for worker execution and then cleared`() {
        val contextManager = DefaultExecutionContextManager()
        val codecRegistry = ExecutionContextCodecRegistry(listOf(TestContextCodec))
        val repository = RecordingCommandRecordRepository()
        var observedContext: TestContext? = null
        val commandSupervisor = object : CommandSupervisor {
            @Suppress("UNCHECKED_CAST")
            override fun <COMMAND : Command<RESULT>, RESULT : Any> send(command: COMMAND): RESULT {
                observedContext = contextManager.current()[TestContextKey]
                return "handled" as RESULT
            }
        }
        val supervisor = TestReliableCommandSupervisor(
            commandSupervisor = commandSupervisor,
            repository = repository,
            unitOfWork = StaticUnitOfWork(active = true),
            transaction = RecordingTransaction(active = true),
            executionContextAccessor = contextManager,
            executionContextScopeManager = contextManager,
            executionContextCodecRegistry = codecRegistry,
        )
        val origin = ExecutionContextSnapshot.builder()
            .put(TestContextKey, TestContext("origin-actor"))
            .build()

        contextManager.install(origin).use {
            supervisor.schedule(TestCommand("context"), LocalDateTime.now())
        }
        val record = repository.single().also { it.beginCommand(LocalDateTime.now()) }

        supervisor.executeWorker(record.command, record)

        assertEquals(TestContext("origin-actor"), observedContext)
        assertTrue(contextManager.current().isEmpty)
        assertEquals(
            listOf(EncodedExecutionContextElement("test-context", 1, "origin-actor")),
            record.executionContext,
        )
    }

    private data class TestCommand(val value: String) : Command<String>

    private class TestReliableCommandSupervisor(
        commandSupervisor: CommandSupervisor,
        repository: CommandRecordRepository,
        unitOfWork: CommandUnitOfWorkCoordinator,
        transaction: ReliableCommandTransaction,
        executionContextAccessor: ExecutionContextAccessor = ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
        executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager { AutoCloseable { } },
        executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
        invocationScopeAccessor: InvocationScopeAccessor = InvocationScopeAccessor { InvocationKind.COMMAND },
    ) : DefaultReliableCommandSupervisor(
        commandSupervisor = commandSupervisor,
        validator = null,
        commandRecordRepository = repository,
        unitOfWorkProvider = { unitOfWork },
        transaction = transaction,
        serviceName = "test-service",
        threadPoolSize = 1,
        threadFactoryClassName = "",
        executionContextAccessor = executionContextAccessor,
        executionContextScopeManager = executionContextScopeManager,
        executionContextCodecRegistry = executionContextCodecRegistry,
        invocationScopeAccessor = invocationScopeAccessor,
    ) {
        var workerSignals: Int = 0
            private set

        override fun scheduleExecution(command: Command<*>, record: CommandRecord) {
            workerSignals += 1
        }

        fun executeWorker(command: Command<*>, record: CommandRecord): Any = internalSend(command, record)
    }

    private object UnusedCommandSupervisor : CommandSupervisor {
        override fun <COMMAND : Command<RESULT>, RESULT : Any> send(command: COMMAND): RESULT =
            error("worker execution was not expected")
    }

    private class RecordingTransaction(var active: Boolean) : ReliableCommandTransaction {
        private val afterCommitActions = mutableListOf<() -> Unit>()

        override fun requireActive() {
            check(active) { "Reliable Command registration requires an active physical transaction" }
        }

        override fun afterCommit(action: () -> Unit) {
            requireActive()
            afterCommitActions += action
        }

        fun commit() {
            val actions = afterCommitActions.toList()
            afterCommitActions.clear()
            active = false
            actions.forEach { it() }
        }

        fun rollback() {
            afterCommitActions.clear()
            active = false
        }
    }

    private class StaticUnitOfWork(override val active: Boolean) : CommandUnitOfWorkCoordinator {
        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
    }

    private class RecordingUnitOfWork : CommandUnitOfWorkCoordinator {
        private var depth: Int = 0
        var executionCount: Int = 0
            private set

        override val active: Boolean
            get() = depth > 0

        override fun <RESULT> execute(block: () -> RESULT): RESULT {
            executionCount += 1
            depth += 1
            return try {
                block()
            } finally {
                depth -= 1
            }
        }

    }

    private class RecordingCommandRecordRepository : CommandRecordRepository {
        private val records = linkedMapOf<String, TestCommandRecord>()
        var saveCount: Int = 0
            private set

        override fun create(): CommandRecord = TestCommandRecord()

        override fun save(commandRecord: CommandRecord) {
            saveCount += 1
            records[commandRecord.id] = commandRecord as TestCommandRecord
        }

        override fun getById(id: String): CommandRecord = requireNotNull(records[id])

        override fun getByNextTryTime(
            serviceName: String,
            maxNextTryTime: LocalDateTime,
            limit: Int,
        ): List<CommandRecord> = records.values.take(limit)

        override fun archiveByExpireAt(serviceName: String, maxExpireAt: LocalDateTime, limit: Int): Int = 0

        fun single(): CommandRecord = records.values.single()
    }

    private class TestCommandRecord : CommandRecord {
        private lateinit var payload: Command<*>
        private var result: Any? = null
        private var executing: Boolean = false
        private var executed: Boolean = false

        override val id: String = "command-${nextId++}"
        override lateinit var type: String
        override val command: Command<*>
            get() = payload
        override var executionContext: List<EncodedExecutionContextElement> = emptyList()
            private set
        override lateinit var scheduleTime: LocalDateTime
        override lateinit var nextTryTime: LocalDateTime
        override val isValid: Boolean
            get() = !executed
        override val isInvalid: Boolean
            get() = false
        override val isExecuting: Boolean
            get() = executing
        override val isExecuted: Boolean
            get() = executed

        override fun init(
            command: Command<*>,
            serviceName: String,
            commandType: String,
            scheduleAt: LocalDateTime,
            expireAfter: Duration,
            retryTimes: Int,
            executionContext: Collection<EncodedExecutionContextElement>,
        ) {
            payload = command
            type = commandType
            scheduleTime = scheduleAt
            nextTryTime = scheduleAt
            this.executionContext = executionContext.toList()
        }

        @Suppress("UNCHECKED_CAST")
        override fun <RESULT : Any> getResult(): RESULT? = result as RESULT?

        override fun beginCommand(now: LocalDateTime): Boolean {
            executing = true
            scheduleTime = now
            nextTryTime = now
            return true
        }

        override fun cancelCommand(now: LocalDateTime): Boolean {
            executing = false
            return true
        }

        override fun endCommand(now: LocalDateTime, result: Any) {
            this.result = result
            executing = false
            executed = true
        }

        override fun occurredException(now: LocalDateTime, throwable: Throwable) {
            executing = false
        }

        private companion object {
            var nextId: Int = 1
        }
    }

    private data class TestContext(val actor: String) : ExecutionContextElement

    private companion object {
        val TestContextKey = ExecutionContextKey("test-context", TestContext::class.java)

        val TestContextCodec = object : ExecutionContextElementCodec<TestContext> {
            override val key: ExecutionContextKey<TestContext> = TestContextKey
            override val version: Int = 1
            override val boundaries: Set<ExecutionContextBoundary> = setOf(ExecutionContextBoundary.RELIABLE_COMMAND)

            override fun encode(element: TestContext): String = element.actor

            override fun decode(value: String): TestContext = TestContext(value)
        }
    }
}
