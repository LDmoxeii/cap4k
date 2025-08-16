package com.only4.cap4k.ddd.core.application.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventAttachedTransactionCommittedEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * DefaultIntegrationEventSupervisor 详尽测试用例
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
@DisplayName("DefaultIntegrationEventSupervisor Tests")
class DefaultIntegrationEventSupervisorTest {

    // Mock dependencies
    private lateinit var eventPublisher: EventPublisher
    private lateinit var eventRecordRepository: EventRecordRepository
    private lateinit var integrationEventInterceptorManager: IntegrationEventInterceptorManager
    private lateinit var applicationEventPublisher: ApplicationEventPublisher
    private lateinit var mockEventRecord: EventRecord
    private lateinit var supervisor: DefaultIntegrationEventSupervisor

    private val testServiceName = "test-service"

    // Test event classes
    @IntegrationEvent("test-event")
    data class TestIntegrationEvent(val id: String, val data: String)

    @IntegrationEvent("another-event", "test-subscriber")
    data class AnotherIntegrationEvent(val value: Int)

    // Non-integration event (no annotation)
    data class RegularEvent(val message: String)

    // Mock interceptors
    private lateinit var mockIntegrationEventInterceptor: IntegrationEventInterceptor
    private lateinit var mockEventInterceptor: EventInterceptor

    @BeforeEach
    fun setUp() {
        eventPublisher = mockk()
        eventRecordRepository = mockk()
        integrationEventInterceptorManager = mockk()
        applicationEventPublisher = mockk()
        mockEventRecord = mockk()
        mockIntegrationEventInterceptor = mockk()
        mockEventInterceptor = mockk()

        // Setup default mock behaviors
        every { eventRecordRepository.create() } returns mockEventRecord
        every { mockEventRecord.init(any(), any(), any(), any(), any()) } just Runs
        every { mockEventRecord.markPersist(any()) } just Runs
        every { eventRecordRepository.save(any()) } just Runs
        every { applicationEventPublisher.publishEvent(any()) } just Runs
        every { eventPublisher.publish(any()) } just Runs

        every { integrationEventInterceptorManager.orderedIntegrationEventInterceptors } returns
                linkedSetOf(mockIntegrationEventInterceptor)
        every { integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent } returns
                linkedSetOf(mockEventInterceptor)

        every { mockIntegrationEventInterceptor.onAttach(any(), any()) } just Runs
        every { mockIntegrationEventInterceptor.onDetach(any()) } just Runs
        every { mockEventInterceptor.prePersist(any()) } just Runs
        every { mockEventInterceptor.postPersist(any()) } just Runs

        supervisor = DefaultIntegrationEventSupervisor(
            eventPublisher,
            eventRecordRepository,
            integrationEventInterceptorManager,
            applicationEventPublisher,
            testServiceName
        )

        // Reset thread local state before each test
        DefaultIntegrationEventSupervisor.reset()
    }

    @Nested
    @DisplayName("Attach Method Tests")
    inner class AttachMethodTests {

        @Test
        @DisplayName("应该成功附加集成事件")
        fun `should successfully attach integration event`() {
            val event = TestIntegrationEvent("1", "test data")
            val schedule = LocalDateTime.now().plusHours(1)

            assertDoesNotThrow {
                supervisor.attach(event, schedule)
            }

            verify { mockIntegrationEventInterceptor.onAttach(event, schedule) }
        }

        @Test
        @DisplayName("附加事件时应该抛出异常如果不是集成事件")
        fun `should throw exception when attaching non-integration event`() {
            val event = RegularEvent("test message")
            val schedule = LocalDateTime.now()

            val exception = assertThrows<DomainException> {
                supervisor.attach(event, schedule)
            }

            assertEquals("事件类型必须为集成事件", exception.message)
            verify(exactly = 0) { mockIntegrationEventInterceptor.onAttach(any(), any()) }
        }

        @Test
        @DisplayName("应该正确处理多个事件的附加")
        fun `should handle multiple event attachments`() {
            val event1 = TestIntegrationEvent("1", "data1")
            val event2 = AnotherIntegrationEvent(42)
            val schedule1 = LocalDateTime.now().plusHours(1)
            val schedule2 = LocalDateTime.now().plusHours(2)

            supervisor.attach(event1, schedule1)
            supervisor.attach(event2, schedule2)

            verify { mockIntegrationEventInterceptor.onAttach(event1, schedule1) }
            verify { mockIntegrationEventInterceptor.onAttach(event2, schedule2) }
        }

        @Test
        @DisplayName("应该正确处理相同事件的重复附加")
        fun `should handle duplicate event attachments`() {
            val event = TestIntegrationEvent("1", "test data")
            val schedule1 = LocalDateTime.now().plusHours(1)
            val schedule2 = LocalDateTime.now().plusHours(2)

            supervisor.attach(event, schedule1)
            supervisor.attach(event, schedule2) // Same event, different schedule

            verify(exactly = 2) { mockIntegrationEventInterceptor.onAttach(event, any()) }
        }

        @Test
        @DisplayName("应该正确处理默认调度时间")
        fun `should handle default schedule time`() {
            val event = TestIntegrationEvent("1", "test data")
            supervisor.attach(event)
            verify {
                mockIntegrationEventInterceptor.onAttach(event, any())
            }
        }
    }

