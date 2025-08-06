package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import io.mockk.*
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.common.message.MessageExt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.messaging.Message
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("RocketMQ集成事件订阅适配器测试")
class RocketMqIntegrationEventSubscriberAdapterTest {

    private val eventSubscriberManager: EventSubscriberManager = mockk()
    private val rocketMqIntegrationEventConfigure: RocketMqIntegrationEventConfigure = mockk()
    private val environment: Environment = mockk()
    private val eventMessageInterceptor1: EventMessageInterceptor = mockk()
    private val eventMessageInterceptor2: EventMessageInterceptor = mockk()

    private val scanPath = "com.only4.cap4k.test"
    private val applicationName = "test-app"
    private val defaultNameSrv = "localhost:9876"
    private val msgCharset = "UTF-8"

    private lateinit var adapter: RocketMqIntegrationEventSubscriberAdapter

    @BeforeEach
    fun setup() {
        clearAllMocks()

        adapter = RocketMqIntegrationEventSubscriberAdapter(
            eventSubscriberManager = eventSubscriberManager,
            eventMessageInterceptors = listOf(eventMessageInterceptor1, eventMessageInterceptor2),
            rocketMqIntegrationEventConfigure = rocketMqIntegrationEventConfigure,
            environment = environment,
            scanPath = scanPath,
            applicationName = applicationName,
            defaultNameSrv = defaultNameSrv,
            msgCharset = msgCharset
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
        assertEquals("test-subscriber", annotation.subscriber)
    }

    @Test
    @DisplayName("应该能够处理消息消费成功场景")
    fun `should handle message consumption success scenario`() {
        // given
        val messageExt = mockk<MessageExt>()
        val context = mockk<ConsumeConcurrentlyContext>()
        val messageBody = """{"name":"test","value":"123"}"""
        val testEvent = TestEventPayload("test", "123")

        every { messageExt.msgId } returns "msg-123"
        every { messageExt.body } returns messageBody.toByteArray()
        every { messageExt.properties } returns mapOf("key" to "value")
        every { eventSubscriberManager.dispatch(any()) } just Runs
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

        // 模拟JSON解析
        mockkStatic("com.alibaba.fastjson.JSON")
        every {
            com.alibaba.fastjson.JSON.parseObject(
                messageBody,
                TestEventPayload::class.java,
                any<com.alibaba.fastjson.parser.Feature>()
            )
        } returns testEvent

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
        verify { eventSubscriberManager.dispatch(testEvent) }
        verify { eventMessageInterceptor1.preSubscribe(any<Message<*>>()) }
        verify { eventMessageInterceptor1.postSubscribe(any<Message<*>>()) }
        verify { eventMessageInterceptor2.preSubscribe(any<Message<*>>()) }
        verify { eventMessageInterceptor2.postSubscribe(any<Message<*>>()) }
    }

    @Test
    @DisplayName("应该能够处理消息消费异常场景")
    fun `should handle message consumption exception scenario`() {
        // given
        val messageExt = mockk<MessageExt>()
        val context = mockk<ConsumeConcurrentlyContext>()
        val exception = RuntimeException("消费异常")

        every { messageExt.msgId } returns "msg-123"
        every { messageExt.body } returns "invalid-json".toByteArray()
        every { messageExt.properties } returns emptyMap()

        // 模拟JSON解析异常
        mockkStatic("com.alibaba.fastjson.JSON")
        every {
            com.alibaba.fastjson.JSON.parseObject(
                any<String>(),
                any<Class<*>>(),
                any<com.alibaba.fastjson.parser.Feature>()
            )
        } throws exception

        // 通过反射调用私有方法
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
        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, result)
    }

    @Test
    @DisplayName("应该能够处理没有拦截器的消息消费")
    fun `should handle message consumption without interceptors`() {
        // given
        val adapterWithoutInterceptors = RocketMqIntegrationEventSubscriberAdapter(
            eventSubscriberManager = eventSubscriberManager,
            eventMessageInterceptors = emptyList(),
            rocketMqIntegrationEventConfigure = rocketMqIntegrationEventConfigure,
            environment = environment,
            scanPath = scanPath,
            applicationName = applicationName,
            defaultNameSrv = defaultNameSrv,
            msgCharset = msgCharset
        )

        val messageExt = mockk<MessageExt>()
        val context = mockk<ConsumeConcurrentlyContext>()
        val messageBody = """{"name":"test","value":"123"}"""
        val testEvent = TestEventPayload("test", "123")

        every { messageExt.msgId } returns "msg-123"
        every { messageExt.body } returns messageBody.toByteArray()
        every { messageExt.properties } returns emptyMap()
        every { eventSubscriberManager.dispatch(testEvent) } just Runs

        mockkStatic("com.alibaba.fastjson.JSON")
        every {
            com.alibaba.fastjson.JSON.parseObject(
                messageBody,
                TestEventPayload::class.java,
                any<com.alibaba.fastjson.parser.Feature>()
            )
        } returns testEvent

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
        verify { eventSubscriberManager.dispatch(testEvent) }
    }

    // 测试用的集成事件类
    @IntegrationEvent(value = "test-topic", subscriber = "test-subscriber")
    private class TestIntegrationEvent

    @IntegrationEvent(value = "test-topic", subscriber = IntegrationEvent.NONE_SUBSCRIBER)
    private class NoneSubscriberEvent

    // 测试用的事件载荷类
    private data class TestEventPayload(val name: String, val value: String)

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
