package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextAccessor
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.net.ServerSocket
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HttpIntegrationEventSelfRouteTest {
    @Test
    fun `one process can publish and consume through the real fixed HTTP endpoint`() {
        val port = ServerSocket(0).use { it.localPort }
        val context = SpringApplicationBuilder(SelfRouteApplication::class.java)
            .web(WebApplicationType.SERVLET)
            .properties(
                "server.port=$port",
                "spring.application.name=http-self-route",
                "spring.main.banner-mode=off",
                "logging.level.root=ERROR",
                "cap4k.ddd.integration.event.http.routes[http.self-route]=http://127.0.0.1:$port",
            )
            .run()
        try {
            val publisher = context.getBean(IntegrationEventPublisher::class.java)
            val handler = context.getBean(SelfRouteHandler::class.java)
            val querySignals = context.getBean(QueryExecutionSignals::class.java)
            val stateRegistry = context.getBean(RuntimeProviderStateRegistry::class.java)
            val executionContextAccessor = context.getBean(ExecutionContextAccessor::class.java)
            val event = event("payload")
            val envelope = IntegrationEventEnvelope(
                eventId = event.id,
                eventType = event.type,
                originService = "http-self-route",
                publishedAt = event.publishedAt,
                deliveryAttempt = 1,
                executionContext = emptyList(),
                payloadJson = RuntimeJson.write(SelfRouteEvent("payload")),
            )
            val callbackCompleted = CountDownLatch(1)
            val callbackFailure = AtomicReference<Throwable?>()
            val callbackAfterHandler = AtomicBoolean(false)

            assertEquals(RuntimeProviderState.RECOVERING, stateRegistry.httpFact().state)
            assertEquals("enrolled", stateRegistry.httpFact().category)
            publisher.publish(
                event,
                envelope,
                object : IntegrationEventPublisher.PublishCallback {
                    override fun onSuccess(event: EventRecord) {
                        callbackAfterHandler.set(querySignals.finished.count == 0L)
                        callbackCompleted.countDown()
                    }

                    override fun onException(event: EventRecord, throwable: Throwable) {
                        callbackFailure.set(throwable)
                        callbackCompleted.countDown()
                    }
                },
            )

            assertTrue(callbackCompleted.await(5, TimeUnit.SECONDS), "HTTP self-route callback timed out")
            assertTrue(handler.received.await(1, TimeUnit.SECONDS), "HTTP self-route Handler timed out")
            assertTrue(querySignals.finished.await(1, TimeUnit.SECONDS), "tracked Query timed out")
            assertNull(callbackFailure.get())
            assertTrue(callbackAfterHandler.get(), "publisher completed before the local Handler scope")
            assertEquals(SelfRouteEvent("payload"), handler.event)
            assertEquals("event-self-route", handler.deliveryContext?.eventId)
            assertEquals(RuntimeProviderState.HEALTHY, stateRegistry.httpFact().state)
            assertEquals("handoff-succeeded", stateRegistry.httpFact().category)
            assertNull(context.getBean(ReliableEventDeliveryContextAccessor::class.java).currentOrNull())
            assertTrue(executionContextAccessor.current().isEmpty)

            val failedEvent = event("fail")
            val failedEnvelope = IntegrationEventEnvelope(
                eventId = failedEvent.id,
                eventType = failedEvent.type,
                originService = "http-self-route",
                publishedAt = failedEvent.publishedAt,
                deliveryAttempt = 1,
                executionContext = emptyList(),
                payloadJson = RuntimeJson.write(SelfRouteEvent("fail")),
            )
            val failedCallback = CountDownLatch(1)
            val failedSuccess = AtomicBoolean(false)
            val failedFailure = AtomicReference<Throwable?>()
            publisher.publish(
                failedEvent,
                failedEnvelope,
                object : IntegrationEventPublisher.PublishCallback {
                    override fun onSuccess(event: EventRecord) {
                        failedSuccess.set(true)
                        failedCallback.countDown()
                    }

                    override fun onException(event: EventRecord, throwable: Throwable) {
                        failedFailure.set(throwable)
                        failedCallback.countDown()
                    }
                },
            )
            assertTrue(failedCallback.await(5, TimeUnit.SECONDS), "failed HTTP self-route callback timed out")
            assertTrue(querySignals.failed.await(1, TimeUnit.SECONDS), "failed tracked Query timed out")
            assertTrue(!failedSuccess.get())
            assertTrue(failedFailure.get() != null)
            assertEquals(RuntimeProviderState.DEGRADED, stateRegistry.httpFact().state)
            assertTrue(executionContextAccessor.current().isEmpty)
        } finally {
            context.close()
            DefaultIntegrationEventSupervisor.reset()
        }
    }


    private fun RuntimeProviderStateRegistry.httpFact() = snapshot().single {
        it.providerId == HttpIntegrationEventAutoConfiguration.HTTP_PROVIDER_ID
    }
    private fun event(value: String): EventRecord = mock(EventRecord::class.java).also { event ->
        `when`(event.id).thenReturn("event-self-route")
        `when`(event.type).thenReturn("http.self-route")
        `when`(event.publishedAt).thenReturn(Instant.parse("2026-08-09T00:00:00Z"))
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SelfRouteSupport::class, SelfRouteHandler::class)
    class SelfRouteApplication

    @Configuration(proxyBeanMethods = false)
    class SelfRouteSupport {
        @Bean
        fun eventRecordRepository(): EventRecordRepository = mock(EventRecordRepository::class.java)

        @Bean
        fun queryExecution(): QueryExecution = object : QueryExecution {
            override val active: Boolean = true

            override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
        }

        @Bean
        fun queryExecutionSignals(): QueryExecutionSignals = QueryExecutionSignals()

        @Bean
        fun selfRouteQueryHandler(signals: QueryExecutionSignals): SelfRouteQueryHandler =
            SelfRouteQueryHandler(signals)

        @Bean
        fun reliableEventCoordinator(): ReliableEventCoordinator = mock(ReliableEventCoordinator::class.java)

        @Bean
        fun integrationEventInterceptorManager(
            eventInterceptors: List<EventInterceptor>,
        ): IntegrationEventInterceptorManager = object : IntegrationEventInterceptorManager {
            override val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor> = eventInterceptors
                .filterIsInstance<IntegrationEventInterceptor>()
                .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
                .toCollection(LinkedHashSet())
            override val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor> = eventInterceptors
                .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
                .toCollection(LinkedHashSet())
        }
    }

    class SelfRouteHandler(
        private val deliveryContextAccessor: ReliableEventDeliveryContextAccessor,
        private val querySupervisor: QuerySupervisor,
        private val querySignals: QueryExecutionSignals,
    ) {
        val received = CountDownLatch(1)
        @Volatile var event: SelfRouteEvent? = null
        @Volatile var deliveryContext: ReliableEventDeliveryContext? = null

        @EventListener
        fun on(event: SelfRouteEvent) {
            querySupervisor.askAsync(SelfRouteQuery(event.value)).whenComplete { _, failure ->
                if (failure != null) querySignals.failed.countDown()
            }
            this.event = event
            this.deliveryContext = deliveryContextAccessor.currentOrNull()
            received.countDown()
        }
    }
}

@IntegrationEvent("http.self-route")
data class SelfRouteEvent(val value: String)

data class SelfRouteQuery(val value: String) : Query<String>

class QueryExecutionSignals {
    val finished = CountDownLatch(1)
    val failed = CountDownLatch(1)
}

class SelfRouteQueryHandler(
    private val signals: QueryExecutionSignals,
) : QueryHandler<SelfRouteQuery, String> {
    override fun handle(query: SelfRouteQuery): String {
        try {
            if (query.value == "fail") {
                throw IllegalStateException("query failed")
            }
            Thread.sleep(100)
            return query.value
        } finally {
            signals.finished.countDown()
        }
    }
}