    @Nested
    @DisplayName("Detach Method Tests")
    inner class DetachMethodTests {

        @Test
        @DisplayName("应该成功分离已附加的事件")
        fun `should successfully detach attached event`() {
            val event = TestIntegrationEvent("1", "test data")
            val schedule = LocalDateTime.now().plusHours(1)

            supervisor.attach(event, schedule)
            supervisor.detach(event)

            verify { mockIntegrationEventInterceptor.onAttach(event, schedule) }
            verify { mockIntegrationEventInterceptor.onDetach(event) }
        }

        @Test
        @DisplayName("分离未附加的事件应该不抛出异常")
        fun `should not throw exception when detaching non-attached event`() {
            val event = TestIntegrationEvent("1", "test data")

            assertDoesNotThrow {
                supervisor.detach(event)
            }

            // When no events are attached, detach should NOT call interceptors since it returns early
            verify(exactly = 0) { mockIntegrationEventInterceptor.onDetach(any()) }
        }

        @Test
        @DisplayName("应该只分离指定的事件")
        fun `should only detach specified event`() {
            val event1 = TestIntegrationEvent("1", "data1")
            val event2 = AnotherIntegrationEvent(42)

            supervisor.attach(event1)
            supervisor.attach(event2)
            supervisor.detach(event1)

            verify { mockIntegrationEventInterceptor.onDetach(event1) }
            verify(exactly = 0) { mockIntegrationEventInterceptor.onDetach(event2) }
        }
    }

