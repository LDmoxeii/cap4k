package com.only4.cap4k.ddd.core.application.command.impl

import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.command.*
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class DefaultReliableCommandSupervisorTest {
    @Test
    fun `registration persists then wakes only after commit`() {
        val repository = RecordingRepository()
        val transaction = RecordingTransaction()
        val wakeUp = RecordingWakeUp()
        val supervisor = supervisor(repository, transaction, wakeUp)
        val scheduleAt = LocalDateTime.of(2026, 8, 7, 12, 30)

        val id = supervisor.schedule(TestCommand("work"), scheduleAt)
        assertEquals(emptyList<LocalDateTime>(), wakeUp.schedules)
        transaction.commit()

        assertEquals(repository.record.id, id)
        assertEquals(scheduleAt, repository.record.scheduleAt)
        assertEquals(listOf(scheduleAt), wakeUp.schedules)
    }

    @Test
    fun `rollback never wakes worker`() {
        val repository = RecordingRepository()
        val transaction = RecordingTransaction()
        val wakeUp = RecordingWakeUp()

        supervisor(repository, transaction, wakeUp).schedule(TestCommand("rollback"), LocalDateTime.now())
        transaction.rollback()

        assertEquals(1, repository.saveCount)
        assertTrue(wakeUp.schedules.isEmpty())
    }

    @Test
    fun `registration requires command scope unit of work and physical transaction`() {
        val repository = RecordingRepository()
        val inactiveUow = supervisor(repository, RecordingTransaction(), RecordingWakeUp(), uowActive = false)
        assertThrows<IllegalStateException> { inactiveUow.schedule(TestCommand("x"), LocalDateTime.now()) }

        val noTransaction = supervisor(repository, RecordingTransaction(active = false), RecordingWakeUp())
        assertThrows<IllegalStateException> { noTransaction.schedule(TestCommand("x"), LocalDateTime.now()) }

        val capability = supervisor(repository, RecordingTransaction(), RecordingWakeUp(), InvocationKind.CAPABILITY)
        assertThrows<IllegalStateException> { capability.schedule(TestCommand("x"), LocalDateTime.now()) }
    }

    private fun supervisor(
        repository: RecordingRepository,
        transaction: RecordingTransaction,
        wakeUp: RecordingWakeUp,
        scope: InvocationKind = InvocationKind.COMMAND,
        uowActive: Boolean = true,
    ) = DefaultReliableCommandSupervisor(
        validator = null,
        commandRecordRepository = repository,
        unitOfWorkProvider = { StaticUnitOfWork(uowActive) },
        transaction = transaction,
        wakeUp = wakeUp,
        serviceName = "test-service",
        executionContextAccessor = ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
        executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
        invocationScopeAccessor = InvocationScopeAccessor { scope },
    )

    private data class TestCommand(val value: String) : Command<String>
    private class StaticUnitOfWork(override val active: Boolean) : CommandUnitOfWorkCoordinator {
        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
    }
    private class RecordingWakeUp : ReliableCommandWakeUp {
        val schedules = mutableListOf<LocalDateTime>()
        override fun wakeUp(scheduleAt: LocalDateTime) { schedules += scheduleAt }
    }
    private class RecordingTransaction(var active: Boolean = true) : ReliableCommandTransaction {
        private val actions = mutableListOf<() -> Unit>()
        override fun requireActive() { check(active) { "physical transaction required" } }
        override fun afterCommit(action: () -> Unit) { requireActive(); actions += action }
        fun commit() {
            val committedActions = actions.toList()
            actions.clear()
            committedActions.forEach { it() }
            active = false
        }
        fun rollback() { actions.clear(); active = false }
    }
    private class RecordingRepository : CommandRecordRepository {
        lateinit var record: TestRecord
        var saveCount = 0
        override fun create(): CommandRecord = TestRecord()
        override fun save(commandRecord: CommandRecord) { record = commandRecord as TestRecord; saveCount += 1 }
    }
    private class TestRecord : CommandRecord {
        override val id = "command-1"
        lateinit var scheduleAt: LocalDateTime
        override fun init(command: Command<*>, serviceName: String, commandType: String, scheduleAt: LocalDateTime, expireAfter: Duration, retryTimes: Int, executionContext: Collection<EncodedExecutionContextElement>) { this.scheduleAt = scheduleAt }
    }
}
