package com.only4.cap4k.ddd.starter.command

import com.only4.cap4k.ddd.application.command.JpaCommandExecutionSubstrate
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import com.only4.cap4k.ddd.core.share.retry.RetryDelayStep
import com.only4.cap4k.ddd.core.share.retry.RetryableClassification
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import jakarta.persistence.Column
import org.springframework.beans.factory.annotation.Autowired
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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:command-atomic-claim;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ],
)
@Import(JpaCommandAtomicClaimIntegrationTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaCommandAtomicClaimIntegrationTest {
    @Autowired
    lateinit var records: CommandRecordJpaRepository

    @Autowired
    lateinit var substrate: JpaCommandExecutionSubstrate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    private val executors = mutableListOf<ExecutorService>()

    @BeforeEach
    fun reset() = records.deleteAll()

    @AfterEach
    fun stopExecutors() = executors.forEach(ExecutorService::shutdownNow)

    @Test
    fun `two transactions that observed the same candidate produce exactly one durable owner`() {
        val now = testTime()
        val record = records.saveAndFlush(command(now))
        val candidatesRead = CountDownLatch(2)
        val startCas = CountDownLatch(1)
        val observedIds = Collections.synchronizedList(mutableListOf<Long>())
        val tokens = listOf("a".repeat(32), "b".repeat(32))
        val executor = Executors.newFixedThreadPool(2).also(executors::add)

        val futures = tokens.map { token ->
            executor.submit<Int> {
                requireNotNull(TransactionTemplate(transactionManager).execute {
                    val candidate = records.findClaimCandidates(
                        serviceName = SERVICE,
                        now = now,
                        readyStates = setOf(
                            CommandRecordEntity.CommandState.INIT,
                            CommandRecordEntity.CommandState.EXCEPTION,
                        ),
                        ownedState = CommandRecordEntity.CommandState.EXECUTING,
                        pageable = PageRequest.of(0, 32),
                    ).single()
                    observedIds += requireNotNull(candidate.id)
                    candidatesRead.countDown()
                    check(startCas.await(5, TimeUnit.SECONDS))
                    records.claim(
                        recordId = record.id!!,
                        serviceName = SERVICE,
                        readyStates = setOf(
                            CommandRecordEntity.CommandState.INIT,
                            CommandRecordEntity.CommandState.EXCEPTION,
                        ),
                        ownedState = CommandRecordEntity.CommandState.EXECUTING,
                        now = now,
                        nextTryTime = now.plusMinutes(1),
                        token = token,
                        leaseUntil = now.plusSeconds(30),
                        retryLimit = 3,
                    )
                })
            }
        }
        assertTrue(candidatesRead.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(record.id!!, record.id!!), observedIds.sorted())
        startCas.countDown()

        assertEquals(1, futures.sumOf { it.get(10, TimeUnit.SECONDS) })

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTING, stored.commandState)
        assertTrue(stored.deliveryToken in tokens)
        assertEquals(now.plusSeconds(30), stored.leaseUntil)
        assertEquals(1, stored.triedTimes)
    }

    @Test
    fun `token mismatch cannot renew acknowledge or fail`() {
        val now = testTime()
        val record = records.saveAndFlush(command(now))
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))
        val mismatch = ownership.copy(token = "0".repeat(32))

        assertFalse(substrate.renew(mismatch, now.plusSeconds(1), Duration.ofSeconds(30)))
        assertFalse(substrate.acknowledge(mismatch, now.plusSeconds(1)))
        assertFalse(substrate.fail(mismatch, now.plusSeconds(1), IllegalStateException("business-secret")))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTING, stored.commandState)
        assertEquals(ownership.token, stored.deliveryToken)
        assertEquals(ownership.leaseUntil, stored.leaseUntil)
        assertNull(stored.failureFactsJson)
    }

    @Test
    fun `lease renews before expiry and lost worker is replaced only after expiry`() {
        val now = testTime()
        records.saveAndFlush(command(now))
        val first = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(5)))

        assertTrue(substrate.renew(first, now.plusSeconds(1), Duration.ofSeconds(10)))
        assertNull(substrate.claim(SERVICE, now.plusSeconds(10), Duration.ofSeconds(5)))
        assertFalse(substrate.renew(first, now.plusSeconds(12), Duration.ofSeconds(10)))

        val replacement = requireNotNull(substrate.claim(SERVICE, now.plusSeconds(12), Duration.ofSeconds(5)))
        assertNotEquals(first.token, replacement.token)
        assertEquals(first.recordId, replacement.recordId)
    }

    @Test
    fun `renew never shortens an active lease`() {
        val now = testTime()
        records.saveAndFlush(command(now))
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        assertFalse(substrate.renew(ownership, now.plusSeconds(1), Duration.ofSeconds(1)))

        val stored = records.findById(ownership.recordId).orElseThrow()
        assertEquals(ownership.leaseUntil, stored.leaseUntil)
        assertEquals(ownership.token, stored.deliveryToken)
    }

    @Test
    fun `correct token cannot transition after its lease expires`() {
        val now = testTime()
        records.saveAndFlush(command(now))
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(5)))
        val before = records.findById(ownership.recordId).orElseThrow()
        val expiredLeaseTime = now.plusSeconds(6)

        assertFalse(substrate.renew(ownership, expiredLeaseTime, Duration.ofSeconds(30)))
        assertFalse(substrate.acknowledge(ownership, expiredLeaseTime))
        assertFalse(substrate.fail(ownership, expiredLeaseTime, IllegalStateException("business-secret")))

        val stored = records.findById(ownership.recordId).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTING, stored.commandState)
        assertEquals(ownership.token, stored.deliveryToken)
        assertEquals(ownership.leaseUntil, stored.leaseUntil)
        assertEquals(before.version, stored.version)
        assertEquals(before.triedTimes, stored.triedTimes)
        assertEquals(before.nextTryTime, stored.nextTryTime)
        assertNull(stored.failureFactsJson)
    }

    @Test
    fun `live owner can renew and acknowledge after record expiry`() {
        val now = testTime()
        val record = records.saveAndFlush(
            command(now).apply { expireAt = now.plusSeconds(5) }
        )
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        assertTrue(substrate.renew(ownership, now.plusSeconds(6), Duration.ofSeconds(30)))
        assertTrue(substrate.acknowledge(ownership, now.plusSeconds(7)))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTED, stored.commandState)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    @Test
    fun `lost owner is terminalized after record and lease expiry`() {
        val now = testTime()
        val record = records.saveAndFlush(
            command(now).apply { expireAt = now.plusSeconds(5) }
        )
        requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(3)))

        assertNull(substrate.claim(SERVICE, now.plusSeconds(6), Duration.ofSeconds(30)))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXPIRED, stored.commandState)
        assertEquals(1, stored.triedTimes)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    @Test
    fun `expired owner token cannot transition after replacement claim`() {
        val now = testTime()
        records.saveAndFlush(command(now))
        val first = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(5)))
        val expiredAt = now.plusSeconds(6)
        val replacement = requireNotNull(substrate.claim(SERVICE, expiredAt, Duration.ofSeconds(5)))

        assertNotEquals(first.token, replacement.token)
        assertFalse(substrate.renew(first, expiredAt, Duration.ofSeconds(30)))
        assertFalse(substrate.acknowledge(first, expiredAt))
        assertFalse(substrate.fail(first, expiredAt, IllegalStateException("business-secret")))

        val stored = records.findById(first.recordId).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTING, stored.commandState)
        assertEquals(replacement.token, stored.deliveryToken)
        assertEquals(replacement.leaseUntil, stored.leaseUntil)
        assertNull(stored.failureFactsJson)
    }

    @Test
    fun `terminal and cancelled records cannot be claimed`() {
        val now = testTime()
        val rejectedStates = listOf(
            CommandRecordEntity.CommandState.CANCEL,
            CommandRecordEntity.CommandState.EXPIRED,
            CommandRecordEntity.CommandState.EXHAUSTED,
            CommandRecordEntity.CommandState.EXECUTED,
        )
        rejectedStates.forEachIndexed { index, state ->
            val record = records.saveAndFlush(command(now, serviceName = "$SERVICE-$index", state = state))
            assertNull(substrate.claim("$SERVICE-$index", now, Duration.ofSeconds(5)))
            val stored = records.findById(record.id!!).orElseThrow()
            assertEquals(state, stored.commandState)
            assertEquals(0, stored.triedTimes)
            assertNull(stored.deliveryToken)
            assertNull(stored.leaseUntil)
        }
    }

    @Test
    fun `command SQL contains every mapped JPA column`() {
        val sql = requireNotNull(javaClass.getResource("/command.sql")).readText()
        CommandRecordEntity::class.java.declaredFields
            .mapNotNull { it.getAnnotation(Column::class.java)?.name?.trim('`') }
            .forEach { column ->
                assertTrue(Regex("(?i)\\b${Regex.escape(column)}\\b").containsMatchIn(sql), column)
            }
        assertTrue(
            Regex("(?i)`delivery_token`\\s+varchar\\(64\\)\\s+character set ascii collate ascii_bin")
                .containsMatchIn(sql),
            "delivery_token must use a case-sensitive binary collation",
        )
        listOf(
            "expire_at",
            "create_at",
            "last_try_time",
            "next_try_time",
            "lease_until",
            "db_created_at",
            "db_updated_at",
        ).forEach { column ->
            assertTrue(
                Regex("(?i)`${Regex.escape(column)}`\\s+datetime\\(3\\)").containsMatchIn(sql),
                "$column must retain millisecond precision",
            )
        }
    }

    @Test
    fun `acknowledgement is token and lease bound and terminal`() {
        val now = testTime()
        val record = records.saveAndFlush(command(now))
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(5)))

        assertTrue(substrate.acknowledge(ownership, now.plusSeconds(1)))
        assertFalse(substrate.acknowledge(ownership, now.plusSeconds(2)))
        assertNull(substrate.claim(SERVICE, now.plusSeconds(10), Duration.ofSeconds(5)))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTED, stored.commandState)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    @Test
    fun `failure uses persisted retry snapshot and stores only safe facts`() {
        val now = testTime()
        val snapshot = ReliableRetryPolicySnapshot(
            policyVersion = 1,
            retryLimit = 3,
            retryableClassification = RetryableClassification.ANY_EXCEPTION,
            delaySteps = listOf(RetryDelayStep(throughAttempt = null, delayMinutes = 7)),
        )
        val record = records.saveAndFlush(
            command(now).apply {
                retryPolicy = RuntimeJson.write(snapshot)
                tryTimes = 1
            }
        )
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        assertTrue(substrate.fail(ownership, now.plusSeconds(1), IllegalStateException("business-secret")))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXCEPTION, stored.commandState)
        assertEquals(now.plusSeconds(1).plusMinutes(7), stored.nextTryTime)
        assertNotNull(stored.failureFactsJson)
        assertFalse(stored.failureFactsJson!!.contains("business-secret"))
        assertFalse(stored.failureFactsJson!!.contains("stackTrace"))
        assertTrue(stored.failureFacts!!.retryable)
        assertEquals(1, stored.failureFacts!!.attempt)
    }

    @Test
    fun `failure becomes terminal when persisted retry budget is exhausted`() {
        val now = testTime()
        val firstAttemptAt = now.minusMinutes(1)
        val record = CommandRecordEntity().init(
            commandParam = TestCommand("first"),
            svcName = SERVICE,
            commandType = "test-command",
            scheduleAt = firstAttemptAt,
            expireAfter = Duration.ofHours(1),
            retryTimes = 1,
        ).apply {
            retryPolicy = RuntimeJson.write(zeroDelayPolicy(1))
            tryTimes = 1
        }
        assertTrue(record.beginCommand(firstAttemptAt))
        record.occurredException(firstAttemptAt, IllegalStateException("business-secret"))
        records.saveAndFlush(record)

        assertNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXHAUSTED, stored.commandState)
        assertTrue(stored.failureFacts!!.terminal)
        assertFalse(stored.failureFacts!!.retryable)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    @Test
    fun `expired retryable facts are terminalized during claim cleanup`() {
        val now = testTime()
        val attemptAt = now.minusMinutes(1)
        val record = command(now).apply {
            assertTrue(beginCommand(attemptAt))
            occurredException(attemptAt, IllegalStateException("business-secret"))
            expireAt = now.minusSeconds(1)
            nextTryTime = now.minusSeconds(1)
        }
        records.saveAndFlush(record)

        assertNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXPIRED, stored.commandState)
        assertTrue(stored.failureFacts!!.terminal)
        assertFalse(stored.failureFacts!!.retryable)
        assertFalse(stored.failureFactsJson!!.contains("business-secret"))
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    @Test
    fun `exhausted retryable facts are terminalized during claim cleanup`() {
        val now = testTime()
        val attemptAt = now.minusMinutes(1)
        val record = command(now).apply {
            retryPolicy = RuntimeJson.write(zeroDelayPolicy(3))
            tryTimes = 3
            assertTrue(beginCommand(attemptAt))
            occurredException(attemptAt, IllegalStateException("business-secret"))
            triedTimes = 3
            nextTryTime = now.minusSeconds(1)
        }
        records.saveAndFlush(record)

        assertNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXHAUSTED, stored.commandState)
        assertTrue(stored.failureFacts!!.terminal)
        assertFalse(stored.failureFacts!!.retryable)
        assertFalse(stored.failureFactsJson!!.contains("business-secret"))
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    @Test
    fun `production command path executes exactly the persisted retry budget`() {
        val now = testTime()
        val firstAttemptAt = now.minusMinutes(1)
        val record = CommandRecordEntity().init(
            commandParam = TestCommand("first"),
            svcName = SERVICE,
            commandType = "test-command",
            scheduleAt = firstAttemptAt,
            expireAfter = Duration.ofHours(1),
            retryTimes = 3,
        ).apply {
            retryPolicy = RuntimeJson.write(zeroDelayPolicy(3))
            tryTimes = 3
        }
        assertTrue(record.beginCommand(firstAttemptAt))
        record.occurredException(firstAttemptAt, IllegalStateException("first-attempt"))
        records.saveAndFlush(record)
        assertEquals(1, record.triedTimes)

        val second = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))
        assertEquals(2, records.findById(record.id!!).orElseThrow().triedTimes)
        assertTrue(substrate.fail(second, now.plusSeconds(1), IllegalStateException("second-attempt")))

        val third = requireNotNull(substrate.claim(SERVICE, now.plusSeconds(1), Duration.ofSeconds(30)))
        assertEquals(3, records.findById(record.id!!).orElseThrow().triedTimes)
        assertTrue(substrate.fail(third, now.plusSeconds(2), IllegalStateException("third-attempt")))
        assertNull(substrate.claim(SERVICE, now.plusSeconds(3), Duration.ofSeconds(30)))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXHAUSTED, stored.commandState)
        assertEquals(3, stored.triedTimes)
        assertTrue(stored.failureFacts!!.terminal)
        assertFalse(stored.failureFacts!!.retryable)
    }

    @Test
    fun `failure becomes expired when the record expires before the owner fails`() {
        val now = testTime()
        val record = records.saveAndFlush(
            command(now).apply {
                expireAt = now.plusSeconds(10)
            }
        )
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        assertTrue(substrate.fail(ownership, now.plusSeconds(11), IllegalStateException("business-secret")))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXPIRED, stored.commandState)
        assertTrue(stored.failureFacts!!.terminal)
        assertFalse(stored.failureFacts!!.retryable)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    @Test
    fun `token bound failure transition rolls back atomically`() {
        val now = testTime()
        records.saveAndFlush(command(now))
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                assertEquals(
                    1,
                    records.transitionFailure(
                        recordId = ownership.recordId,
                        token = ownership.token,
                        ownedState = CommandRecordEntity.CommandState.EXECUTING,
                        failureState = CommandRecordEntity.CommandState.EXCEPTION,
                        failureFacts = "{}",
                        nextTryTime = now.plusMinutes(1),
                        now = now.plusSeconds(1),
                    ),
                )
                throw IllegalStateException("force rollback")
            }
        }

        val stored = records.findById(ownership.recordId).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTING, stored.commandState)
        assertEquals(ownership.token, stored.deliveryToken)
        assertEquals(ownership.leaseUntil, stored.leaseUntil)
        assertNull(stored.failureFactsJson)
    }

    @Test
    fun `claim state token and attempt roll back atomically`() {
        val now = testTime()
        val record = records.saveAndFlush(command(now))
        val token = "a".repeat(32)

        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                assertEquals(
                    1,
                    records.claim(
                        recordId = record.id!!,
                        serviceName = SERVICE,
                        readyStates = setOf(CommandRecordEntity.CommandState.INIT),
                        ownedState = CommandRecordEntity.CommandState.EXECUTING,
                        now = now,
                        nextTryTime = now.plusMinutes(1),
                        token = token,
                        leaseUntil = now.plusSeconds(30),
                        retryLimit = 3,
                    ),
                )
                throw IllegalStateException("force rollback")
            }
        }

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.INIT, stored.commandState)
        assertEquals(0, stored.triedTimes)
        assertEquals(record.version, stored.version)
        assertEquals(record.lastTryTime, stored.lastTryTime)
        assertEquals(record.nextTryTime, stored.nextTryTime)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
    }

    private fun command(
        now: LocalDateTime,
        serviceName: String = SERVICE,
        state: CommandRecordEntity.CommandState = CommandRecordEntity.CommandState.INIT,
    ): CommandRecordEntity = CommandRecordEntity(
        commandUuid = "command-${System.nanoTime()}",
        svcName = serviceName,
        commandType = "test-command",
        retryPolicy = RuntimeJson.write(ReliableRetryPolicySnapshot.capture(null, 3)),
        createAt = now.minusMinutes(1),
        expireAt = now.plusHours(1),
        commandState = state,
        lastTryTime = now.minusMinutes(1),
        nextTryTime = now.minusSeconds(1),
        triedTimes = 0,
        tryTimes = 3,
    )

    private fun zeroDelayPolicy(retryLimit: Int): ReliableRetryPolicySnapshot = ReliableRetryPolicySnapshot(
        policyVersion = 1,
        retryLimit = retryLimit,
        retryableClassification = RetryableClassification.ANY_EXCEPTION,
        delaySteps = listOf(RetryDelayStep(throughAttempt = null, delayMinutes = 0)),
    )

    private data class TestCommand(val value: String) : Command<Unit>

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
        const val SERVICE = "command-service"
    }
}
