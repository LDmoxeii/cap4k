package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.application.JpaRedriveResult
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.reliable.ReliableRetentionPolicy
import com.only4.cap4k.ddd.core.share.retry.RetryDelayStep
import com.only4.cap4k.ddd.core.share.retry.RetryableClassification
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import com.only4.cap4k.ddd.domain.event.JpaEventExecutionSubstrate
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
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
        "spring.datasource.url=jdbc:h2:mem:event-retention;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
    ],
)
@Import(JpaEventRetentionCleanupIntegrationTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaEventRetentionCleanupIntegrationTest {
    @jakarta.annotation.Resource
    lateinit var records: EventJpaRepository

    @jakarta.annotation.Resource
    lateinit var substrate: JpaEventExecutionSubstrate

    @jakarta.annotation.Resource
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun reset() = records.deleteAll()

    @Test
    fun `successful acknowledgement gets terminal time and is removed after cutoff`() {
        val now = testTime()
        val record = records.saveAndFlush(event(now))
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
        val expired = records.saveAndFlush(event(now).apply { expireAt = now.minusSeconds(1) })
        assertNull(substrate.claim(SERVICE, now, Duration.ofSeconds(30)))
        assertEquals(now, records.findById(expired.id!!).orElseThrow().terminalizedAt)

        val exhausted = records.saveAndFlush(
            event(now).apply {
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
            event(now, state = Event.EventState.DELIVERED).apply { terminalizedAt = old },
            event(now, state = Event.EventState.EXHAUSTED).apply {
                terminalizedAt = old
                expireAt = now.minusSeconds(1)
            },
            event(now, state = Event.EventState.EXPIRED).apply { terminalizedAt = old },
            event(now, state = Event.EventState.EXCEPTION).apply { terminalizedAt = old },
            event(now, state = Event.EventState.CANCEL).apply { terminalizedAt = old },
            event(now, state = Event.EventState.DELIVERED).apply {
                terminalizedAt = old
                leaseUntil = now.plusMinutes(5)
            },
            event("other-service", now, Event.EventState.DELIVERED).apply { terminalizedAt = old },
            event(now, state = Event.EventState.DELIVERED).apply { terminalizedAt = now },
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
        assertTrue(remaining.any { it.eventState == Event.EventState.EXCEPTION })
        assertTrue(remaining.any { it.eventState == Event.EventState.CANCEL })
        assertTrue(remaining.any { it.leaseUntil != null })
        assertTrue(remaining.any { it.svcName == "other-service" })
        assertTrue(remaining.any { it.terminalizedAt == now })
    }

    @Test
    fun `final delete rechecks state after candidate selection`() {
        val now = testTime()
        val record = records.saveAndFlush(
            event(now, state = Event.EventState.DELIVERED).apply {
                terminalizedAt = now.minusHours(2)
            },
        )
        val ids = records.findRetentionCandidateIds(
            serviceName = SERVICE,
            successState = Event.EventState.DELIVERED,
            exhaustedState = Event.EventState.EXHAUSTED,
            expiredState = Event.EventState.EXPIRED,
            successfulCutoff = now.minusHours(1),
            exhaustedCutoff = now.minusHours(1),
            expiredCutoff = now.minusHours(1),
            now = now,
            pageable = PageRequest.of(0, 10),
        )
        assertEquals(listOf(record.id!!), ids)

        record.eventState = Event.EventState.EXCEPTION
        record.terminalizedAt = null
        records.saveAndFlush(record)

        assertEquals(0, inTransaction {
            records.deleteRetentionCandidates(
                recordIds = ids,
                serviceName = SERVICE,
                successState = Event.EventState.DELIVERED,
                exhaustedState = Event.EventState.EXHAUSTED,
                expiredState = Event.EventState.EXPIRED,
                successfulCutoff = now.minusHours(1),
                exhaustedCutoff = now.minusHours(1),
                expiredCutoff = now.minusHours(1),
                now = now,
            )
        })
        assertTrue(records.existsById(record.id!!))
        assertNull(records.findById(record.id!!).orElseThrow().terminalizedAt)
    }

    @Test
    fun `future exhausted is retained and can be redriven`() {
        val now = testTime()
        val record = records.saveAndFlush(
            event(now, state = Event.EventState.EXHAUSTED).apply {
                terminalizedAt = now.minusHours(2)
                expireAt = now.plusHours(1)
            },
        )
        val policy = ReliableRetentionPolicy(
            successful = Duration.ofHours(1),
            exhausted = Duration.ofHours(1),
            expired = Duration.ofHours(1),
            batchLimit = 10,
        )

        val cleanup = substrate.cleanup(SERVICE, now, policy)
        assertEquals(0, cleanup.examined)
        assertEquals(0, cleanup.deleted)
        val retained = records.findById(record.id!!).orElseThrow()
        assertEquals(Event.EventState.EXHAUSTED, retained.eventState)
        assertEquals(
            JpaRedriveResult.REDRIVEN,
            substrate.redrive(record.id!!, SERVICE, retained.version, retained.eventState, "future-exhausted", now),
        )
        val redriven = records.findById(record.id!!).orElseThrow()
        assertEquals(Event.EventState.INIT, redriven.eventState)
        assertNull(redriven.terminalizedAt)
        assertEquals("future-exhausted", redriven.redriveRequestToken)
    }

    @Test
    fun `expired exhausted is removable after its retention cutoff`() {
        val now = testTime()
        val record = records.saveAndFlush(
            event(now, state = Event.EventState.EXHAUSTED).apply {
                terminalizedAt = now.minusHours(2)
                expireAt = now.minusSeconds(1)
            },
        )

        val result = substrate.cleanup(
            SERVICE,
            now,
            ReliableRetentionPolicy(
                successful = Duration.ofHours(1),
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
    fun `stale cleanup candidate cannot delete after redrive wins`() {
        val now = testTime()
        val record = records.saveAndFlush(
            event(now, state = Event.EventState.DELIVERED).apply {
                terminalizedAt = now.minusHours(2)
            },
        )
        val candidateIds = records.findRetentionCandidateIds(
            serviceName = SERVICE,
            successState = Event.EventState.DELIVERED,
            exhaustedState = Event.EventState.EXHAUSTED,
            expiredState = Event.EventState.EXPIRED,
            successfulCutoff = now.minusHours(1),
            exhaustedCutoff = now.minusHours(1),
            expiredCutoff = now.minusHours(1),
            now = now,
            pageable = PageRequest.of(0, 10),
        )
        assertEquals(listOf(record.id!!), candidateIds)

        val exhausted = records.findById(record.id!!).orElseThrow().apply {
            eventState = Event.EventState.EXHAUSTED
            expireAt = now.plusHours(1)
            terminalizedAt = now.minusHours(2)
        }
        records.saveAndFlush(exhausted)
        val redriveVersion = records.findById(record.id!!).orElseThrow().version
        assertEquals(
            JpaRedriveResult.REDRIVEN,
            substrate.redrive(record.id!!, SERVICE, redriveVersion, Event.EventState.EXHAUSTED, "race", now),
        )

        assertEquals(0, inTransaction {
            records.deleteRetentionCandidates(
                recordIds = candidateIds,
                serviceName = SERVICE,
                successState = Event.EventState.DELIVERED,
                exhaustedState = Event.EventState.EXHAUSTED,
                expiredState = Event.EventState.EXPIRED,
                successfulCutoff = now.minusHours(1),
                exhaustedCutoff = now.minusHours(1),
                expiredCutoff = now.minusHours(1),
                now = now,
            )
        })
        val retained = records.findById(record.id!!).orElseThrow()
        assertEquals(Event.EventState.INIT, retained.eventState)
        assertEquals("race", retained.redriveRequestToken)
    }

    private fun event(
        now: LocalDateTime,
        state: Event.EventState = Event.EventState.INIT,
    ): Event = event(SERVICE, now, state)

    private fun event(
        serviceName: String,
        now: LocalDateTime,
        state: Event.EventState,
    ): Event = Event(
        eventUuid = "event-${System.nanoTime()}",
        svcName = serviceName,
        eventType = "retention-event",
        data = "{}",
        dataType = TestEvent::class.java.name,
        retryPolicy = RuntimeJson.write(ReliableRetryPolicySnapshot.capture(null, 3)),
        createAt = now.minusMinutes(1),
        publishedAt = now.minusMinutes(1),
        expireAt = now.plusHours(1),
        eventState = state,
        lastTryTime = now.minusMinutes(1),
        nextTryTime = now.minusSeconds(1),
        triedTimes = 0,
        tryTimes = 3,
    )

    @DomainEvent("retention-event")
    private data class TestEvent(val value: String)

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
    @EntityScan(basePackageClasses = [Event::class])
    @EnableJpaRepositories(basePackageClasses = [EventJpaRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun substrate(records: EventJpaRepository): JpaEventExecutionSubstrate =
            JpaEventExecutionSubstrate(records)
    }

    private companion object {
        const val SERVICE = "event-retention-service"
    }
}
