package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.application.JpaOwnershipClaim
import com.only4.cap4k.ddd.application.JpaOwnershipToken
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import com.only4.cap4k.ddd.domain.event.persistence.TestEvent
import com.only4.cap4k.ddd.domain.event.persistence.UserCreatedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class JpaEventScheduleServiceTest {
    private val publisher = mockk<EventPublisher>()
    private val substrate = mockk<JpaEventExecutionSubstrate>()
    private val records = mockk<EventJpaRepository>()
    private val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val leaseDuration = Duration.ofSeconds(30)
    private val renewInterval = Duration.ofSeconds(10)
    private lateinit var service: JpaEventScheduleService

    @AfterEach
    fun shutdown() {
        if (::service.isInitialized) service.shutdown()
    }

    @Test
    fun `claimed Domain Event is acknowledged only through completion`() {
        val fixture = claimed(TestEvent("test", 12345))
        service = newService(batchSize = 2)
        every { substrate.claim("test-service", any(), leaseDuration, 2) } returnsMany
            listOf(fixture.ownership, null)
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)
        every { substrate.acknowledge(fixture.ownership, any()) } returns true
        every { publisher.publish(any(), any()) } answers {
            val record = firstArg<EventRecord>()
            secondArg<EventPublisher.Completion>().onSuccess(record)
        }

        assertEquals(1, service.drainNow())

        verify(exactly = 1) { publisher.publish(match { it.deliveryAttempt == 1 }, any()) }
        verify(exactly = 1) { substrate.acknowledge(fixture.ownership, any()) }
        verify(exactly = 0) { substrate.fail(any(), any(), any()) }
    }

    @Test
    fun `Domain Event publisher failure enters token bound failure transition`() {
        val fixture = claimed(TestEvent("test", 12345))
        val failure = IllegalStateException("handler failed")
        service = newService(batchSize = 2)
        every { substrate.claim("test-service", any(), leaseDuration, 2) } returnsMany
            listOf(fixture.ownership, null)
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)
        every { substrate.fail(fixture.ownership, any(), failure) } returns true
        every { publisher.publish(any(), any()) } answers {
            val record = firstArg<EventRecord>()
            secondArg<EventPublisher.Completion>().onFailure(record, failure)
        }

        assertEquals(1, service.drainNow())

        verify(exactly = 1) { substrate.fail(fixture.ownership, any(), failure) }
        verify(exactly = 0) { substrate.acknowledge(any(), any()) }
    }

    @Test
    fun `Domain Event publisher must complete synchronously`() {
        val fixture = claimed(TestEvent("test", 12345))
        service = newService(batchSize = 2)
        every { substrate.claim("test-service", any(), leaseDuration, 2) } returnsMany
            listOf(fixture.ownership, null)
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)
        every { substrate.fail(fixture.ownership, any(), any()) } returns true
        every { publisher.publish(any(), any()) } returns Unit

        assertEquals(1, service.drainNow())

        verify(exactly = 1) {
            substrate.fail(
                fixture.ownership,
                any(),
                match { it is IllegalStateException && it.message!!.contains("synchronous completion") },
            )
        }
    }

    @Test
    fun `Integration Event may complete after publisher returns`() {
        val fixture = claimed(UserCreatedEvent("user-1", "name", "mail@example.com"))
        lateinit var completion: EventPublisher.Completion
        lateinit var published: EventRecord
        service = newService(batchSize = 2)
        every { substrate.claim("test-service", any(), leaseDuration, 2) } returnsMany
            listOf(fixture.ownership, null)
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)
        every { substrate.acknowledge(fixture.ownership, any()) } returns true
        every { publisher.publish(any(), any()) } answers {
            published = firstArg()
            completion = secondArg()
        }

        assertEquals(1, service.drainNow())
        verify(exactly = 0) { substrate.acknowledge(any(), any()) }
        verify(exactly = 0) { substrate.fail(any(), any(), any()) }

        completion.onSuccess(published)

        verify(exactly = 1) { substrate.acknowledge(fixture.ownership, any()) }
    }

    @Test
    fun `Integration Event lease is renewed while provider callback is pending`() {
        val fixture = claimed(UserCreatedEvent("user-1", "name", "mail@example.com"))
        lateinit var completion: EventPublisher.Completion
        lateinit var published: EventRecord
        val shortLease = Duration.ofMillis(100)
        val shortRenewInterval = Duration.ofMillis(10)
        service = newService(batchSize = 2, leaseDuration = shortLease, renewInterval = shortRenewInterval)
        every { substrate.claim("test-service", any(), shortLease, 2) } returnsMany
            listOf(fixture.ownership, null)
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)
        every { substrate.renew(fixture.ownership, any(), shortLease) } returns true
        every { substrate.acknowledge(fixture.ownership, any()) } returns true
        every { publisher.publish(any(), any()) } answers {
            published = firstArg()
            completion = secondArg()
        }

        assertEquals(1, service.drainNow())
        verify(timeout = 500, atLeast = 1) { substrate.renew(fixture.ownership, any(), shortLease) }

        completion.onSuccess(published)
        verify(exactly = 1) { substrate.acknowledge(fixture.ownership, any()) }
    }

    @Test
    fun `completion may retry a transition after a transient database failure`() {
        val fixture = claimed(TestEvent("test", 12345))
        service = newService(batchSize = 2)
        every { substrate.claim("test-service", any(), leaseDuration, 2) } returnsMany
            listOf(fixture.ownership, null)
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)
        every { substrate.acknowledge(fixture.ownership, any()) } returnsMany listOf(false, true)
        every { publisher.publish(any(), any()) } answers {
            val record = firstArg<EventRecord>()
            val completion = secondArg<EventPublisher.Completion>()
            completion.onSuccess(record)
            completion.onSuccess(record)
        }

        assertEquals(1, service.drainNow())

        verify(exactly = 2) { substrate.acknowledge(fixture.ownership, any()) }
    }

    @Test
    fun `payload loading failure enters token bound failure transition`() {
        val fixture = claimedWithoutPayload()
        service = newService(batchSize = 1)
        every { substrate.claim("test-service", any(), leaseDuration, 1) } returns fixture.ownership
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)
        every { substrate.fail(fixture.ownership, any(), any()) } returns true

        assertEquals(1, service.drainNow())

        verify(exactly = 1) {
            substrate.fail(
                fixture.ownership,
                any(),
                match { it is DomainException },
            )
        }
        verify(exactly = 0) { publisher.publish(any(), any()) }
    }

    @Test
    fun `stale loaded ownership never reaches publisher`() {
        val fixture = claimed(TestEvent("test", 12345))
        fixture.event.deliveryToken = JpaOwnershipToken.fromText("b".repeat(32)).toByteArray()
        service = newService(batchSize = 1)
        every { substrate.claim("test-service", any(), leaseDuration, 1) } returns fixture.ownership
        every { records.findById(fixture.ownership.recordId) } returns Optional.of(fixture.event)

        assertEquals(1, service.drainNow())

        verify(exactly = 0) { publisher.publish(any(), any()) }
        verify(exactly = 0) { substrate.acknowledge(any(), any()) }
        verify(exactly = 0) { substrate.fail(any(), any(), any()) }
    }

    @Test
    fun `no due claim performs no publication`() {
        service = newService(batchSize = 1)
        every { substrate.claim("test-service", any(), leaseDuration, 1) } returns null

        assertEquals(0, service.drainNow())

        verify(exactly = 0) { records.findById(any()) }
        verify(exactly = 0) { publisher.publish(any(), any()) }
    }

    @Test
    fun `partition creation is optional`() {
        service = newService(enableAddPartition = false)

        service.init()

        verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
    }

    @Test
    fun `invalid lease policy is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            newService(leaseDuration = Duration.ofSeconds(5), renewInterval = Duration.ofSeconds(5))
        }
        assertFalse(::service.isInitialized)
    }

    private fun newService(
        batchSize: Int = 4,
        leaseDuration: Duration = this.leaseDuration,
        renewInterval: Duration = this.renewInterval,
        enableAddPartition: Boolean = false,
    ): JpaEventScheduleService = JpaEventScheduleService(
        eventPublisher = publisher,
        executionSubstrate = substrate,
        eventJpaRepository = records,
        serviceName = "test-service",
        batchSize = batchSize,
        leaseDuration = leaseDuration,
        leaseRenewInterval = renewInterval,
        workerThreads = 1,
        enableAddPartition = enableAddPartition,
        jdbcTemplate = jdbcTemplate,
    ).also { service = it }

    private fun claimed(payload: Any): Fixture {
        val ownership = JpaOwnershipClaim(
            recordId = 1L,
            token = JpaOwnershipToken.fromText("a".repeat(32)),
            leaseUntil = LocalDateTime.now().plusMinutes(1),
        )
        val event = Event().init(
            payload = payload,
            svcName = "test-service",
            scheduleAt = LocalDateTime.now().minusSeconds(1),
            expireAfter = Duration.ofHours(1),
            retryTimes = 3,
        ).apply {
            id = ownership.recordId
            eventState = Event.EventState.DELIVERING
            triedTimes = 1
            deliveryToken = ownership.token.toByteArray()
            leaseUntil = ownership.leaseUntil
        }
        return Fixture(ownership, event)
    }

    private fun claimedWithoutPayload(): Fixture {
        val ownership = JpaOwnershipClaim(
            recordId = 1L,
            token = JpaOwnershipToken.fromText("a".repeat(32)),
            leaseUntil = LocalDateTime.now().plusMinutes(1),
        )
        val event = Event(
            id = ownership.recordId,
            eventUuid = UUID.randomUUID().toString(),
            svcName = "test-service",
            eventType = "test",
            data = "{}",
            dataType = "",
            eventState = Event.EventState.DELIVERING,
            triedTimes = 1,
            tryTimes = 3,
            retryPolicy = "{\"retryLimit\":3,\"delaySteps\":[{\"attempt\":1,\"delayMinutes\":1}]}" ,
            deliveryToken = ownership.token.toByteArray(),
            leaseUntil = ownership.leaseUntil,
            expireAt = LocalDateTime.now().plusHours(1),
        )
        return Fixture(ownership, event)
    }

    private data class Fixture(
        val ownership: JpaOwnershipClaim,
        val event: Event,
    )
}
