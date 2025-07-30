package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.AbstractAggregateRoot
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * DefaultDomainEventSupervisor测试
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
@DisplayName("DefaultDomainEventSupervisor 测试")
class DefaultDomainEventSupervisorTest {

    private lateinit var eventRecordRepository: EventRecordRepository
    private lateinit var domainEventInterceptorManager: DomainEventInterceptorManager
    private lateinit var eventPublisher: EventPublisher
    private lateinit var applicationEventPublisher: ApplicationEventPublisher
    private lateinit var supervisor: DefaultDomainEventSupervisor

    private val svcName = "test-service"

    @BeforeEach
    fun setUp() {
        eventRecordRepository = mockk()
        domainEventInterceptorManager = mockk()
        eventPublisher = mockk()
        applicationEventPublisher = mockk()

        // Mock 默认行为
        every { domainEventInterceptorManager.orderedDomainEventInterceptors } returns emptySet()
        every { domainEventInterceptorManager.orderedEventInterceptors4DomainEvent } returns emptySet()
        every { eventPublisher.publish(any()) } just Runs
        every { applicationEventPublisher.publishEvent(any()) } just Runs

        supervisor = DefaultDomainEventSupervisor(
            eventRecordRepository,
            domainEventInterceptorManager,
            eventPublisher,
            applicationEventPublisher,
            svcName
        )

        // 重置线程本地变量
        DefaultDomainEventSupervisor.reset()
    }

    @AfterEach
    fun tearDown() {
        DefaultDomainEventSupervisor.reset()
        clearAllMocks()
    }

