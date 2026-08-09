package com.only4.cap4k.ddd.starter.command

import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.share.annotation.Retry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(classes = [ReliableCommandProductionPathIntegrationTest.TestApplication::class])
@TestPropertySource(
    properties = [
        "spring.application.name=reliable-command-production-path",
        "spring.datasource.url=jdbc:h2:mem:reliable-command-production-path;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "cap4k.ddd.application.command.worker.worker-count=1",
        "cap4k.ddd.application.command.worker.batch-size=4",
        "cap4k.ddd.application.command.worker.poll-interval=PT0.05S",
        "cap4k.ddd.application.command.worker.lease-duration=PT2S",
        "cap4k.ddd.application.command.worker.renew-interval=PT0.2S",
        "logging.level.com.only4.cap4k.ddd=WARN",
        "logging.level.org.hibernate=WARN",
    ],
)
class ReliableCommandProductionPathIntegrationTest {
    @Autowired
    lateinit var records: CommandRecordJpaRepository

    @Autowired
    lateinit var probe: ExecutionProbe

    @BeforeEach
    fun reset() {
        records.deleteAll()
        probe.reset()
    }

    @Test
    fun `committed registration executes through a new synchronous command unit of work and acknowledges`() {
        val callerThread = Thread.currentThread().name
        val value = "committed"
        probe.expect(value)

        val id = Mediator.commands.send(RegisterReliableCommand(value))

        assertTrue(probe.await(value))
        val observation = requireNotNull(probe.observation(value))
        assertFalse(observation.sourceHandlerActive)
        assertTrue(observation.commandUnitOfWorkActive)
        assertNotEquals(callerThread, observation.threadName)
        assertEquals(CommandRecordEntity.CommandState.EXECUTED, awaitState(id))
    }

    @Test
    fun `source transaction rollback leaves no claimable command and never invokes its handler`() {
        val value = "rolled-back"
        probe.expect(value)

        assertThrows<IllegalStateException> {
            Mediator.commands.send(RegisterReliableCommand(value, rollback = true))
        }

        val id = requireNotNull(probe.lastRegisteredId)
        assertNull(records.findAll().singleOrNull { it.commandUuid == id })
        assertFalse(probe.await(value, timeoutMillis = 250))
        assertNull(probe.observation(value))
    }

    @Test
    fun `handler failure is retried from persisted policy and then acknowledged`() {
        val value = "retry-once"
        probe.expect(value)

        val id = Mediator.commands.send(RegisterReliableCommand(value))

        assertTrue(probe.await(value))
        val record = awaitRecord(id, CommandRecordEntity.CommandState.EXECUTED)
        assertEquals(2, record.triedTimes)
        assertEquals(2, probe.attempts(value))
        assertTrue(requireNotNull(record.failureFacts).retryable)
        assertFalse(requireNotNull(record.failureFactsJson).contains("business-secret"))
    }

    private fun awaitState(commandUuid: String): CommandRecordEntity.CommandState {
        return awaitRecord(commandUuid, CommandRecordEntity.CommandState.EXECUTED).commandState
    }

    private fun awaitRecord(
        commandUuid: String,
        expectedState: CommandRecordEntity.CommandState,
    ): CommandRecordEntity {
        repeat(100) {
            records.findAll().singleOrNull { it.commandUuid == commandUuid }?.let { record ->
                if (record.commandState == expectedState) return record
            }
            Thread.sleep(20)
        }
        return requireNotNull(records.findAll().singleOrNull { it.commandUuid == commandUuid })
    }

    data class RegisterReliableCommand(
        val value: String,
        val rollback: Boolean = false,
    ) : Command<String>

    @Retry(retryTimes = 2, retryIntervals = [0], expireAfter = 5)
    data class ExecuteReliableCommand(val value: String) : Command<Unit>

    data class ExecutionObservation(
        val threadName: String,
        val sourceHandlerActive: Boolean,
        val commandUnitOfWorkActive: Boolean,
    )

    class ExecutionProbe {
        private val sourceHandlerActive = AtomicBoolean(false)
        private val latches = ConcurrentHashMap<String, CountDownLatch>()
        private val observations = ConcurrentHashMap<String, ExecutionObservation>()
        private val attempts = ConcurrentHashMap<String, Int>()
        @Volatile
        var lastRegisteredId: String? = null

        fun reset() {
            sourceHandlerActive.set(false)
            latches.clear()
            observations.clear()
            attempts.clear()
            lastRegisteredId = null
        }

        fun expect(value: String) {
            latches[value] = CountDownLatch(1)
        }

        fun <T> inSourceHandler(block: () -> T): T {
            sourceHandlerActive.set(true)
            return try {
                block()
            } finally {
                sourceHandlerActive.set(false)
            }
        }

        fun record(value: String, commandUnitOfWorkActive: Boolean) {
            observations[value] = ExecutionObservation(
                threadName = Thread.currentThread().name,
                sourceHandlerActive = sourceHandlerActive.get(),
                commandUnitOfWorkActive = commandUnitOfWorkActive,
            )
            latches[value]?.countDown()
        }

        fun beginAttempt(value: String): Int = attempts.merge(value, 1, Int::plus)!!

        fun attempts(value: String): Int = attempts[value] ?: 0

        fun await(value: String, timeoutMillis: Long = 2_000): Boolean =
            requireNotNull(latches[value]).await(timeoutMillis, TimeUnit.MILLISECONDS)

        fun observation(value: String): ExecutionObservation? = observations[value]
    }

    class RegisterReliableCommandHandler(
        private val probe: ExecutionProbe,
    ) : CommandHandler<RegisterReliableCommand, String> {
        override fun handle(command: RegisterReliableCommand): String = probe.inSourceHandler {
            val id = Mediator.commands.enqueue(ExecuteReliableCommand(command.value))
            probe.lastRegisteredId = id
            if (command.rollback) throw IllegalStateException("force source rollback")
            id
        }
    }

    class ExecuteReliableCommandHandler(
        private val probe: ExecutionProbe,
        private val unitOfWork: CommandUnitOfWorkCoordinator,
    ) : CommandHandler<ExecuteReliableCommand, Unit> {
        override fun handle(command: ExecuteReliableCommand) {
            if (probe.beginAttempt(command.value) == 1 && command.value == "retry-once") {
                throw IllegalStateException("business-secret")
            }
            probe.record(command.value, unitOfWork.active)
        }
    }

    @SpringBootApplication
    class TestApplication {
        @Bean
        fun executionProbe(): ExecutionProbe = ExecutionProbe()

        @Bean
        fun registerReliableCommandHandler(probe: ExecutionProbe): RegisterReliableCommandHandler =
            RegisterReliableCommandHandler(probe)

        @Bean
        fun executeReliableCommandHandler(
            probe: ExecutionProbe,
            unitOfWork: CommandUnitOfWorkCoordinator,
        ): ExecuteReliableCommandHandler = ExecuteReliableCommandHandler(probe, unitOfWork)
    }
}
