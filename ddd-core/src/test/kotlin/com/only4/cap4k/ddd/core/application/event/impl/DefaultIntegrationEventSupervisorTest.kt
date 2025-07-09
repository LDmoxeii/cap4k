package com.only4.cap4k.ddd.core.application.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventAttachedTransactionCommittedEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@IntegrationEvent
data class TestEvent(val id: String, val message: String)

class DefaultIntegrationEventSupervisorTest {

    @MockK
    private lateinit var eventPublisher: EventPublisher

    @MockK
    private lateinit var eventRecordRepository: EventRecordRepository

    @MockK
    private lateinit var integrationEventInterceptorManager: IntegrationEventInterceptorManager

    @MockK
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockK
    private lateinit var eventRecord: EventRecord

    @MockK
    private lateinit var integrationEventInterceptor: IntegrationEventInterceptor

    @MockK
    private lateinit var eventInterceptor: EventInterceptor

    private lateinit var supervisor: DefaultIntegrationEventSupervisor
    private val svcName = "test-service"
    private val testEvent = TestEvent("1", "Test Message")
    private val now = LocalDateTime.now()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        // 设置通用模拟行为
        every { eventRecordRepository.create() } returns eventRecord
        every { eventRecord.init(any(), any(), any(), any(), any()) } just Runs
        every { eventRecord.markPersist(any()) } just Runs
        every { eventRecordRepository.save(any()) } just Runs
        every { applicationEventPublisher.publishEvent(any()) } just Runs
        every { eventPublisher.publish(any()) } just Runs

        every { integrationEventInterceptorManager.orderedIntegrationEventInterceptors } returns listOf(
            integrationEventInterceptor
        ).toSet()
        every { integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent } returns listOf(
            eventInterceptor
        ).toSet()

        every { integrationEventInterceptor.onAttach(any(), any()) } just Runs
        every { integrationEventInterceptor.onDetach(any()) } just Runs
        every { eventInterceptor.prePersist(any()) } just Runs
        every { eventInterceptor.postPersist(any()) } just Runs

