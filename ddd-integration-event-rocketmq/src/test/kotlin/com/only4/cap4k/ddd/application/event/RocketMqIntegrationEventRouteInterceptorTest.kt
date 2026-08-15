package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class RocketMqIntegrationEventRouteInterceptorTest {
    @BeforeEach
    fun resetRuntimeScope() {
        DefaultIntegrationEventSupervisor.reset()
    }

    @Test
    fun `configured route passes eager attachment and defensive pre-persistence validation`() {
        val interceptor = interceptor(configured = true)

        assertDoesNotThrow { interceptor.onAttach(RoutedEvent("payload"), LocalDateTime.now()) }
        assertDoesNotThrow { interceptor.prePersist(record("content.published")) }
    }

    @Test
    fun `missing route is rejected at both attachment boundaries`() {
        val interceptor = interceptor(configured = false)

        assertThrows<IllegalStateException> {
            interceptor.onAttach(RoutedEvent("payload"), LocalDateTime.now())
        }
        assertThrows<IllegalStateException> { interceptor.prePersist(record("content.published")) }
    }

    @Test
    fun `missing route is rejected by the eager supervisor path before repository save`() {
        val repository = mockk<EventRecordRepository>(relaxed = true)
        val supervisor = supervisor(repository, interceptor(configured = false))

        assertThrows<IllegalStateException> {
            supervisor.schedule(RoutedEvent("payload"), LocalDateTime.now())
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `lazy registration missing route fails before repository save`() {
        val repository = mockk<EventRecordRepository>()
        val event = mockk<EventRecord>(relaxed = true)
        every { event.type } returns "content.published"
        every { repository.create() } returns event
        every { repository.save(any()) } just runs
        val routeInterceptor = interceptor(configured = false)
        val supervisor = supervisor(repository, routeInterceptor)

        supervisor.schedule(LocalDateTime.now()) { RoutedEvent("payload") }

        assertThrows<IllegalStateException> { supervisor.release() }
        verify(exactly = 0) { repository.save(any()) }
    }

    private fun supervisor(
        repository: EventRecordRepository,
        routeInterceptor: RocketMqIntegrationEventRouteInterceptor,
    ): DefaultIntegrationEventSupervisor {
        val interceptorManager = object : IntegrationEventInterceptorManager {
            override val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor> = setOf(routeInterceptor)
            override val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor> = setOf(routeInterceptor)
        }
        return DefaultIntegrationEventSupervisor(
            reliableEventCoordinator = mockk<ReliableEventCoordinator>(relaxed = true),
            eventRecordRepository = repository,
            integrationEventInterceptorManager = interceptorManager,
            applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true),
            svcName = "content-service",
            executionContextAccessor = ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
            invocationScopeAccessor = { InvocationKind.COMMAND },
        )
    }

    private fun interceptor(configured: Boolean) = RocketMqIntegrationEventRouteInterceptor(
        StaticIntegrationEventRouteResolver(
            routes = if (configured) {
                mapOf("content.published" to RocketMqIntegrationEventRoute("content", "published"))
            } else {
                emptyMap()
            },
            providerIdentity = "rocketmq",
        )
    )

    private fun record(eventName: String): EventRecord = mockk {
        every { type } returns eventName
    }

    @IntegrationEvent("content.published")
    private data class RoutedEvent(val value: String)
}