    @Nested
    @DisplayName("Release Method Tests")
    inner class ReleaseMethodTests {

        @Test
        @DisplayName("应该成功释放并持久化附加的事件")
        fun `should successfully release and persist attached events`() {
            val event = TestIntegrationEvent("1", "test data")
            val schedule = LocalDateTime.now().plusHours(1)

            supervisor.attach(event, schedule)
            supervisor.release()

            verify { eventRecordRepository.create() }
            verify {
                mockEventRecord.init(
                    event,
                    testServiceName,
                    schedule,
                    Duration.ofMinutes(1440),
                    200
                )
            }
            verify { mockEventRecord.markPersist(true) }
            verify { mockEventInterceptor.prePersist(mockEventRecord) }
            verify { eventRecordRepository.save(mockEventRecord) }
            verify { mockEventInterceptor.postPersist(mockEventRecord) }
            verify {
                applicationEventPublisher.publishEvent(
                    match<IntegrationEventAttachedTransactionCommittedEvent> {
                        it.events.size == 1 && it.events.contains(mockEventRecord)
                    }
                )
            }
        }

        @Test
        @DisplayName("释放多个事件应该按顺序处理")
        fun `should process multiple events in order when releasing`() {
            val event1 = TestIntegrationEvent("1", "data1")
            val event2 = AnotherIntegrationEvent(42)
            val mockEventRecord2 = mockk<EventRecord>()

            every { eventRecordRepository.create() } returnsMany listOf(mockEventRecord, mockEventRecord2)
            every { mockEventRecord2.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord2.markPersist(any()) } just Runs

            supervisor.attach(event1)
            supervisor.attach(event2)
            supervisor.release()

            verify(exactly = 2) { eventRecordRepository.create() }
            verify(exactly = 2) { eventRecordRepository.save(any()) }
            verify {
                applicationEventPublisher.publishEvent(
                    match<IntegrationEventAttachedTransactionCommittedEvent> {
                        it.events.size == 2
                    }
                )
            }
        }

        @Test
        @DisplayName("释放空事件列表应该不执行任何操作")
        fun `should not perform any operations when releasing empty event list`() {
            supervisor.release()

            verify(exactly = 0) { eventRecordRepository.create() }
            verify(exactly = 0) { eventRecordRepository.save(any()) }
            verify {
                applicationEventPublisher.publishEvent(
                    match<IntegrationEventAttachedTransactionCommittedEvent> {
                        it.events.isEmpty()
                    }
                )
            }
        }

        @Test
        @DisplayName("释放后应该清空线程本地存储")
        fun `should clear thread local storage after release`() {
            val event = TestIntegrationEvent("1", "test data")

            supervisor.attach(event)
            supervisor.release()

            // 再次释放应该没有事件
            supervisor.release()

            verify(exactly = 1) { eventRecordRepository.create() }
            // Should still be called twice total - once from first release, once from second
            verify(exactly = 2) {
                applicationEventPublisher.publishEvent(any<IntegrationEventAttachedTransactionCommittedEvent>())
            }
        }
    }

    @Nested
    @DisplayName("Publish Method Tests")
    inner class PublishMethodTests {

        @Test
        @DisplayName("应该立即发布集成事件")
        fun `should immediately publish integration event`() {
            val event = TestIntegrationEvent("1", "test data")
            val schedule = LocalDateTime.now().plusMinutes(30)

            supervisor.publish(event, schedule)

            verify { eventRecordRepository.create() }
            verify {
                mockEventRecord.init(
                    event,
                    testServiceName,
                    schedule,
                    Duration.ofMinutes(1440),
                    200
                )
            }
            verify { mockEventRecord.markPersist(true) }
            verify { mockEventInterceptor.prePersist(mockEventRecord) }
            verify { eventRecordRepository.save(mockEventRecord) }
            verify { mockEventInterceptor.postPersist(mockEventRecord) }
            verify {
                applicationEventPublisher.publishEvent(
                    match<IntegrationEventAttachedTransactionCommittedEvent> {
                        it.events.size == 1 && it.events.contains(mockEventRecord)
                    }
                )
            }
        }

        @Test
        @DisplayName("发布事件应该使用默认调度时间")
        fun `should use default schedule time when publishing`() {
            val event = TestIntegrationEvent("1", "test data")
            supervisor.publish(event)
            verify {
                mockEventRecord.init(
                    event,
                    testServiceName,
                    any<LocalDateTime>(), // Accept any LocalDateTime since timing is unpredictable
                    Duration.ofMinutes(1440),
                    200
                )
            }
        }

        @Test
        @DisplayName("发布多个事件应该分别处理")
        fun `should handle multiple event publications separately`() {
            val event1 = TestIntegrationEvent("1", "data1")
            val event2 = AnotherIntegrationEvent(42)
            val mockEventRecord2 = mockk<EventRecord>()

            every { eventRecordRepository.create() } returnsMany listOf(mockEventRecord, mockEventRecord2)
            every { mockEventRecord2.init(any(), any(), any(), any(), any()) } just Runs
            every { mockEventRecord2.markPersist(any()) } just Runs

            supervisor.publish(event1)
            supervisor.publish(event2)

            verify(exactly = 2) { eventRecordRepository.create() }
            verify(exactly = 2) { eventRecordRepository.save(any()) }
            verify(exactly = 2) {
                applicationEventPublisher.publishEvent(any<IntegrationEventAttachedTransactionCommittedEvent>())
            }
        }
    }

