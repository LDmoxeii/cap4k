package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventPublisher
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.domain.event.JpaEventExecutionSubstrate
import com.only4.cap4k.ddd.domain.event.JpaEventRecordRepository
import com.only4.cap4k.ddd.domain.event.JpaEventScheduleService
import com.only4.cap4k.ddd.domain.event.JpaReliableDomainEventProvider
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:event-coordinator-composition;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ],
)
@Import(JpaEventCoordinatorCompositionIntegrationTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaEventCoordinatorCompositionIntegrationTest {
    @Autowired
    lateinit var records: EventJpaRepository

    @Autowired
    lateinit var provider: JpaReliableDomainEventProvider

    @Autowired
    lateinit var coordinator: JpaEventScheduleService

    @Autowired
    lateinit var dispatcher: RecordingDispatcher

    @BeforeEach
    fun reset() {
        records.deleteAll()
        dispatcher.reset()
    }

    @Test
    fun `production provider and coordinator deliver a persisted Domain Event`() {
        provider.publish(
            DomainTestEvent("created"),
            LocalDateTime.now().minusSeconds(1),
            ExecutionContextSnapshot.EMPTY,
        )

        assertTrue(dispatcher.completed.await(5, java.util.concurrent.TimeUnit.SECONDS))

        val stored = awaitState(Event.EventState.DELIVERED)
        assertEquals(Event.EventState.DELIVERED, stored.eventState)
        assertEquals(1, stored.triedTimes)
        assertNull(stored.deliveryToken)
        assertNull(stored.leaseUntil)
        assertEquals(1, dispatcher.dispatches.get())
    }

    @Test
    fun `production Domain Event failure persists safe retry state and redelivers`() {
        dispatcher.failNext.set(true)
        provider.publish(
            DomainTestEvent("retry"),
            LocalDateTime.now().minusSeconds(1),
            ExecutionContextSnapshot.EMPTY,
        )

        assertTrue(dispatcher.failed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val failed = awaitState(Event.EventState.EXCEPTION)
        assertEquals(Event.EventState.EXCEPTION, failed.eventState)
        assertEquals(1, failed.triedTimes)
        assertNotNull(failed.failureFactsJson)
        assertNull(failed.deliveryToken)
        assertNull(failed.leaseUntil)

        failed.nextTryTime = LocalDateTime.now().minusSeconds(1)
        records.saveAndFlush(failed)
        coordinator.wake()

        assertTrue(dispatcher.completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val delivered = awaitState(Event.EventState.DELIVERED)
        assertEquals(Event.EventState.DELIVERED, delivered.eventState)
        assertEquals(2, delivered.triedTimes)
        assertEquals(2, dispatcher.dispatches.get())
    }

    class TestConfig {
        @Bean
        fun substrate(records: EventJpaRepository): JpaEventExecutionSubstrate =
            JpaEventExecutionSubstrate(records)

        @Bean
        fun eventRecordRepository(records: EventJpaRepository): EventRecordRepository =
            JpaEventRecordRepository(records)

        @Bean
        fun dispatcher(): RecordingDispatcher = RecordingDispatcher()

        @Bean
        fun domainEventInterceptorManager(): DomainEventInterceptorManager =
            DefaultDomainEventInterceptorManager(emptyList())

        @Bean
        fun eventPublisher(
            dispatcher: RecordingDispatcher,
            domainEventInterceptorManager: DomainEventInterceptorManager,
            executionContextManager: DefaultExecutionContextManager,
            deliveryContextManager: ReliableEventDeliveryContextScopeManager,
        ): EventPublisher = DefaultEventPublisher(
            eventHandlerDispatcher = dispatcher,
            integrationEventPublishers = emptyList(),
            eventMessageInterceptorManager = EmptyEventMessageInterceptorManager,
            domainEventInterceptorManager = domainEventInterceptorManager,
            integrationEventInterceptorManager = EmptyIntegrationEventInterceptorManager,
            executionContextScopeManager = executionContextManager,
            executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
            reliableEventDeliveryContextScopeManager = deliveryContextManager,
        )

        @Bean
        fun executionContextManager(): DefaultExecutionContextManager = DefaultExecutionContextManager()

        @Bean
        fun deliveryContextManager(
            executionContextManager: DefaultExecutionContextManager,
        ): ReliableEventDeliveryContextScopeManager =
            DefaultReliableEventDeliveryContextManager(executionContextManager, executionContextManager)

        @Bean
        fun provider(
            eventRecordRepository: EventRecordRepository,
            domainEventInterceptorManager: DomainEventInterceptorManager,
            coordinator: JpaEventScheduleService,
            applicationEventPublisher: org.springframework.context.ApplicationEventPublisher,
        ): JpaReliableDomainEventProvider = JpaReliableDomainEventProvider(
            eventRecordRepository = eventRecordRepository,
            domainEventInterceptorManager = domainEventInterceptorManager,
            reliableEventCoordinator = coordinator,
            applicationEventPublisher = applicationEventPublisher,
            serviceName = SERVICE,
            executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
        )

        @Bean(destroyMethod = "shutdown")
        fun coordinator(
            eventPublisher: EventPublisher,
            substrate: JpaEventExecutionSubstrate,
            records: EventJpaRepository,
            jdbcTemplate: JdbcTemplate,
        ): JpaEventScheduleService = JpaEventScheduleService(
            eventPublisher = eventPublisher,
            executionSubstrate = substrate,
            eventJpaRepository = records,
            serviceName = SERVICE,
            batchSize = 4,
            leaseDuration = Duration.ofSeconds(30),
            leaseRenewInterval = Duration.ofSeconds(5),
            workerThreads = 1,
            enableAddPartition = false,
            jdbcTemplate = jdbcTemplate,
        )
    }

    class RecordingDispatcher : EventHandlerDispatcher {
        val dispatches = AtomicInteger()
        val failNext = AtomicBoolean(false)
        var completed = CountDownLatch(1)
        var failed = CountDownLatch(1)

        override fun dispatch(eventPayload: Any) {
            dispatches.incrementAndGet()
            if (failNext.compareAndSet(true, false)) {
                failed.countDown()
                throw IllegalStateException("handler failed")
            }
            completed.countDown()
        }

        fun reset() {
            dispatches.set(0)
            failNext.set(false)
            completed = CountDownLatch(1)
            failed = CountDownLatch(1)
        }
    }

    private fun awaitState(expected: Event.EventState): Event {
        repeat(100) {
            val stored = records.findAll().singleOrNull()
            if (stored?.eventState == expected) return stored
            Thread.sleep(50)
        }
        return records.findAll().single().also {
            assertEquals(expected, it.eventState)
        }
    }

    @DomainEvent("jpa-coordinator-composition")
    data class DomainTestEvent(val value: String)

    private object EmptyEventMessageInterceptorManager : EventMessageInterceptorManager {
        override val orderedEventMessageInterceptors = emptySet<com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor>()
    }

    private object EmptyIntegrationEventInterceptorManager : IntegrationEventInterceptorManager {
        override val orderedIntegrationEventInterceptors =
            emptySet<com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor>()
        override val orderedEventInterceptors4IntegrationEvent =
            emptySet<com.only4.cap4k.ddd.core.domain.event.EventInterceptor>()
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = [Event::class])
    @EnableJpaRepositories(basePackageClasses = [EventJpaRepository::class])
    class TestApplication

    private companion object {
        const val SERVICE = "event-service"
    }
}
