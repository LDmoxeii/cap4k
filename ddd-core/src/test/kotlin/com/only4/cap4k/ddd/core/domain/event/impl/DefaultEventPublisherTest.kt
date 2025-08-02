package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.*
import com.only4.cap4k.ddd.core.share.Constants
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.*
import org.springframework.messaging.Message
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * DefaultEventPublisher测试
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
@DisplayName("DefaultEventPublisher 测试")
class DefaultEventPublisherTest {

    private lateinit var eventSubscriberManager: EventSubscriberManager
    private lateinit var integrationEventPublishers: List<IntegrationEventPublisher>
    private lateinit var eventRecordRepository: EventRecordRepository
    private lateinit var eventMessageInterceptorManager: EventMessageInterceptorManager
    private lateinit var domainEventInterceptorManager: DomainEventInterceptorManager
    private lateinit var integrationEventInterceptorManager: IntegrationEventInterceptorManager
    private lateinit var publisher: DefaultEventPublisher

    private val threadPoolSize = 2

    @BeforeEach
    fun setUp() {
        eventSubscriberManager = mockk()
        integrationEventPublishers = listOf(mockk(), mockk())
        eventRecordRepository = mockk()
        eventMessageInterceptorManager = mockk()
        domainEventInterceptorManager = mockk()
        integrationEventInterceptorManager = mockk()

        // Mock 默认行为
        every { eventSubscriberManager.dispatch(any()) } just Runs
        every { eventRecordRepository.save(any()) } returnsArgument 0
        every { eventMessageInterceptorManager.orderedEventMessageInterceptors } returns emptySet()
        every { domainEventInterceptorManager.orderedEventInterceptors4DomainEvent } returns emptySet()
        every { integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent } returns emptySet()
        every { integrationEventPublishers[0].publish(any(), any()) } just Runs
        every { integrationEventPublishers[1].publish(any(), any()) } just Runs

        publisher = DefaultEventPublisher(
            eventSubscriberManager,
            integrationEventPublishers,
            eventRecordRepository,
            eventMessageInterceptorManager,
            domainEventInterceptorManager,
            integrationEventInterceptorManager,
            threadPoolSize
        )
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTests {

        @Test
        @DisplayName("应该能够初始化线程池")
        fun `should initialize thread pool`() {
            // given
            val eventRecord = createTestEventRecord()

            // when
            publisher.publish(eventRecord)

            // then - 初始化应该成功，不抛出异常
            verify { eventSubscriberManager.dispatch(any()) }
        }

        @Test
        @DisplayName("多次初始化应该是安全的")
        fun `multiple initializations should be safe`() {
            // given
            val eventRecord1 = createTestEventRecord()
            val eventRecord2 = createTestEventRecord()

            // when
            publisher.publish(eventRecord1)
            publisher.publish(eventRecord2)

            // then
            verify(exactly = 2) { eventSubscriberManager.dispatch(any()) }
        }

        @Test
        @DisplayName("多次调用init方法应该只创建一个executor实例")
        fun `should create only one executor instance when init called multiple times`() {
            // given
            val publisher = DefaultEventPublisher(
                eventSubscriberManager,
                integrationEventPublishers,
                eventRecordRepository,
                eventMessageInterceptorManager,
                domainEventInterceptorManager,
                integrationEventInterceptorManager,
                2
            )

            // when - 多次调用init方法
            publisher.init()
            publisher.init()
            publisher.init()

            // then - 通过反射获取lazy delegate
            val lazyDelegateField = DefaultEventPublisher::class.java.getDeclaredField("executor\$delegate")
            lazyDelegateField.isAccessible = true
            val lazyDelegate = lazyDelegateField.get(publisher) as Lazy<ScheduledExecutorService>

            // 验证lazy已初始化
            assertTrue(lazyDelegate.isInitialized())

            // 获取多次executor实例，应该是同一个对象
            val executor1 = lazyDelegate.value
            val executor2 = lazyDelegate.value
            val executor3 = lazyDelegate.value

            // 验证是同一个实例
            assertSame(executor1, executor2)
            assertSame(executor2, executor3)

            // 验证线程池大小配置正确
            assertTrue(executor1 is ScheduledThreadPoolExecutor)
            assertEquals(2, executor1.corePoolSize)
        }
    }

    @Nested
    @DisplayName("领域事件发布测试")
    inner class DomainEventPublishTests {

        @Test
        @DisplayName("应该发布普通领域事件")
        fun `should publish regular domain events`() {
            // given
            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN,
                persist = false
            )

            // when
            publisher.publish(eventRecord)

            // then
            verify { eventSubscriberManager.dispatch(any()) }
            verify { eventMessageInterceptorManager.orderedEventMessageInterceptors }
            verify { domainEventInterceptorManager.orderedEventInterceptors4DomainEvent }
        }

        @Test
        @DisplayName("应该发布需要持久化的领域事件")
        fun `should publish persistent domain events`() {
            // given
            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN,
                persist = true
            )

            // when
            publisher.publish(eventRecord)

            // Wait for async execution to complete
            Thread.sleep(100)

            // then
            verify { eventSubscriberManager.dispatch(any()) }
            verify { eventRecordRepository.save(any()) }
        }

