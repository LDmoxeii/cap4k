package com.only4.cap4k.ddd.starter.command

import com.only4.cap4k.ddd.application.JpaOwnershipClaim
import com.only4.cap4k.ddd.application.command.JpaCommandExecutionSubstrate
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime
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
    fun `two concurrent claimers produce exactly one durable owner`() {
        val now = testTime()
        val record = records.saveAndFlush(command(now))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2).also(executors::add)

        val futures = List(2) {
            executor.submit<JpaOwnershipClaim?> {
                ready.countDown()
                check(start.await(5, TimeUnit.SECONDS))
                substrate.claim(SERVICE, now, Duration.ofSeconds(30))
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()

        val claims = futures.map { it.get(10, TimeUnit.SECONDS) }.filterNotNull()
        assertEquals(1, claims.size)
        assertTrue(claims.single().token.isNotBlank())

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXECUTING, stored.commandState)
        assertEquals(claims.single().token, stored.deliveryToken)
        assertEquals(claims.single().leaseUntil, stored.leaseUntil)
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
        val snapshot = ReliableRetryPolicySnapshot(
            policyVersion = 1,
            retryLimit = 1,
            retryableClassification = RetryableClassification.ANY_EXCEPTION,
            delaySteps = listOf(RetryDelayStep(throughAttempt = null, delayMinutes = 7)),
        )
        val record = records.saveAndFlush(
            command(now).apply {
                retryPolicy = RuntimeJson.write(snapshot)
                tryTimes = 99
            }
        )
        val ownership = requireNotNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))

        assertTrue(substrate.fail(ownership, now.plusSeconds(1), IllegalStateException("business-secret")))

        val stored = records.findById(record.id!!).orElseThrow()
        assertEquals(CommandRecordEntity.CommandState.EXHAUSTED, stored.commandState)
        assertTrue(stored.failureFacts!!.terminal)
        assertFalse(stored.failureFacts!!.retryable)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
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