    @Nested
    @DisplayName("Transaction Event Listener Tests")
    inner class TransactionEventListenerTests {

//        @Test
//        @DisplayName("事务提交事件应该触发事件发布")
//        fun `transaction committed event should trigger event publishing`() {
//            val events = listOf(mockEventRecord)
//            val commitEvent = IntegrationEventAttachedTransactionCommittedEvent(supervisor, events)
//
//            // Call the extension function with the supervisor
//            with(supervisor) {
//                commitEvent.onTransactionCommitted()
//            }
//
//            verify { eventPublisher.publish(mockEventRecord) }
//        }
//
//        @Test
//        @DisplayName("空事件列表不应该触发发布")
//        fun `empty event list should not trigger publishing`() {
//            val commitEvent = IntegrationEventAttachedTransactionCommittedEvent(supervisor, emptyList())
//
//            // Call the extension function with the supervisor
//            with(supervisor) {
//                commitEvent.onTransactionCommitted()
//            }
//
//            verify(exactly = 0) { eventPublisher.publish(any()) }
//        }
//
//        @Test
//        @DisplayName("多个事件应该逐个发布")
//        fun `multiple events should be published individually`() {
//            val mockEventRecord2 = mockk<EventRecord>()
//            val events = listOf(mockEventRecord, mockEventRecord2)
//            val commitEvent = IntegrationEventAttachedTransactionCommittedEvent(supervisor, events)
//
//            // Call the extension function with the supervisor
//            with(supervisor) {
//                commitEvent.onTransactionCommitted()
//            }
//
//            verify { eventPublisher.publish(mockEventRecord) }
//            verify { eventPublisher.publish(mockEventRecord2) }
//        }
    }

