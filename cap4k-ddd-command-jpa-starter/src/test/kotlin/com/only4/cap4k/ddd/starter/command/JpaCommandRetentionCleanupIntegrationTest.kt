package com.only4.cap4k.ddd.starter.command

import com.only4.cap4k.ddd.application.command.JpaCommandExecutionSubstrate
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.reliable.ReliableRetentionPolicy
import com.only4.cap4k.ddd.core.share.retry.RetryDelayStep
import com.only4.cap4k.ddd.core.share.retry.RetryableClassification
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:command-retention;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
    ],
)
@Import(JpaCommandRetentionCleanupIntegrationTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaCommandRetentionCleanupIntegrationTest {
    @jakarta.annotation.Resource
    lateinit var records: CommandRecordJpaRepository

    @jakarta.annotation.Resource
    lateinit var substrate: JpaCommandExecutionSubstrate

    @jakarta.annotation.Resource
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun reset() = records.deleteAll()

    @Test
    fun `successful acknowledgement gets terminal time and is removed after cutoff`() {
        val now = testTime()
        val record = records.saveAndFlush(command(now))
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        assertTrue(substrate.acknowledge(ownership, now))
        assertEquals(now, records.findById(record.id!!).orElseThrow().terminalizedAt)

        val result = substrate.cleanup(
            SERVICE,
            now.plusSeconds(2),
            ReliableRetentionPolicy(
                successful = Duration.ofSeconds(1),
                exhausted = Duration.ofHours(1),
                expired = Duration.ofHours(1),
                batchLimit = 10,
            ),
        )

        assertEquals(1, result.examined)
        assertEquals(1, result.deleted)
        assertFalse(records.existsById(record.id!!))
    }

    @Test
    fun `expiry and exhaustion transitions persist terminal time`() {
        val now = testTime()
        val expired = records.saveAndFlush(command(now).apply { expireAt = now.minusSeconds(1) })
        assertNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))
        assertEquals(now, records.findById(expired.id!!).orElseThrow().terminalizedAt)

        val exhausted = records.saveAndFlush(
            command(now).apply {
                retryPolicy = RuntimeJson.write(zeroDelayPolicy(1))
                tryTimes = 1
            },
        )
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))
        assertTrue(substrate.fail(ownership, now, IllegalStateException("failure")))
        assertEquals(
            now,
            records.findById(exhausted.id!!).orElseThrow().terminalizedAt,
        )
    }

    @Test
    fun `cleanup is bounded and protects retryable cancelled leased and other-service records`() {
        val now = testTime()
        val old = now.minusHours(2)
        val recordsToInsert = listOf(
            command(now, state = CommandRecordEntity.CommandState.EXECUTED).apply { terminalizedAt = old },
            command(now, state = CommandRecordEntity.CommandState.EXHAUSTED).apply { terminalizedAt = old },
            command(now, state = CommandRecordEntity.CommandState.EXPIRED).apply { terminalizedAt = old },
            command(now, state = CommandRecordEntity.CommandState.EXCEPTION).apply { terminalizedAt = old },
            command(now, state = CommandRecordEntity.CommandState.CANCEL).apply { terminalizedAt = old },
            command(now, state = CommandRecordEntity.CommandState.EXECUTED).apply {
                terminalizedAt = old
                leaseUntil = now.plusMinutes(5)
            },
            command("other-service", now, CommandRecordEntity.CommandState.EXECUTED).apply { terminalizedAt = old },
            command(now, state = CommandRecordEntity.CommandState.EXECUTED).apply { terminalizedAt = now },
        )
        records.saveAllAndFlush(recordsToInsert)

        val policy = ReliableRetentionPolicy(
            successful = Duration.ofHours(1),
            exhausted = Duration.ofHours(1),
            expired = Duration.ofHours(1),
            batchLimit = 2,
        )
        val first = substrate.cleanup(SERVICE, now, policy)
        assertEquals(2, first.examined)
        assertEquals(2, first.deleted)

        val second = substrate.cleanup(SERVICE, now, policy)
        assertEquals(1, second.examined)
        assertEquals(1, second.deleted)

        val remaining = records.findAll()
        assertEquals(5, remaining.size)
        assertTrue(remaining.any { it.commandState == CommandRecordEntity.CommandState.EXCEPTION })
        assertTrue(remaining.any { it.commandState == CommandRecordEntity.CommandState.CANCEL })
        assertTrue(remaining.any { it.leaseUntil != null })
        assertTrue(remaining.any { it.svcName == "other-service" })
        assertTrue(remaining.any { it.terminalizedAt == now })
    }

    @Test
    fun `final delete rechecks state after candidate selection`() {
        val now = testTime()
        val record = records.saveAndFlush(
            command(now, state = CommandRecordEntity.CommandState.EXECUTED).apply {
                terminalizedAt = now.minusHours(2)
            },
        )
        val ids = records.findRetentionCandidateIds(
            serviceName = SERVICE,
            successState = CommandRecordEntity.CommandState.EXECUTED,
            exhaustedState = CommandRecordEntity.CommandState.EXHAUSTED,
            expiredState = CommandRecordEntity.CommandState.EXPIRED,
            successfulCutoff = now.minusHours(1),
            exhaustedCutoff = now.minusHours(1),
            expiredCutoff = now.minusHours(1),
            pageable = PageRequest.of(0, 10),
        )
        assertEquals(listOf(record.id!!), ids)

        record.commandState = CommandRecordEntity.CommandState.EXCEPTION
        record.terminalizedAt = null
        records.saveAndFlush(record)

        assertEquals(0, inTransaction {
            records.deleteRetentionCandidates(
                recordIds = ids,
                serviceName = SERVICE,
                successState = CommandRecordEntity.CommandState.EXECUTED,
                exhaustedState = CommandRecordEntity.CommandState.EXHAUSTED,
                expiredState = CommandRecordEntity.CommandState.EXPIRED,
                successfulCutoff = now.minusHours(1),
                exhaustedCutoff = now.minusHours(1),
                expiredCutoff = now.minusHours(1),
            )
        })
        assertTrue(records.existsById(record.id!!))
        assertNull(records.findById(record.id!!).orElseThrow().terminalizedAt)
    }

    private fun command(
        now: LocalDateTime,
        state: CommandRecordEntity.CommandState = CommandRecordEntity.CommandState.INIT,
    ): CommandRecordEntity = command(SERVICE, now, state)

    private fun command(
        serviceName: String,
        now: LocalDateTime,
        state: CommandRecordEntity.CommandState,
    ): CommandRecordEntity = CommandRecordEntity(
        commandUuid = "command-${System.nanoTime()}",
        svcName = serviceName,
        commandType = "retention-command",
        param = "{}",
        paramType = TestCommand::class.java.name,
        retryPolicy = RuntimeJson.write(ReliableRetryPolicySnapshot.capture(null, 3)),
        createAt = now.minusMinutes(1),
        expireAt = now.plusHours(1),
        commandState = state,
        lastTryTime = now.minusMinutes(1),
        nextTryTime = now.minusSeconds(1),
        triedTimes = 0,
        tryTimes = 3,
    )

    private data class TestCommand(val value: String) : Command<Unit>

    private fun zeroDelayPolicy(retryLimit: Int): ReliableRetryPolicySnapshot = ReliableRetryPolicySnapshot(
        policyVersion = 1,
        retryLimit = retryLimit,
        retryableClassification = RetryableClassification.ANY_EXCEPTION,
        delaySteps = listOf(RetryDelayStep(throughAttempt = null, delayMinutes = 0)),
    )

    private fun inTransaction(block: () -> Int): Int = requireNotNull(
        TransactionTemplate(transactionManager).execute { block() },
    )

    private fun testTime(): LocalDateTime = LocalDateTime.of(2026, 8, 7, 10, 0, 0)

    @SpringBootApplication
    @EntityScan(basePackageClasses = [CommandRecordEntity::class])
    @EnableJpaRepositories(basePackageClasses = [CommandRecordJpaRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun substrate(records: CommandRecordJpaRepository): JpaCommandExecutionSubstrate =
            JpaCommandExecutionSubstrate(records)
    }

    private companion object {
        const val SERVICE = "command-retention-service"
    }
}