        supervisor = DefaultIntegrationEventSupervisor(
            eventPublisher,
            eventRecordRepository,
            integrationEventInterceptorManager,
            applicationEventPublisher,
            svcName
        )
    }

    @AfterEach
    fun tearDown() {
        // 清理ThreadLocal
        DefaultIntegrationEventSupervisor.reset()
    }

    @Test
    fun `测试attach方法 - 成功附加事件`() {
        // 执行
        supervisor.attach(testEvent, now)

        // 验证
        verify { integrationEventInterceptor.onAttach(testEvent, now) }
    }

    @Test
    fun `测试attach方法 - 传入null事件时抛出异常`() {
        // 执行与验证
        val exception = assertThrows<IllegalArgumentException> {
            supervisor.attach(null, now)
        }

        assertEquals("事件负载不能为空", exception.message)
    }

    @Test
    fun `测试attach方法 - 传入非集成事件时抛出异常`() {
        // 执行与验证
        val nonIntegrationEvent = "Not an integration event"
        val exception = assertThrows<IllegalArgumentException> {
            supervisor.attach(nonIntegrationEvent, now)
        }

        assertEquals("事件类型必须为集成事件", exception.message)
    }

    @Test
    fun `测试detach方法 - 成功解除附加事件`() {
        // 准备
        supervisor.attach(testEvent, now)

        // 执行
        supervisor.detach(testEvent)

        // 验证
        verify { integrationEventInterceptor.onDetach(testEvent) }
    }

    @Test
    fun `测试release方法 - 成功释放事件`() {
        // 准备
        supervisor.attach(testEvent, now)

        // 执行
        supervisor.release()

        // 验证
        verifySequence {
            // attach调用
            integrationEventInterceptor.onAttach(testEvent, now)

            // release调用
            eventRecordRepository.create()
            eventRecord.init(testEvent, svcName, any(), any(), any())
            eventRecord.markPersist(true)
            eventInterceptor.prePersist(eventRecord)
            eventRecordRepository.save(eventRecord)
            eventInterceptor.postPersist(eventRecord)
            applicationEventPublisher.publishEvent(any<IntegrationEventAttachedTransactionCommittedEvent>())
        }
    }

    @Test
    fun `测试publish方法 - 成功发布事件`() {
        // 执行
        supervisor.publish(testEvent, now)

        // 验证
        verifySequence {
            eventRecordRepository.create()
            eventRecord.init(testEvent, svcName, now, any(), any())
            eventRecord.markPersist(true)
            eventInterceptor.prePersist(eventRecord)
            eventRecordRepository.save(eventRecord)
            eventInterceptor.postPersist(eventRecord)
            applicationEventPublisher.publishEvent(any<IntegrationEventAttachedTransactionCommittedEvent>())
        }
    }

    @Test
    fun `测试publish方法 - 传入null事件时抛出异常`() {
        // 执行与验证
        val exception = assertThrows<IllegalArgumentException> {
            supervisor.publish(null, now)
        }

        assertEquals("事件负载不能为空", exception.message)
    }

    @Test
    fun `测试onTransactionCommitted方法 - 成功处理事件`() {
        // 准备
        val eventList = listOf(eventRecord)
        val transactionEvent = IntegrationEventAttachedTransactionCommittedEvent(supervisor, eventList)

        // 执行
        supervisor.onTransactionCommitted(transactionEvent)

        // 验证
        verify { eventPublisher.publish(eventRecord) }
    }

    @Test
    fun `测试onTransactionCommitted方法 - 空事件列表不处理`() {
        // 准备
        val emptyEventList = emptyList<EventRecord>()
        val transactionEvent = IntegrationEventAttachedTransactionCommittedEvent(supervisor, emptyEventList)

        // 执行
        supervisor.onTransactionCommitted(transactionEvent)

        // 验证
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `测试getDeliverTime方法 - 返回已设置的时间`() {
        // 准备 - 使用反射访问protected方法
        val putDeliverTimeMethod = DefaultIntegrationEventSupervisor::class.java.getDeclaredMethod(
            "putDeliverTime",
            Any::class.java,
            LocalDateTime::class.java
        ).apply { isAccessible = true }

        val getDeliverTimeMethod = DefaultIntegrationEventSupervisor::class.java.getDeclaredMethod(
            "getDeliverTime",
            Any::class.java
        ).apply { isAccessible = true }

        // 执行
        putDeliverTimeMethod.invoke(supervisor, testEvent, now)
        val result = getDeliverTimeMethod.invoke(supervisor, testEvent) as LocalDateTime

        // 验证
        assertEquals(now, result)
    }

    @Test
    fun `测试getDeliverTime方法 - 未设置时返回当前时间`() {
        // 准备 - 使用反射访问protected方法
        val getDeliverTimeMethod = DefaultIntegrationEventSupervisor::class.java.getDeclaredMethod(
            "getDeliverTime",
            Any::class.java
        ).apply { isAccessible = true }

        // 执行
        val result = getDeliverTimeMethod.invoke(supervisor, testEvent) as LocalDateTime

        // 验证
        assertNotNull(result)
        // 由于时间可能有微小差异，只验证是否为当前时间的近似值
        assertTrue(result.isAfter(LocalDateTime.now().minusSeconds(1)))
        assertTrue(result.isBefore(LocalDateTime.now().plusSeconds(1)))
    }

    @Test
    fun `测试popEvents方法 - 返回并清除事件列表`() {
        // 准备
        supervisor.attach(testEvent, now)

        // 使用反射访问protected方法
        val popEventsMethod = DefaultIntegrationEventSupervisor::class.java.getDeclaredMethod("popEvents")
            .apply { isAccessible = true }

        // 执行
        val result = popEventsMethod.invoke(supervisor) as Set<*>

        // 验证
        assertEquals(1, result.size)
        assertTrue(result.contains(testEvent))

        // 再次调用应返回空集合
        val emptyResult = popEventsMethod.invoke(supervisor) as Set<*>
        assertEquals(0, emptyResult.size)
    }

    @Test
    fun `测试reset方法 - 清除所有ThreadLocal数据`() {
        // 准备
        supervisor.attach(testEvent, now)

        // 执行
        DefaultIntegrationEventSupervisor.reset()

        // 验证 - 使用反射访问protected方法
        val popEventsMethod = DefaultIntegrationEventSupervisor::class.java.getDeclaredMethod("popEvents")
            .apply { isAccessible = true }

        val emptyResult = popEventsMethod.invoke(supervisor) as Set<*>
        assertEquals(0, emptyResult.size)
    }
}