        @Test
        @DisplayName("应该处理领域事件发布异常")
        fun `should handle domain event publishing exceptions`() {
            // given
            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN
            )
            every { eventSubscriberManager.dispatch(any()) } throws RuntimeException("Dispatch failed")

            // when & then
            assertThrows<DomainException> {
                publisher.publish(eventRecord)
            }

            verify { domainEventInterceptorManager.orderedEventInterceptors4DomainEvent }
        }

        @Test
        @DisplayName("应该处理延迟领域事件")
        fun `should handle delayed domain events`() {
            // given
            val futureTime = LocalDateTime.now().plusMinutes(5)
            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN,
                scheduleTime = futureTime
            )

            // when
            publisher.publish(eventRecord)

            // then - 延迟事件应该被调度，不会立即执行dispatch
            Thread.sleep(100) // 给调度器一些时间
            // 注意：这里我们不能直接验证延迟执行，因为它是异步的
        }
    }

    @Nested
    @DisplayName("集成事件发布测试")
    inner class IntegrationEventPublishTests {

        @Test
        @DisplayName("应该发布集成事件")
        fun `should publish integration events`() {
            // given
            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION
            )

            // when
            publisher.publish(eventRecord)

            // then
            verify { integrationEventPublishers[0].publish(any(), any()) }
            verify { integrationEventPublishers[1].publish(any(), any()) }
            verify { integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent }
        }

        @Test
        @DisplayName("应该处理集成事件发布异常")
        fun `should handle integration event publishing exceptions`() {
            // given
            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION
            )
            every { integrationEventPublishers[0].publish(any(), any()) } throws RuntimeException("Publish failed")

            // when & then
            assertThrows<DomainException> {
                publisher.publish(eventRecord)
            }

            verify { integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent }
        }

        @Test
        @DisplayName("应该处理延迟集成事件")
        fun `should handle delayed integration events`() {
            // given
            val futureTime = LocalDateTime.now().plusMinutes(5)
            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION,
                scheduleTime = futureTime
            )

            // when
            publisher.publish(eventRecord)

            // then - 延迟事件应该被调度
            Thread.sleep(100) // 给调度器一些时间
        }
    }

    @Nested
    @DisplayName("事件重试测试")
    inner class EventRetryTests {

        @Test
        @DisplayName("应该能够重试事件")
        fun `should retry events`() {
            // given
            val eventRecord = createTestEventRecord()
            val minNextTryTime = LocalDateTime.now().minusMinutes(1)

            every { eventRecord.nextTryTime } returns LocalDateTime.now().plusMinutes(1) // 设置为大于minNextTryTime的时间
            every { eventRecord.isValid } returns true
            every { eventRecord.isDelivered } returns true
            every { eventRecord.beginDelivery(any()) } returns true

            // when
            publisher.resume(eventRecord, minNextTryTime)

            // then
            verify { eventRecordRepository.save(eventRecord) }
            verify { eventRecord.markPersist(true) }
        }

        @Test
        @DisplayName("应该处理重试循环保护")
        fun `should handle retry loop protection`() {
            // given
            val eventRecord = createTestEventRecord()
            val minNextTryTime = LocalDateTime.now().plusDays(1) // 很远的未来时间

            every { eventRecord.nextTryTime } returns LocalDateTime.now().minusMinutes(1)
            every { eventRecord.isValid } returns true
            every { eventRecord.beginDelivery(any()) } returns false

            // when & then
            assertThrows<DomainException> {
                publisher.resume(eventRecord, minNextTryTime)
            }
        }
    }

    @Nested
    @DisplayName("发布回调测试")
    inner class PublishCallbackTests {

        @Test
        @DisplayName("成功回调应该正确处理")
        fun `success callback should handle correctly`() {
            // given
            val eventRecord = createTestEventRecord()
            val callback = DefaultEventPublisher.IntegrationEventSendPublishCallback(
                emptySet(),
                emptySet(),
                eventRecordRepository
            )

            every { eventRecord.confirmedDelivery(any()) } just Runs

            // when
            callback.onSuccess(eventRecord)

            // then
            verify { eventRecord.confirmedDelivery(any()) }
            verify { eventRecordRepository.save(eventRecord) }
        }

        @Test
        @DisplayName("异常回调应该正确处理")
        fun `exception callback should handle correctly`() {
            // given
            val eventRecord = createTestEventRecord()
            val throwable = RuntimeException("Test exception")
            val callback = DefaultEventPublisher.IntegrationEventSendPublishCallback(
                emptySet(),
                emptySet(),
                eventRecordRepository
            )

            every { eventRecord.occurredException(any(), any()) } just Runs

            // when
            callback.onException(eventRecord, throwable)

            // then
            verify { eventRecord.occurredException(any(), throwable) }
            verify { eventRecordRepository.save(eventRecord) }
        }
    }

    @Nested
    @DisplayName("拦截器集成测试")
    inner class InterceptorIntegrationTests {

        @Test
        @DisplayName("应该调用所有相关拦截器")
        fun `should call all relevant interceptors`() {
            // given
            val messageInterceptor = mockk<EventMessageInterceptor>()
            val domainInterceptor = mockk<EventInterceptor>()

            every { messageInterceptor.initPublish(any()) } just Runs
            every { messageInterceptor.prePublish(any()) } just Runs
            every { messageInterceptor.postPublish(any()) } just Runs
            every { domainInterceptor.preRelease(any()) } just Runs
            every { domainInterceptor.postRelease(any()) } just Runs
            every { domainInterceptor.prePersist(any()) } just Runs
            every { domainInterceptor.postPersist(any()) } just Runs

            every { eventMessageInterceptorManager.orderedEventMessageInterceptors } returns setOf(messageInterceptor)
            every { domainEventInterceptorManager.orderedEventInterceptors4DomainEvent } returns setOf(domainInterceptor)

            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN,
                persist = true
            )

            // when
            publisher.publish(eventRecord)

            // then
            verify { messageInterceptor.initPublish(any()) }
            verify { messageInterceptor.prePublish(any()) }
            verify { messageInterceptor.postPublish(any()) }
            verify { domainInterceptor.preRelease(any()) }
            verify { domainInterceptor.postRelease(any()) }
            verify { domainInterceptor.prePersist(any()) }
            verify { domainInterceptor.postPersist(any()) }
        }

        @Test
        @DisplayName("拦截器异常应该被正确处理")
        fun `interceptor exceptions should be handled correctly`() {
            // given
            val domainInterceptor = mockk<EventInterceptor>()
            every { domainInterceptor.preRelease(any()) } throws RuntimeException("Interceptor failed")
            every { domainInterceptor.onException(any(), any()) } just Runs

            every { domainEventInterceptorManager.orderedEventInterceptors4DomainEvent } returns setOf(domainInterceptor)

            val eventRecord = createTestEventRecord(
                eventType = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN
            )

            // when & then
            assertThrows<DomainException> {
                publisher.publish(eventRecord)
            }

            verify { domainInterceptor.onException(any(), any()) }
        }
    }

    @Nested
    @DisplayName("并发和性能测试")
    inner class ConcurrencyAndPerformanceTests {

        @Test
        @DisplayName("应该能够并发发布事件")
        fun `should publish events concurrently`() {
            // given
            val eventRecords = (1..10).map {
                createTestEventRecord("event$it")
            }

            // when
            val threads = eventRecords.map { eventRecord ->
                Thread {
                    publisher.publish(eventRecord)
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // then
            verify(exactly = 10) { eventSubscriberManager.dispatch(any()) }
        }

        @Test
        @DisplayName("应该能够处理大量事件")
        fun `should handle large number of events`() {
            // given
            val eventRecords = (1..100).map {
                createTestEventRecord("event$it")
            }

            // when
            eventRecords.forEach { eventRecord ->
                publisher.publish(eventRecord)
            }

            // then
            verify(exactly = 100) { eventSubscriberManager.dispatch(any()) }
        }
    }

    // 辅助方法
    private fun createTestEventRecord(
        payload: String = "test payload",
        eventType: String = Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN,
        persist: Boolean = false,
        scheduleTime: LocalDateTime? = null
    ): EventRecord {
        val eventRecord = mockk<EventRecord>()
        val message = mockk<Message<Any>>()
        val headers = mutableMapOf<String, Any>()


        headers[Constants.HEADER_KEY_CAP4J_EVENT_TYPE] = eventType
        if (persist) {
            headers[Constants.HEADER_KEY_CAP4J_PERSIST] = true
        }
        if (scheduleTime != null) {
            headers[Constants.HEADER_KEY_CAP4J_SCHEDULE] = scheduleTime.toEpochSecond(ZoneOffset.UTC)
        }

        every { eventRecord.message } returns message
        every { eventRecord.payload } returns payload
        every { eventRecord.id } returns "test-id"
        every { eventRecord.markPersist(any()) } just Runs
        every { eventRecord.confirmedDelivery(any()) } just Runs
        every { eventRecord.occurredException(any(), any()) } just Runs
        every { eventRecord.nextTryTime } returns LocalDateTime.now().plusMinutes(10) // 设置为未来时间避免死循环
        every { eventRecord.isValid } returns true
        every { eventRecord.isDelivered } returns true // 添加缺失的Mock属性
        every { eventRecord.beginDelivery(any()) } returns true
        every { message.headers } returns EventMessageInterceptor.ModifiableMessageHeaders(headers)

        return eventRecord
    }
}
