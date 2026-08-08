package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.JpaOwnershipClaim
import com.only4.cap4k.ddd.application.JpaOwnershipToken
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JpaReliableCommandWorkerTest {
    private val now = LocalDateTime.of(2026, 8, 7, 10, 0)
    private val ownership = JpaOwnershipClaim(
        recordId = 7,
        token = JpaOwnershipToken.fromText("a".repeat(JpaOwnershipToken.BYTE_LENGTH)),
        leaseUntil = now.plusMinutes(1),
    )

    @Test
    fun `claimed command is sent synchronously then acknowledged by ownership`() {
        val substrate = substrateWithClaim(TestCommand("work"))
        val supervisor = RecordingCommandSupervisor()
        worker(substrate, supervisor).use { worker ->
            assertEquals(1, worker.processAvailable())
        }

        assertEquals(listOf(TestCommand("work")), supervisor.commands)
        verify(exactly = 1) { substrate.acknowledge(ownership, any()) }
        verify(exactly = 0) { substrate.fail(any(), any(), any()) }
    }

    @Test
    fun `handler failure is transitioned through token fenced failure`() {
        val substrate = substrateWithClaim(TestCommand("fail"))
        val failure = IllegalStateException("business-secret")
        val supervisor = RecordingCommandSupervisor(failure = failure)
        worker(substrate, supervisor).use { worker ->
            assertEquals(1, worker.processAvailable())
        }

        verify(exactly = 1) { substrate.fail(ownership, any(), failure) }
        verify(exactly = 0) { substrate.acknowledge(any(), any()) }
    }

    @Test
    fun `worker renews ownership while synchronous handler is still running`() {
        val substrate = substrateWithClaim(TestCommand("slow"))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val supervisor = RecordingCommandSupervisor {
            entered.countDown()
            assertTrue(release.await(2, TimeUnit.SECONDS))
        }
        val worker = worker(
            substrate = substrate,
            supervisor = supervisor,
            leaseDuration = Duration.ofSeconds(1),
            renewInterval = Duration.ofMillis(10),
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<Int> { worker.processAvailable() }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            verify(timeout = 1_000, atLeast = 1) { substrate.renew(ownership, any(), Duration.ofSeconds(1)) }
            release.countDown()
            assertEquals(1, result.get(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            worker.close()
        }
    }

    @Test
    fun `no claim completes without invoking a handler`() {
        val substrate = mockk<JpaCommandExecutionSubstrate>()
        every { substrate.claim(any(), any(), any(), any()) } returns null
        val supervisor = RecordingCommandSupervisor()

        worker(substrate, supervisor).use { worker ->
            assertEquals(0, worker.processAvailable())
        }

        assertTrue(supervisor.commands.isEmpty())
    }

    @Test
    fun `future schedule notification prompts a scan without creating a future timer`() {
        val substrate = mockk<JpaCommandExecutionSubstrate>()
        every { substrate.claim(any(), any(), any(), any()) } returns null
        val worker = worker(substrate, RecordingCommandSupervisor())
        try {
            worker.wakeUp(now.plusYears(10))
            verify(timeout = 1_000, exactly = 1) { substrate.claim(any(), any(), any(), any()) }
        } finally {
            worker.close()
        }
    }

    @Test
    fun `shutdown during a batch does not claim or fail another command`() {
        val substrate = mockk<JpaCommandExecutionSubstrate>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { substrate.claim(any(), any(), any(), any()) } returns ownership andThen
            JpaOwnershipClaim(
                recordId = 8,
                token = JpaOwnershipToken.fromText("b".repeat(JpaOwnershipToken.BYTE_LENGTH)),
                leaseUntil = now.plusMinutes(1),
            )
        every { substrate.load(ownership, any()) } returns JpaClaimedCommand(TestCommand("first"), emptyList())
        every { substrate.renew(ownership, any(), any()) } returns true
        every { substrate.acknowledge(ownership, any()) } returns true
        every { substrate.fail(any(), any(), any()) } returns true
        val worker = worker(
            substrate,
            RecordingCommandSupervisor {
                entered.countDown()
                assertTrue(release.await(2, TimeUnit.SECONDS))
            },
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<Int> { worker.processAvailable() }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            worker.close()
            release.countDown()
            assertEquals(1, result.get(2, TimeUnit.SECONDS))
            verify(exactly = 1) { substrate.claim(any(), any(), any(), any()) }
            verify(exactly = 0) { substrate.fail(any(), any(), any()) }
        } finally {
            release.countDown()
            executor.shutdownNow()
            worker.close()
        }
    }

    private fun substrateWithClaim(command: Command<*>): JpaCommandExecutionSubstrate =
        mockk<JpaCommandExecutionSubstrate>().also { substrate ->
            every { substrate.claim(any(), any(), any(), any()) } returns ownership andThen null
            every { substrate.load(ownership, any()) } returns JpaClaimedCommand(command, emptyList())
            every { substrate.acknowledge(ownership, any()) } returns true
            every { substrate.fail(ownership, any(), any()) } returns true
            every { substrate.renew(ownership, any(), any()) } returns true
        }

    private fun worker(
        substrate: JpaCommandExecutionSubstrate,
        supervisor: CommandSupervisor,
        leaseDuration: Duration = Duration.ofMinutes(1),
        renewInterval: Duration = Duration.ofSeconds(10),
    ): JpaReliableCommandWorker {
        val contextManager = DefaultExecutionContextManager()
        return JpaReliableCommandWorker(
            substrate = substrate,
            commandSupervisor = supervisor,
            executionContextScopeManager = contextManager,
            executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
            serviceName = "command-service",
            workerCount = 1,
            batchSize = 4,
            pollInterval = Duration.ofMinutes(1),
            leaseDuration = leaseDuration,
            renewInterval = renewInterval,
            threadFactoryClassName = "",
            clock = { now },
        )
    }

    private data class TestCommand(val value: String) : Command<Unit>

    private class RecordingCommandSupervisor(
        private val failure: Throwable? = null,
        private val beforeComplete: () -> Unit = {},
    ) : CommandSupervisor {
        val commands = mutableListOf<Command<*>>()

        @Suppress("UNCHECKED_CAST")
        override fun <COMMAND : Command<RESULT>, RESULT : Any> send(command: COMMAND): RESULT {
            commands += command
            beforeComplete()
            failure?.let { throw it }
            return Unit as RESULT
        }
    }
}
