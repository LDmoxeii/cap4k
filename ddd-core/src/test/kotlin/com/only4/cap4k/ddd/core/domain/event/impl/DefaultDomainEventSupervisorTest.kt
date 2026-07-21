package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.AbstractAggregateRoot
import java.time.LocalDateTime

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
        @DisplayName("供应商附加的领域事件应该在release时解析且不把lambda当作payload")
        fun `supplier attached domain event should resolve during release instead of using lambda as payload`() {
            // given
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.of(2026, 1, 1, 12, 0)
            val actualEvent = TestDomainEvent("supplier event")
            val capturedPayloads = mutableListOf<Any>()

            every { eventRecordRepository.create() } answers {
                mockk<EventRecord>().also { eventRecord ->
                    every { eventRecord.init(any(), any(), any(), any(), any()) } answers {
                        capturedPayloads.add(firstArg())
                    }
                    every { eventRecord.markPersist(any()) } just Runs
                }
            }

            supervisor.attach(entity, schedule) { actualEvent }

            // when
            supervisor.release(setOf(entity))

            // then
            assertEquals(listOf(actualEvent), capturedPayloads)
            assertFalse(capturedPayloads.single() is Function0<*>)
        }

        @Test
        @DisplayName("同一实体上相等的领域事件payload不应该被去重")
        fun `equal domain event payloads attached to one entity should not be deduplicated`() {
            // given
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()
            val event1 = TestDomainEvent("same")
            val event2 = TestDomainEvent("same")

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs

            supervisor.attach(event1, entity, schedule)
            supervisor.attach(event2, entity, schedule)

            // when
            supervisor.release(setOf(entity))

            // then
            verify(exactly = 2) { eventRecordRepository.create() }
            verify(exactly = 2) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("detach相等payload时只移除指定的一个附件")
        fun `detach should remove only one equal domain event attachment`() {
            // given
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()
            val event1 = TestDomainEvent("same")
            val event2 = TestDomainEvent("same")

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs

            supervisor.attach(event1, entity, schedule)
            supervisor.attach(event2, entity, schedule)

            // when
            supervisor.detach(event1, entity)
            supervisor.release(setOf(entity))

            // then
            verify(exactly = 1) { eventRecordRepository.create() }
            verify(exactly = 1) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("release只释放当前scope中指定实体的领域事件")
        fun `release should only release domain attachments for supplied entities in current scope`() {
            // given
            val entity1 = TestEntity("entity1")
            val entity2 = TestEntity("entity2")
            val schedule = LocalDateTime.now()

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs

            supervisor.attach(TestDomainEvent("event1"), entity1, schedule)
            supervisor.attach(TestDomainEvent("event2"), entity2, schedule)

            // when
            supervisor.release(setOf(entity1))

            // then
            verify(exactly = 1) { eventRecordRepository.create() }
            verify(exactly = 1) { eventPublisher.publish(any()) }

            clearMocks(eventRecordRepository, eventPublisher, answers = false)

            // when
            supervisor.release(setOf(entity2))

            // then
            verify(exactly = 1) { eventRecordRepository.create() }
            verify(exactly = 1) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("供应商解析出的集成事件应该在release时被拒绝")
        fun `supplier produced integration event should be rejected during release`() {
            // given
            val entity = TestEntity("entity1")
            supervisor.attach(entity, LocalDateTime.now()) { TestIntegrationEvent("integration event") }

            // when & then
            assertThrows<DomainException> {
                supervisor.release(setOf(entity))
            }
        }

        @Test
        @DisplayName("独立ambient scope中供应商抛异常时release应该清理scope")
        fun `release should clear standalone ambient scope when lazy supplier throws`() {
            // given
            val entity = TestEntity("entity1")
            supervisor.attach(entity, LocalDateTime.now()) { throw IllegalStateException("supplier failed") }

            // when & then
            assertThrows<IllegalStateException> {
                supervisor.release(setOf(entity))
            }
            assertTrue(EventRuntimeContext.currentOrNull() == null)
        }

        @Test
        @DisplayName("独立ambient scope中供应商产出集成事件校验失败时release应该清理scope")
        fun `release should clear standalone ambient scope when supplier produces integration event`() {
            // given
            val entity = TestEntity("entity1")
            supervisor.attach(entity, LocalDateTime.now()) { TestIntegrationEvent("integration event") }

            // when & then
            assertThrows<DomainException> {
                supervisor.release(setOf(entity))
            }
            assertTrue(EventRuntimeContext.currentOrNull() == null)
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

        @Test
        @DisplayName("没有显式调度时间的Spring Data领域事件不应该被误判为延迟事件")
        fun `unscheduled Spring Data domain events should not be treated as delayed events`() {
            // given
            val springEntity = TestSpringAggregateRoot()
            springEntity.addDomainEvent(TestDomainEvent("spring event"))
            val releaseTime = LocalDateTime.of(2026, 1, 1, 0, 0)

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs
            every { eventRecordRepository.save(any()) } just Runs

            mockkStatic(LocalDateTime::class)
            every { LocalDateTime.now() } returnsMany listOf(
                releaseTime,
                releaseTime.plusNanos(1)
            )

            try {
                // when
                supervisor.release(setOf(springEntity))

                // then
                verify { eventPublisher.publish(mockEventRecord) }
                verify(exactly = 0) { eventRecordRepository.save(any()) }
            } finally {
                unmockkStatic(LocalDateTime::class)
            }
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

    @Nested
    @DisplayName("Function0事件供应商测试")
    inner class Function0EventSupplierTests {

        @Test
        @DisplayName("release应该正确调用Function0事件供应商")
        fun `release should correctly invoke Function0 event suppliers`() {
            // given
            val actualEvent = TestDomainEvent("supplier event")
            val entity = TestEntity("entity1")
            val payloads = mutableListOf<Any>()
            every { eventRecordRepository.create() } answers {
                mockk<EventRecord>().also { eventRecord ->
                    every { eventRecord.init(any(), any(), any(), any(), any()) } answers {
                        payloads.add(firstArg())
                    }
                    every { eventRecord.markPersist(any()) } just Runs
                }
            }
            supervisor.attach(entity, LocalDateTime.now()) { actualEvent }

            // when
            supervisor.release(setOf(entity))

            // then
            assertEquals(listOf(actualEvent), payloads)
        }

        @Test
        @DisplayName("release应该处理混合的常规事件和Function0事件")
        fun `release should handle mixed regular events and Function0 events`() {
            // given
            val regularEvent = TestDomainEvent("regular event")
            val supplierEvent = TestDomainEvent("supplier event")
            val entity = TestEntity("entity1")
            val payloads = mutableListOf<Any>()
            every { eventRecordRepository.create() } answers {
                mockk<EventRecord>().also { eventRecord ->
                    every { eventRecord.init(any(), any(), any(), any(), any()) } answers {
                        payloads.add(firstArg())
                    }
                    every { eventRecord.markPersist(any()) } just Runs
                }
            }
            supervisor.attach(regularEvent, entity, LocalDateTime.now())
            supervisor.attach(entity, LocalDateTime.now()) { supplierEvent }

            // when
            supervisor.release(setOf(entity))

            // then
            assertEquals(listOf(regularEvent, supplierEvent), payloads)
        }

        @Test
        @DisplayName("release应该在没有事件时不创建事件记录")
        fun `release should not create records when no events exist`() {
            // given
            val entity = TestEntity("entity1")

            // when
            supervisor.release(setOf(entity))

            // then
            verify(exactly = 0) { eventRecordRepository.create() }
        }

        @Test
        @DisplayName("release应该移除实体的事件映射")
        fun `release should remove entity event mapping`() {
            // given
            val event = TestDomainEvent("test event")
            val entity = TestEntity("entity1")
            val schedule = LocalDateTime.now()

            val mockEventRecord = mockk<EventRecord>()
            every { eventRecordRepository.create() } returns mockEventRecord
            every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord.markPersist(any()) } just Runs

            supervisor.attach(event, entity, schedule)

            // when
            supervisor.release(setOf(entity))
            supervisor.release(setOf(entity))

            // then
            verify(exactly = 1) { eventRecordRepository.create() }
        }

        @Test
        @DisplayName("Function0应该支持复杂的事件创建逻辑")
        fun `Function0 should support complex event creation logic`() {
            // given
            var counter = 0
            val complexEventSupplier: () -> TestDomainEvent = {
                counter++
                TestDomainEvent("complex event #$counter")
            }
            val entity = TestEntity("entity1")

            val payloads = mutableListOf<TestDomainEvent>()
            every { eventRecordRepository.create() } answers {
                mockk<EventRecord>().also { eventRecord ->
                    every { eventRecord.init(any(), any(), any(), any(), any()) } answers {
                        payloads.add(firstArg())
                    }
                    every { eventRecord.markPersist(any()) } just Runs
                }
            }
            supervisor.attach(entity, LocalDateTime.now(), complexEventSupplier)

            // when
            supervisor.release(setOf(entity))

            // then
            assertEquals(1, payloads.size)
            assertEquals("complex event #1", payloads.single().message)
            assertEquals(1, counter) // Should only be invoked once
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

    // 测试用的Spring聚合根
    class TestSpringAggregateRoot : AbstractAggregateRoot<TestSpringAggregateRoot>() {
        fun addDomainEvent(event: Any) {
            registerEvent(event)
        }
    }
}
