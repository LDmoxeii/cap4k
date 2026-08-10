package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class RabbitMqIntegrationEventRouteInterceptorTest {
    private val interceptor = RabbitMqIntegrationEventRouteInterceptor(
        StaticIntegrationEventRouteResolver(
            routes = mapOf("content.published" to RabbitMqIntegrationEventRoute("content", "published")),
            providerIdentity = "rabbitmq",
        ),
    )

    @BeforeEach
    fun setUp() = DefaultIntegrationEventSupervisor.reset()

    @AfterEach
    fun tearDown() = DefaultIntegrationEventSupervisor.reset()

    @Test
    fun `eager attachment rejects a missing route before persistence`() {
        assertDoesNotThrow {
            interceptor.onAttach(ContentPublished("ok"), LocalDateTime.parse("2026-08-10T12:00:00"))
        }
        assertThrows<IllegalStateException> {
            interceptor.onAttach(ContentMissing("missing"), LocalDateTime.parse("2026-08-10T12:00:00"))
        }
    }

    @Test
    fun `pre persist defensively rejects a missing route`() {
        val event = mockk<EventRecord> {
            every { type } returns "content.missing"
        }

        assertThrows<IllegalStateException> { interceptor.prePersist(event) }
    }

    @Test
    fun `lazy missing route reaches pre persist guard but never saves a durable record`() {
        val record = mockk<EventRecord>(relaxed = true) {
            every { type } returns "content.missing"
        }
        val repository = mockk<EventRecordRepository> {
            every { create() } returns record
            every { save(any()) } just Runs
        }
        val manager = object : IntegrationEventInterceptorManager {
            override val orderedIntegrationEventInterceptors = setOf(interceptor)
            override val orderedEventInterceptors4IntegrationEvent = setOf(interceptor)
        }
        val supervisor = DefaultIntegrationEventSupervisor(
            reliableEventCoordinator = mockk<ReliableEventCoordinator>(relaxed = true),
            eventRecordRepository = repository,
            integrationEventInterceptorManager = manager,
            applicationEventPublisher = mockk(relaxed = true),
            svcName = "test-service",
            invocationScopeAccessor = InvocationScopeAccessor { InvocationKind.COMMAND },
        )
        supervisor.enqueue { ContentMissing("lazy") }

        assertThrows<IllegalStateException> { supervisor.release() }

        verify(exactly = 1) { repository.create() }
        verify(exactly = 0) { repository.save(any()) }
    }

    @IntegrationEvent("content.published")
    private data class ContentPublished(val value: String)

    @IntegrationEvent("content.missing")
    private data class ContentMissing(val value: String)
}
