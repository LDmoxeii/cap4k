package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import io.mockk.*
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.common.message.MessageExt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.messaging.Message
import java.time.Instant

@DisplayName("RocketMQ集成事件订阅适配器测试")
class RocketMqIntegrationEventSubscriberAdapterTest {

    private var installedDeliveryContext: ReliableEventDeliveryContext? = null
    private val observingReliableEventDeliveryContextScopeManager = object : ReliableEventDeliveryContextScopeManager {
        override fun install(context: ReliableEventDeliveryContext): AutoCloseable {
            installedDeliveryContext = context
            return AutoCloseable { }
        }
        override fun suppress(): AutoCloseable = AutoCloseable { }
    }

    private val eventHandlerDispatcher: EventHandlerDispatcher = mockk()
    private val eventMessageInterceptor1: EventMessageInterceptor = mockk()
    private val eventMessageInterceptor2: EventMessageInterceptor = mockk()
    private val consumerGroupResolver = RocketMqConsumerGroupResolver()

    private val applicationName = "test-app"
    private val defaultNameSrv = "localhost:9876"
    private val msgCharset = "UTF-8"
    private val eventTypeCatalog = object : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(TestEventPayload::class.java)
    }

    private lateinit var adapter: RocketMqIntegrationEventSubscriberAdapter
    private lateinit var stateReporter: RecordingReporter

    @BeforeEach
    fun setup() {
        clearPlaceholderCache()
        clearAllMocks()
        installedDeliveryContext = null
        stateReporter = RecordingReporter()

        adapter = RocketMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = eventHandlerDispatcher,
            eventMessageInterceptors = listOf(eventMessageInterceptor1, eventMessageInterceptor2),
            routeResolver = routeResolver(),
            consumerGroupResolver = consumerGroupResolver,
            eventTypeCatalog = eventTypeCatalog,
            applicationName = applicationName,
            defaultNameSrv = defaultNameSrv,
            msgCharset = msgCharset,
            stateReporter = stateReporter,
            reliableEventDeliveryContextScopeManager = observingReliableEventDeliveryContextScopeManager
        )
    }

    @Test
    @DisplayName("应该能够创建默认的MQ消费者-基本测试")
    fun `should create default MQ consumer - basic test`() {
        // when - 直接测试有注解的类，只验证类能正确识别
        val testClass = TestIntegrationEvent::class.java
        val annotation = testClass.getAnnotation(IntegrationEvent::class.java)

        // then - 验证注解存在且值正确
        assertNotNull(annotation)
        assertEquals("test-topic", annotation.value)
    }

    @Test
    @DisplayName("应该能够处理消息消费成功场景")
    fun `should handle message consumption success scenario`() {
        // given
        val messageExt = mockk<MessageExt>()
        val context = mockk<ConsumeConcurrentlyContext>()
        val testEvent = TestEventPayload("test", "123")

        every { messageExt.msgId } returns "msg-123"
        every { messageExt.body } returns canonicalBody(testEvent)
        every { messageExt.properties } returns mapOf(
            "key" to "value",
            "cap4k-event-id" to "event-123",
            "cap4k-timestamp" to "1000",
        )
        every { messageExt.reconsumeTimes } returns 0
        every { eventHandlerDispatcher.dispatch(any()) } just Runs
        every { eventMessageInterceptor1.preSubscribe(any<Message<*>>()) } just Runs
        every { eventMessageInterceptor1.postSubscribe(any<Message<*>>()) } just Runs
        every { eventMessageInterceptor2.preSubscribe(any<Message<*>>()) } just Runs
        every { eventMessageInterceptor2.postSubscribe(any<Message<*>>()) } just Runs

        // 设置拦截器Order模拟
        mockkStatic("org.springframework.core.annotation.OrderUtils")
        every {
            org.springframework.core.annotation.OrderUtils.getOrder(
                eventMessageInterceptor1.javaClass,
                Ordered.LOWEST_PRECEDENCE
            )
        } returns 1
        every {
            org.springframework.core.annotation.OrderUtils.getOrder(
                eventMessageInterceptor2.javaClass,
                Ordered.LOWEST_PRECEDENCE
            )
        } returns 2

        // 通过反射调用私有方法来测试消息处理
        val method = adapter.javaClass.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            List::class.java,
            ConsumeConcurrentlyContext::class.java
        )
        method.isAccessible = true

        // when
        val result = method.invoke(adapter, TestEventPayload::class.java, listOf(messageExt), context)

        // then
        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, result)
        verify { eventHandlerDispatcher.dispatch(testEvent) }
        verify { eventMessageInterceptor1.preSubscribe(any<Message<*>>()) }
        verify { eventMessageInterceptor1.postSubscribe(any<Message<*>>()) }
        verify { eventMessageInterceptor2.preSubscribe(any<Message<*>>()) }
        verify { eventMessageInterceptor2.postSubscribe(any<Message<*>>()) }
        val deliveryContext = requireNotNull(installedDeliveryContext)
        assertEquals("event-123", deliveryContext.eventId)
        assertEquals("test-topic", deliveryContext.eventName)
        assertEquals(java.time.Instant.ofEpochMilli(1_000), deliveryContext.publishedAt)
        assertEquals(2, deliveryContext.attempt)
        assertEquals(ReliableEventRedeliveryHint.FIRST, deliveryContext.redeliveryHint)
        assertEquals(
            RuntimeProviderState.HEALTHY,
            stateReporter.lastState,
        )
    }

    @Test
    @DisplayName("应该能够处理消息消费异常场景")
    fun `should handle message consumption exception scenario`() {
        // given
        val messageExt = mockk<MessageExt>()
        val context = mockk<ConsumeConcurrentlyContext>()
        every { messageExt.msgId } returns "msg-123"
        every { messageExt.body } returns "invalid-json".toByteArray()
        every { messageExt.properties } returns emptyMap()

        // 通过反射调用私有方法
        val method = adapter.javaClass.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            List::class.java,
            ConsumeConcurrentlyContext::class.java
        )
        method.isAccessible = true

        // when
        var result: Any? = null
        val logs = captureFormattedLogs(RocketMqIntegrationEventSubscriberAdapter::class.java) {
            result = method.invoke(adapter, TestEventPayload::class.java, listOf(messageExt), context)
        }.joinToString("\n")

        // then
        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, result)
        assertFalse(logs.contains("invalid-json"))
        assertFalse(logs.contains(defaultNameSrv))
        assertFalse(logs.contains("content:published"))
    }

    @Test
    @DisplayName("应该能够处理没有拦截器的消息消费")
    fun `should handle message consumption without interceptors`() {
        // given
        val adapterWithoutInterceptors = RocketMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = eventHandlerDispatcher,
            eventMessageInterceptors = emptyList(),
            routeResolver = routeResolver(),
            consumerGroupResolver = consumerGroupResolver,
            eventTypeCatalog = eventTypeCatalog,
            applicationName = applicationName,
            defaultNameSrv = defaultNameSrv,
            msgCharset = msgCharset,
            stateReporter = stateReporter,
            reliableEventDeliveryContextScopeManager = observingReliableEventDeliveryContextScopeManager
        )

        val messageExt = mockk<MessageExt>()
        val context = mockk<ConsumeConcurrentlyContext>()
        val testEvent = TestEventPayload("test", "123")

        every { messageExt.msgId } returns "msg-123"
        every { messageExt.body } returns canonicalBody(testEvent)
        every { messageExt.properties } returns mapOf(
            "cap4k-event-id" to "event-123",
            "cap4k-timestamp" to "1000",
        )
        every { messageExt.reconsumeTimes } returns 0
        every { eventHandlerDispatcher.dispatch(testEvent) } just Runs
        // 通过反射调用私有方法
        val method = adapterWithoutInterceptors.javaClass.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            List::class.java,
            ConsumeConcurrentlyContext::class.java
        )
        method.isAccessible = true

        // when
        val result =
            method.invoke(adapterWithoutInterceptors, TestEventPayload::class.java, listOf(messageExt), context)

        // then
        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, result)
        verify { eventHandlerDispatcher.dispatch(testEvent) }
    }

    // 测试用的集成事件类
    @IntegrationEvent(value = "test-topic")
    private class TestIntegrationEvent

    // 测试用的事件载荷类
    @IntegrationEvent(value = "test-topic")
    private data class TestEventPayload(val name: String, val value: String)

    private fun canonicalBody(payload: TestEventPayload): ByteArray =
        IntegrationEventEnvelopeCodec().encode(
            IntegrationEventEnvelope(
                eventId = "event-123",
                eventType = "test-topic",
                originService = "test-source",
                publishedAt = java.time.Instant.ofEpochMilli(1_000),
                deliveryAttempt = 2,
                executionContext = emptyList(),
                payloadJson = RuntimeJson.write(payload),
            )
        ).toByteArray()

    private fun routeResolver() = StaticIntegrationEventRouteResolver(
        mapOf("test-topic" to RocketMqIntegrationEventRoute("content", "published")),
        "rocketmq",
    )

    private fun clearPlaceholderCache() {
        val field = Class.forName("com.only4.cap4k.ddd.core.share.misc.TextUtils")
            .getDeclaredField("resolvePlaceholderCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    private class RecordingReporter : RuntimeProviderStateReporter {
        override val providerId: String = RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY
        var lastState: RuntimeProviderState? = null

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            lastState = state
        }

        override fun close() = Unit
    }

    // 测试用的拦截器类
    @Order(1)
    private class TestInterceptor1 : EventMessageInterceptor {
        override fun initPublish(message: Message<*>) {}
        override fun prePublish(message: Message<*>) {}
        override fun postPublish(message: Message<*>) {}
        override fun preSubscribe(message: Message<*>) {}
        override fun postSubscribe(message: Message<*>) {}
    }

    @Order(2)
    private class TestInterceptor2 : EventMessageInterceptor {
        override fun initPublish(message: Message<*>) {}
        override fun prePublish(message: Message<*>) {}
        override fun postPublish(message: Message<*>) {}
        override fun preSubscribe(message: Message<*>) {}
        override fun postSubscribe(message: Message<*>) {}
    }
}
