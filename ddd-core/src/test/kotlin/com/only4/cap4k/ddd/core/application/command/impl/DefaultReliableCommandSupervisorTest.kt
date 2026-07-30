package com.only4.cap4k.ddd.core.application.command.impl

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandTransaction
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

    private data class TestCommand(val value: String) : Command<String>

    private class TestReliableCommandSupervisor(
        commandSupervisor: CommandSupervisor,
        repository: CommandRecordRepository,
        unitOfWork: UnitOfWork,
        transaction: ReliableCommandTransaction,
    ) : DefaultReliableCommandSupervisor(
        commandSupervisor = commandSupervisor,
        validator = null,
        commandRecordRepository = repository,
        unitOfWorkProvider = { unitOfWork },
        transaction = transaction,
        serviceName = "test-service",
        threadPoolSize = 1,
        threadFactoryClassName = "",
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

    private class StaticUnitOfWork(override val active: Boolean) : UnitOfWork {
        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
        override fun persist(entity: Any, intent: PersistIntent) = Unit
        override fun remove(entity: Any) = Unit
        override fun flush() = Unit
    }

    private class RecordingUnitOfWork : UnitOfWork {
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

        override fun persist(entity: Any, intent: PersistIntent) = Unit
        override fun remove(entity: Any) = Unit
        override fun flush() = check(active)
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
        ) {
            payload = command
            type = commandType
            scheduleTime = scheduleAt
            nextTryTime = scheduleAt
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
}