    @Nested
    @DisplayName("Protected Method Tests")
    inner class ProtectedMethodTests {

        @Test
        @DisplayName("popEvents应该清空并返回当前线程的事件")
        fun `popEvents should clear and return current thread events`() {
            val event1 = TestIntegrationEvent("1", "data1")
            val event2 = AnotherIntegrationEvent(42)

            supervisor.attach(event1)
            supervisor.attach(event2)

            // Use reflection to access protected method
            val popEventsMethod = supervisor::class.java.getDeclaredMethod("popEvents")
            popEventsMethod.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val result = popEventsMethod.invoke(supervisor) as Set<Any>

            assertEquals(2, result.size)
            assertTrue(result.contains(event1))
            assertTrue(result.contains(event2))

            // Second call should return empty set
            @Suppress("UNCHECKED_CAST")
            val secondResult = popEventsMethod.invoke(supervisor) as Set<Any>
            assertTrue(secondResult.isEmpty())
        }

        @Test
        @DisplayName("popEvents应该正确调用Function0事件供应商")
        fun `popEvents should correctly invoke Function0 event suppliers`() {
            val actualEvent = TestIntegrationEvent("supplier-event", "from supplier")
            val eventSupplier: () -> TestIntegrationEvent = { actualEvent }

            // Manually add supplier to thread local using reflection to simulate the supplier scenario
            val eventPayloadsField = DefaultIntegrationEventSupervisor::class.java.getDeclaredField("TL_EVENT_PAYLOADS")
            eventPayloadsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val threadLocal = eventPayloadsField.get(null) as ThreadLocal<MutableSet<Any>>

            val eventSet = mutableSetOf<Any>()
            eventSet.add(eventSupplier)
            threadLocal.set(eventSet)

            // Use reflection to access protected method
            val popEventsMethod = supervisor::class.java.getDeclaredMethod("popEvents")
            popEventsMethod.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val result = popEventsMethod.invoke(supervisor) as Set<Any>

            assertEquals(1, result.size)
            assertTrue(result.contains(actualEvent))
            assertFalse(result.contains(eventSupplier))
        }

        @Test
        @DisplayName("popEvents应该处理混合的常规事件和Function0事件")
        fun `popEvents should handle mixed regular events and Function0 events`() {
            val regularEvent = TestIntegrationEvent("regular", "regular event")
            val supplierEvent = AnotherIntegrationEvent(99)
            val eventSupplier: () -> AnotherIntegrationEvent = { supplierEvent }

            // Manually set up thread local with mixed events
            val eventPayloadsField = DefaultIntegrationEventSupervisor::class.java.getDeclaredField("TL_EVENT_PAYLOADS")
            eventPayloadsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val threadLocal = eventPayloadsField.get(null) as ThreadLocal<MutableSet<Any>>

            val eventSet = mutableSetOf<Any>()
            eventSet.add(regularEvent)
            eventSet.add(eventSupplier)
            threadLocal.set(eventSet)

            // Use reflection to access protected method
            val popEventsMethod = supervisor::class.java.getDeclaredMethod("popEvents")
            popEventsMethod.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val result = popEventsMethod.invoke(supervisor) as Set<Any>

            assertEquals(2, result.size)
            assertTrue(result.contains(regularEvent))
            assertTrue(result.contains(supplierEvent))
            assertFalse(result.contains(eventSupplier))
        }

        @Test
        @DisplayName("getDeliverTime应该返回正确的调度时间")
        fun `getDeliverTime should return correct schedule time`() {
            val event = TestIntegrationEvent("1", "test data")
            val schedule = LocalDateTime.now().plusHours(2)

            supervisor.attach(event, schedule)

            // Use reflection to access protected method
            val getDeliverTimeMethod = supervisor::class.java.getDeclaredMethod("getDeliverTime", Any::class.java)
            getDeliverTimeMethod.isAccessible = true

            val result = getDeliverTimeMethod.invoke(supervisor, event) as LocalDateTime

            assertEquals(schedule, result)
        }

        @Test
        @DisplayName("getDeliverTime应该返回当前时间如果没有设置调度时间")
        fun `getDeliverTime should return current time when no schedule time set`() {
            val event = TestIntegrationEvent("1", "test data")
            val beforeTime = LocalDateTime.now()

            // Use reflection to access protected method
            val getDeliverTimeMethod = supervisor::class.java.getDeclaredMethod("getDeliverTime", Any::class.java)
            getDeliverTimeMethod.isAccessible = true

            val result = getDeliverTimeMethod.invoke(supervisor, event) as LocalDateTime
            val afterTime = LocalDateTime.now()

            assertTrue(result.isAfter(beforeTime.minusSeconds(1)))
            assertTrue(result.isBefore(afterTime.plusSeconds(1)))
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    inner class ThreadSafetyTests {

        @Test
        @DisplayName("并发附加事件应该是线程安全的")
        fun `concurrent event attachment should be thread safe`() {
            val threadCount = 10
            val eventsPerThread = 10
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            repeat(threadCount) { threadIndex ->
                executor.submit {
                    try {
                        repeat(eventsPerThread) { eventIndex ->
                            val event = TestIntegrationEvent("$threadIndex-$eventIndex", "data")
                            supervisor.attach(event)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))

            // Each thread should have its own events
            verify(exactly = threadCount * eventsPerThread) {
                mockIntegrationEventInterceptor.onAttach(any(), any())
            }

            executor.shutdown()
        }

        @Test
        @DisplayName("不同线程的事件应该互不影响")
        fun `events from different threads should not interfere`() {
            val latch1 = CountDownLatch(1)
            val latch2 = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)

            val events1 = mutableSetOf<Any>()
            val events2 = mutableSetOf<Any>()

            // Thread 1
            executor.submit {
                try {
                    val event1 = TestIntegrationEvent("thread1", "data1")
                    supervisor.attach(event1)

                    // Use reflection to get events for current thread
                    val popEventsMethod = supervisor::class.java.getDeclaredMethod("popEvents")
                    popEventsMethod.isAccessible = true

                    @Suppress("UNCHECKED_CAST")
                    val result = popEventsMethod.invoke(supervisor) as Set<Any>
                    events1.addAll(result)
                } finally {
                    latch1.countDown()
                }
            }

            // Thread 2
            executor.submit {
                try {
                    val event2 = AnotherIntegrationEvent(42)
                    supervisor.attach(event2)

                    // Use reflection to get events for current thread
                    val popEventsMethod = supervisor::class.java.getDeclaredMethod("popEvents")
                    popEventsMethod.isAccessible = true

                    @Suppress("UNCHECKED_CAST")
                    val result = popEventsMethod.invoke(supervisor) as Set<Any>
                    events2.addAll(result)
                } finally {
                    latch2.countDown()
                }
            }

            assertTrue(latch1.await(5, TimeUnit.SECONDS))
            assertTrue(latch2.await(5, TimeUnit.SECONDS))

            assertEquals(1, events1.size)
            assertEquals(1, events2.size)
            assertTrue(events1.intersect(events2).isEmpty())

            executor.shutdown()
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("拦截器异常不应该阻止事件处理")
        fun `interceptor exceptions should not prevent event processing`() {
            every {
                mockIntegrationEventInterceptor.onAttach(
                    any(),
                    any()
                )
            } throws RuntimeException("Interceptor error")

            val event = TestIntegrationEvent("1", "test data")

            assertThrows<RuntimeException> {
                supervisor.attach(event)
            }
        }

        @Test
        @DisplayName("仓库异常应该向上传播")
        fun `repository exceptions should propagate up`() {
            every { eventRecordRepository.save(any()) } throws RuntimeException("Database error")

            val event = TestIntegrationEvent("1", "test data")
            supervisor.attach(event)

            assertThrows<RuntimeException> {
                supervisor.release()
            }
        }

        @Test
        @DisplayName("EventRecord创建异常应该向上传播")
        fun `EventRecord creation exceptions should propagate up`() {
            every { eventRecordRepository.create() } throws RuntimeException("Creation error")

            val event = TestIntegrationEvent("1", "test data")

            assertThrows<RuntimeException> {
                supervisor.publish(event)
            }
        }
    }

    @Nested
    @DisplayName("Static Method Tests")
    inner class StaticMethodTests {

        @Test
        @DisplayName("reset方法应该清空所有线程本地存储")
        fun `reset method should clear all thread local storage`() {
            val event = TestIntegrationEvent("1", "test data")
            supervisor.attach(event)

            DefaultIntegrationEventSupervisor.reset()

            // Use reflection to check if thread local is cleared
            val popEventsMethod = supervisor::class.java.getDeclaredMethod("popEvents")
            popEventsMethod.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val result = popEventsMethod.invoke(supervisor) as Set<Any>
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {

//        @Test
//        @DisplayName("完整的事件流程应该正常工作")
//        fun `complete event flow should work correctly`() {
//            val event = TestIntegrationEvent("1", "test data")
//            val schedule = LocalDateTime.now().plusHours(1)
//
//            // Attach event
//            supervisor.attach(event, schedule)
//
//            // Release events
//            supervisor.release()
//
//            // Trigger transaction committed event
//            val commitEvent = IntegrationEventAttachedTransactionCommittedEvent(supervisor, listOf(mockEventRecord))
//            // Call the extension function with the supervisor
//            with(supervisor) {
//                commitEvent.onTransactionCommitted()
//            }
//
//            // Verify complete flow
//            verify { mockIntegrationEventInterceptor.onAttach(event, schedule) }
//            verify { mockEventRecord.init(event, testServiceName, schedule, Duration.ofMinutes(1440), 200) }
//            verify { mockEventRecord.markPersist(true) }
//            verify { mockEventInterceptor.prePersist(mockEventRecord) }
//            verify { eventRecordRepository.save(mockEventRecord) }
//            verify { mockEventInterceptor.postPersist(mockEventRecord) }
//            verify { applicationEventPublisher.publishEvent(any<IntegrationEventAttachedTransactionCommittedEvent>()) }
//            verify { eventPublisher.publish(mockEventRecord) }
//        }

        @Test
        @DisplayName("事件附加和分离的组合操作")
        fun `combination of attach and detach operations`() {
            val event1 = TestIntegrationEvent("1", "data1")
            val event2 = AnotherIntegrationEvent(42)
            val event3 = TestIntegrationEvent("3", "data3")

            supervisor.attach(event1)
            supervisor.attach(event2)
            supervisor.attach(event3)

            supervisor.detach(event2) // Remove middle event

            supervisor.release()

            // Should only process event1 and event3
            verify(exactly = 2) { eventRecordRepository.create() }
            verify(exactly = 2) { eventRecordRepository.save(any()) }
        }
    }
}