    @Nested
    @DisplayName("事件附加测试")
    inner class AttachEventTests {

        @Test
        @DisplayName("应该能够附加领域事件到实体")
        fun `should attach domain event to entity`() {
            // given
            val event = TestDomainEvent("test event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            // when
            supervisor.attach(event, entity, schedule)

            // then
            verify {
                domainEventInterceptorManager.orderedDomainEventInterceptors
            }
        }

        @Test
        @DisplayName("应该拒绝附加集成事件")
        fun `should reject attaching integration events`() {
            // given
            val integrationEvent = TestIntegrationEvent("integration event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            // when & then
            assertThrows<DomainException> {
                supervisor.attach(integrationEvent, entity, schedule)
            }
        }

        @Test
        @DisplayName("应该能够附加多个事件到同一实体")
        fun `should attach multiple events to same entity`() {
            // given
            val event1 = TestDomainEvent("event1")
            val event2 = TestDomainEvent("event2")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            // when
            supervisor.attach(event1, entity, schedule)
            supervisor.attach(event2, entity, schedule)

            // then
            verify(exactly = 2) {
                domainEventInterceptorManager.orderedDomainEventInterceptors
            }
        }

        @Test
        @DisplayName("应该能够处理聚合根实体")
        fun `should handle aggregate root entities`() {
            // given
            val event = TestDomainEvent("test event")
            val aggregate = TestAggregate("agg1")
            val schedule = LocalDateTime.now()
            aggregate._wrap(TestEntity("agg1"))

            // when
            supervisor.attach(event, aggregate, schedule)

            // then
            verify {
                domainEventInterceptorManager.orderedDomainEventInterceptors
            }
        }
    }

    @Nested
    @DisplayName("事件分离测试")
    inner class DetachEventTests {

        @Test
        @DisplayName("应该能够从实体分离事件")
        fun `should detach event from entity`() {
            // given
            val event = TestDomainEvent("test event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            supervisor.attach(event, entity, schedule)

            // when
            supervisor.detach(event, entity)

            // then
            verify {
                domainEventInterceptorManager.orderedDomainEventInterceptors
            }
        }

        @Test
        @DisplayName("分离不存在的事件应该安全处理")
        fun `should safely handle detaching non-existent events`() {
            // given
            val event = TestDomainEvent("test event")
            val entity = TestEntity("entity1")

            // when & then - 不应该抛出异常
            supervisor.detach(event, entity)
        }
    }

    @Nested
    @DisplayName("事件发布测试")
    inner class ReleaseEventTests {

        @Test
        @DisplayName("应该发布附加到实体的事件")
        fun `should release events attached to entities`() {
            // given
            val event1 = TestDomainEvent("event1")
            val event2 = TestDomainEvent("event2")
            val entity1 = TestEntity("entity1")
            val entity2 = TestEntity("entity2")
            val schedule = LocalDateTime.now()

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs

            supervisor.attach(event1, entity1, schedule)
            supervisor.attach(event2, entity2, schedule)

            // when
            supervisor.release(setOf(entity1, entity2))

            // then
            verify(exactly = 2) { eventRecordRepository.create() }
            verify(exactly = 2) { eventPublisher.publish(any()) }
            verify(exactly = 2) { applicationEventPublisher.publishEvent(any()) }
        }

        @Test
        @DisplayName("应该处理空实体集合")
        fun `should handle empty entity set`() {
            // given
            val entities = emptySet<Any>()

            // when
            supervisor.release(entities)

            // then
            verify(exactly = 2) { applicationEventPublisher.publishEvent(any()) }
        }

        @Test
        @DisplayName("应该处理需要持久化的事件")
        fun `should handle events that need persistence`() {
            // given
            val event = TestPersistableDomainEvent("persistent event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs
            every { eventRecordRepository.save(any()) } just Runs

            supervisor.attach(event, entity, schedule)

            // when
            supervisor.release(setOf(entity))

            // then
            verify { mockEventRecord.markPersist(true) }
            verify { eventRecordRepository.save(mockEventRecord) }
        }

        @Test
        @DisplayName("应该处理Spring Data AbstractAggregateRoot")
        fun `should handle Spring Data AbstractAggregateRoot`() {
            // given
            val springEntity = TestSpringAggregateRoot()
            springEntity.addDomainEvent(TestDomainEvent("spring event"))

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs

            // when
            supervisor.release(setOf(springEntity))

            // then
            verify { eventRecordRepository.create() }
            verify { eventPublisher.publish(any()) }
        }
    }

    @Nested
    @DisplayName("延迟事件测试")
    inner class DelayedEventTests {

        @Test
        @DisplayName("应该处理延迟发送的事件")
        fun `should handle delayed events`() {
            // given
            val event = TestDomainEvent("delayed event")
            val entity = TestEntity("entity1")
            val futureSchedule = LocalDateTime.now().plusMinutes(30)

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs
            every { eventRecordRepository.save(any()) } just Runs

            supervisor.attach(event, entity, futureSchedule)

            // when
            supervisor.release(setOf(entity))

            // then
            verify { mockEventRecord.markPersist(true) }
            verify { eventRecordRepository.save(mockEventRecord) }
        }

        @Test
        @DisplayName("应该正确获取事件发送时间")
        fun `should correctly get event delivery time`() {
            // given
            val event = TestDomainEvent("timed event")
            val entity = TestEntity("entity1")
            val specificTime = LocalDateTime.of(2025, 7, 24, 10, 30, 0)

            supervisor.attach(event, entity, specificTime)

            // when
            val deliveryTime = supervisor.getDeliverTime(event)

            // then
            assertEquals(specificTime, deliveryTime)
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("应该处理事件发布过程中的异常")
        fun `should handle exceptions during event publishing`() {
            // given
            val event = TestDomainEvent("error event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            every { eventRecordRepository.create() } throws RuntimeException("Repository error")

            supervisor.attach(event, entity, schedule)

            // when & then
            assertThrows<RuntimeException> {
                supervisor.release(setOf(entity))
            }
        }
    }

    @Nested
    @DisplayName("线程本地存储测试")
    inner class ThreadLocalStorageTests {

        @Test
        @DisplayName("应该正确管理线程本地存储")
        fun `should properly manage thread local storage`() {
            // given
            val event = TestDomainEvent("thread local event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            // when
            supervisor.attach(event, entity, schedule)
            DefaultDomainEventSupervisor.reset()

            // 重置后再次发布应该不包含之前的事件
            supervisor.release(setOf(entity))

            // then
            verify(exactly = 0) { eventRecordRepository.create() }
        }

        @Test
        @DisplayName("不同线程应该有独立的事件存储")
        fun `different threads should have independent event storage`() {
            // given
            val event1 = TestDomainEvent("thread1 event")
            val event2 = TestDomainEvent("thread2 event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs

            // when
            val thread1 = Thread {
                supervisor.attach(event1, entity, schedule)
                supervisor.release(setOf(entity))
            }

            val thread2 = Thread {
                supervisor.attach(event2, entity, schedule)
                supervisor.release(setOf(entity))
            }

            thread1.start()
            thread2.start()
            thread1.join()
            thread2.join()

            // then
            verify(exactly = 2) { eventRecordRepository.create() }
        }
    }

    // 测试用的事件类
    @DomainEvent
    data class TestDomainEvent(val message: String)

    @DomainEvent(persist = true)
    data class TestPersistableDomainEvent(val message: String)

    @IntegrationEvent
    data class TestIntegrationEvent(val message: String)

    // 测试用的实体类
    data class TestEntity(val id: String)

    // 测试用的聚合根
    class TestAggregate(private val id: String) : Aggregate.Default<TestEntity>()

    // 测试用的Spring聚合根
    class TestSpringAggregateRoot : AbstractAggregateRoot<TestSpringAggregateRoot>() {
        fun addDomainEvent(event: Any) {
            registerEvent(event)
        }
    }
}
