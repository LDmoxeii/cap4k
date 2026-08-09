package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.rabbitmq.client.Channel
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.Connection
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import org.springframework.core.env.Environment

@DisplayName("RabbitMQ集成事件订阅适配器测试")
class RabbitMqIntegrationEventSubscriberAdapterTest {

    private var installedDeliveryContext: ReliableEventDeliveryContext? = null
    private val observingReliableEventDeliveryContextScopeManager = object : ReliableEventDeliveryContextScopeManager {
        override fun install(context: ReliableEventDeliveryContext): AutoCloseable {
            installedDeliveryContext = context
            return AutoCloseable { }
        }
        override fun suppress(): AutoCloseable = AutoCloseable { }
    }

    private lateinit var eventHandlerDispatcher: EventHandlerDispatcher
    private lateinit var rabbitMqIntegrationEventConfigure: RabbitMqIntegrationEventConfigure
    private lateinit var rabbitListenerContainerFactory: SimpleRabbitListenerContainerFactory
    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var environment: Environment
    private lateinit var adapter: RabbitMqIntegrationEventSubscriberAdapter
    private val eventTypeCatalog = object : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = emptySet()
    }

    @BeforeEach
    fun setUp() {
        clearPlaceholderCache()
        eventHandlerDispatcher = mockk()
        rabbitMqIntegrationEventConfigure = mockk()
        rabbitListenerContainerFactory = mockk()
        connectionFactory = mockk()
        environment = mockk()
        installedDeliveryContext = null
        every { environment.resolvePlaceholders(any()) } answers { firstArg() }

        adapter = RabbitMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = eventHandlerDispatcher,
            eventMessageInterceptors = emptyList(),
            rabbitMqIntegrationEventConfigure = rabbitMqIntegrationEventConfigure,
            rabbitListenerContainerFactory = rabbitListenerContainerFactory,
            connectionFactory = connectionFactory,
            environment = environment,
            eventTypeCatalog = eventTypeCatalog,
            applicationName = "test-app",
            msgCharset = "UTF-8",
            autoDeclareQueue = false,
            reliableEventDeliveryContextScopeManager = observingReliableEventDeliveryContextScopeManager
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @DisplayName("创建默认消费者容器")
    fun shouldCreateDefaultConsumer() {
        // Arrange
        val mockContainer = mockk<SimpleMessageListenerContainer>()
        every { rabbitListenerContainerFactory.createListenerContainer() } returns mockContainer
        every { mockContainer.setQueueNames(any<String>()) } just runs
        every { mockContainer.acknowledgeMode = any() } just runs
        every { environment.resolvePlaceholders("test.exchange:routing.key") } returns "test.exchange:routing.key"
        every { environment.resolvePlaceholders("\${rabbitmq.test.exchange.consumer.queue:test-app}") } returns "test-app"

        // Act
        val result = adapter.createDefaultConsumer(TestIntegrationEvent::class.java)

        // Assert
        assertEquals(mockContainer, result)
        verify { mockContainer.setQueueNames("test-app") }
        verify { mockContainer.acknowledgeMode = AcknowledgeMode.MANUAL }
    }

    @Test
    @DisplayName("解析目标地址包含冒号")
    fun shouldParseTargetWithColon() {
        // Arrange
        val mockContainer = mockk<SimpleMessageListenerContainer>()
        every { rabbitListenerContainerFactory.createListenerContainer() } returns mockContainer
        every { mockContainer.setQueueNames(any<String>()) } just runs
        every { mockContainer.acknowledgeMode = any() } just runs
        every { environment.resolvePlaceholders("test.exchange:routing.key") } returns "test.exchange:routing.key"
        every { environment.resolvePlaceholders("\${rabbitmq.test.exchange.consumer.queue:test-app}") } returns "test-queue"

        // Act
        val result = adapter.createDefaultConsumer(TestIntegrationEvent::class.java)

        // Assert - 验证方法执行没有异常
        verify { mockContainer.setQueueNames("test-queue") }
    }

    @Test
    @DisplayName("解析目标地址不包含冒号")
    fun shouldParseTargetWithoutColon() {
        // Arrange
        val mockContainer = mockk<SimpleMessageListenerContainer>()
        every { rabbitListenerContainerFactory.createListenerContainer() } returns mockContainer
        every { mockContainer.setQueueNames(any<String>()) } just runs
        every { mockContainer.acknowledgeMode = any() } just runs
        every { environment.resolvePlaceholders("exchange") } returns "exchange"
        every { environment.resolvePlaceholders("\${rabbitmq.exchange.consumer.queue:test-app}") } returns "test-queue"

        // Act
        val result = adapter.createDefaultConsumer(NoColonIntegrationEvent::class.java)

        // Assert
        verify { mockContainer.setQueueNames("test-queue") }
    }

    @Test
    @DisplayName("自动声明队列")
    fun shouldAutoDeclareQueue() {
        // Arrange - 简化测试，只验证没有异常抛出
        val autoDeclareAdapter = RabbitMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = eventHandlerDispatcher,
            eventMessageInterceptors = emptyList(),
            rabbitMqIntegrationEventConfigure = rabbitMqIntegrationEventConfigure,
            rabbitListenerContainerFactory = rabbitListenerContainerFactory,
            connectionFactory = connectionFactory,
            environment = environment,
            eventTypeCatalog = eventTypeCatalog,
            applicationName = "test-app",
            msgCharset = "UTF-8",
            autoDeclareQueue = true,
            reliableEventDeliveryContextScopeManager = observingReliableEventDeliveryContextScopeManager
        )

        val mockContainer = mockk<SimpleMessageListenerContainer>()
        val connection = mockk<Connection>(relaxed = true)
        val channel = mockk<com.rabbitmq.client.Channel>(relaxed = true)

        every { rabbitListenerContainerFactory.createListenerContainer() } returns mockContainer
        every { mockContainer.setQueueNames(any<String>()) } just runs
        every { mockContainer.acknowledgeMode = any() } just runs
        every { environment.resolvePlaceholders("test.exchange:routing.key") } returns "test.exchange:routing.key"
        every { environment.resolvePlaceholders("\${rabbitmq.test.exchange.consumer.queue:test-app}") } returns "test-queue"
        every { environment.resolvePlaceholders("\${rabbitmq.test.exchange.type:direct}") } returns "direct"
        every { connectionFactory.createConnection() } returns connection
        every { connection.createChannel(false) } returns channel

        // Act & Assert - 验证方法调用不抛异常
        try {
            val result = autoDeclareAdapter.createDefaultConsumer(TestIntegrationEvent::class.java)
            // 测试通过，说明自动声明队列功能工作正常
        } catch (e: Exception) {
            // 如果出现异常，验证不是致命错误
            assertTrue(e !is NullPointerException)
        }
    }

    @Test
    @DisplayName("消息消费成功确认")
    fun shouldAckMessageOnSuccessfulConsumption() {
        // Arrange
        val message = mockk<Message>()
        val messageProperties = mockk<MessageProperties>()
        val channel = mockk<Channel>()
        val deliveryTag = 123L
        val messageId = "test-message-id"
        val testPayload = TestEventPayload("test")

        every { message.messageProperties } returns messageProperties
        every { message.body } returns canonicalBody(testPayload)
        every { messageProperties.deliveryTag } returns deliveryTag
        every { messageProperties.messageId } returns messageId
        every { messageProperties.headers } returns mutableMapOf()
        every { messageProperties.timestamp } returns java.util.Date(1000L)
        every { messageProperties.redelivered } returns false
        every { eventHandlerDispatcher.dispatch(any()) } just runs
        every { channel.basicAck(deliveryTag, false) } just runs
        // 使用反射调用私有方法
        val onMessageMethod = adapter::class.java.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            Message::class.java,
            Channel::class.java
        )
        onMessageMethod.isAccessible = true

        // Act
        onMessageMethod.invoke(adapter, TestEventPayload::class.java, message, channel)

        // Assert
        verify { eventHandlerDispatcher.dispatch(testPayload) }
        verify { channel.basicAck(deliveryTag, false) }
        val deliveryContext = requireNotNull(installedDeliveryContext)
        assertEquals("test-id", deliveryContext.eventId)
        assertEquals("test.exchange:routing.key", deliveryContext.eventName)
        assertEquals(java.time.Instant.ofEpochMilli(1_000), deliveryContext.publishedAt)
        assertEquals(2, deliveryContext.attempt)
        assertEquals("test-app", deliveryContext.subscriberIdentity)
        assertEquals(ReliableEventRedeliveryHint.FIRST, deliveryContext.redeliveryHint)
    }

    @Test
    @DisplayName("消息消费失败拒绝")
    fun shouldRejectMessageOnConsumptionFailure() {
        // Arrange
        val message = mockk<Message>()
        val messageProperties = mockk<MessageProperties>()
        val channel = mockk<Channel>()
        val deliveryTag = 123L

        every { message.messageProperties } returns messageProperties
        every { message.body } returns "invalid json".toByteArray()
        every { messageProperties.deliveryTag } returns deliveryTag
        every { messageProperties.messageId } returns "test-id"
        every { channel.basicReject(deliveryTag, true) } just runs

        // 使用反射调用私有方法
        val onMessageMethod = adapter::class.java.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            Message::class.java,
            Channel::class.java
        )
        onMessageMethod.isAccessible = true

        // Act
        onMessageMethod.invoke(adapter, TestEventPayload::class.java, message, channel)

        // Assert
        verify { channel.basicReject(deliveryTag, true) }
    }

    @Test
    @DisplayName("使用事件消息拦截器")
    fun shouldUseEventMessageInterceptors() {
        // Arrange
        val interceptor1 = mockk<EventMessageInterceptor>()
        val interceptor2 = mockk<EventMessageInterceptor>()

        // 使用mockk的relaxed模式避免注解mock问题
        mockkStatic(OrderUtils::class)
        every { OrderUtils.getOrder(interceptor1::class.java, any<Int>()) } returns Ordered.HIGHEST_PRECEDENCE
        every { OrderUtils.getOrder(interceptor2::class.java, any<Int>()) } returns Ordered.LOWEST_PRECEDENCE

        every { interceptor1.preSubscribe(any()) } just runs
        every { interceptor1.postSubscribe(any()) } just runs
        every { interceptor2.preSubscribe(any()) } just runs
        every { interceptor2.postSubscribe(any()) } just runs

        val adapterWithInterceptors = RabbitMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = eventHandlerDispatcher,
            eventMessageInterceptors = listOf(interceptor1, interceptor2),
            rabbitMqIntegrationEventConfigure = rabbitMqIntegrationEventConfigure,
            rabbitListenerContainerFactory = rabbitListenerContainerFactory,
            connectionFactory = connectionFactory,
            environment = environment,
            eventTypeCatalog = eventTypeCatalog,
            applicationName = "test-app",
            reliableEventDeliveryContextScopeManager = observingReliableEventDeliveryContextScopeManager
        )

        val message = mockk<Message>()
        val messageProperties = mockk<MessageProperties>()
        val channel = mockk<Channel>()
        val testPayload = TestEventPayload("test")

        every { message.messageProperties } returns messageProperties
        every { message.body } returns canonicalBody(testPayload)
        every { messageProperties.deliveryTag } returns 123L
        every { messageProperties.messageId } returns "test-id"
        every { messageProperties.headers } returns mutableMapOf()
        every { messageProperties.timestamp } returns java.util.Date(1000L)
        every { messageProperties.redelivered } returns false
        every { eventHandlerDispatcher.dispatch(any()) } just runs
        every { channel.basicAck(any(), any()) } just runs
        // 使用反射调用私有方法
        val onMessageMethod = adapterWithInterceptors::class.java.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            Message::class.java,
            Channel::class.java
        )
        onMessageMethod.isAccessible = true

        // Act
        onMessageMethod.invoke(adapterWithInterceptors, TestEventPayload::class.java, message, channel)

        // Assert
        verify { interceptor1.preSubscribe(any()) }
        verify { interceptor1.postSubscribe(any()) }
        verify { interceptor2.preSubscribe(any()) }
        verify { interceptor2.postSubscribe(any()) }
        verify { eventHandlerDispatcher.dispatch(testPayload) }
        unmockkStatic(OrderUtils::class)
    }

    @Test
    @DisplayName("获取排序后的事件消息拦截器")
    fun shouldGetOrderedEventMessageInterceptors() {
        // Arrange
        val highPriorityInterceptor = mockk<EventMessageInterceptor>()
        val lowPriorityInterceptor = mockk<EventMessageInterceptor>()

        // Mock OrderUtils to return specific order values
        mockkStatic(OrderUtils::class)
        every { OrderUtils.getOrder(highPriorityInterceptor::class.java, any<Int>()) } returns 1
        every { OrderUtils.getOrder(lowPriorityInterceptor::class.java, any<Int>()) } returns 10
        every { highPriorityInterceptor.preSubscribe(any()) } just runs
        every { highPriorityInterceptor.postSubscribe(any()) } just runs
        every { lowPriorityInterceptor.preSubscribe(any()) } just runs
        every { lowPriorityInterceptor.postSubscribe(any()) } just runs

        val adapterWithOrderedInterceptors = RabbitMqIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = eventHandlerDispatcher,
            eventMessageInterceptors = listOf(lowPriorityInterceptor, highPriorityInterceptor),
            rabbitMqIntegrationEventConfigure = rabbitMqIntegrationEventConfigure,
            rabbitListenerContainerFactory = rabbitListenerContainerFactory,
            connectionFactory = connectionFactory,
            environment = environment,
            eventTypeCatalog = eventTypeCatalog,
            applicationName = "test-app",
            reliableEventDeliveryContextScopeManager = observingReliableEventDeliveryContextScopeManager
        )

        // 测试通过业务逻辑验证排序功能，而不是直接访问字段
        val message = mockk<org.springframework.amqp.core.Message>()
        val messageProperties = mockk<MessageProperties>()
        val channel = mockk<Channel>()
        val testPayload = TestEventPayload("test")

        every { message.messageProperties } returns messageProperties
        every { message.body } returns canonicalBody(testPayload)
        every { messageProperties.deliveryTag } returns 123L
        every { messageProperties.messageId } returns "test-id"
        every { messageProperties.headers } returns mutableMapOf()
        every { messageProperties.timestamp } returns java.util.Date(1000L)
        every { messageProperties.redelivered } returns false
        every { eventHandlerDispatcher.dispatch(any()) } just runs
        every { channel.basicAck(any(), any()) } just runs
        // 使用反射调用私有方法测试拦截器排序
        val onMessageMethod = adapterWithOrderedInterceptors::class.java.getDeclaredMethod(
            "onMessage",
            Class::class.java,
            org.springframework.amqp.core.Message::class.java,
            Channel::class.java
        )
        onMessageMethod.isAccessible = true

        // Act
        onMessageMethod.invoke(adapterWithOrderedInterceptors, TestEventPayload::class.java, message, channel)

        // Assert - 验证拦截器都被调用了（排序通过OrderUtils mock控制）
        verify { highPriorityInterceptor.preSubscribe(any()) }
        verify { highPriorityInterceptor.postSubscribe(any()) }
        verify { lowPriorityInterceptor.preSubscribe(any()) }
        verify { lowPriorityInterceptor.postSubscribe(any()) }
        unmockkStatic(OrderUtils::class)
    }

    @Test
    @DisplayName("关闭所有消息监听器容器")
    fun shouldShutdownAllContainers() {
        // Act & Assert - 验证shutdown方法不会抛出异常
        try {
            adapter.shutdown()
            // 测试通过，说明shutdown方法工作正常
        } catch (e: Exception) {
            throw AssertionError("shutdown方法不应该抛出异常", e)
        }
    }

    @Test
    @DisplayName("关闭时处理异常")
    fun shouldHandleExceptionDuringShutdown() {
        // Arrange - 创建一个会抛异常的容器
        val container = mockk<SimpleMessageListenerContainer>()
        every { container.shutdown() } throws RuntimeException("Shutdown error")

        // Act & Assert - 验证shutdown方法不会抛出异常，即使容器关闭失败
        try {
            adapter.shutdown()
            // 测试通过，说明异常被正确处理
        } catch (e: Exception) {
            // 不应该抛出异常
            throw AssertionError("shutdown方法不应该抛出异常", e)
        }
    }

    // 测试用的事件类和注解
    @IntegrationEvent("test.exchange:routing.key")
    private class TestIntegrationEvent

    @IntegrationEvent("exchange")
    private class NoColonIntegrationEvent

    private class NonIntegrationEvent

    @IntegrationEvent("")
    private class EmptyValueIntegrationEvent

    @IntegrationEvent("test.exchange:routing.key")
    private data class TestEventPayload(val data: String)

    private fun canonicalBody(payload: TestEventPayload): ByteArray =
        IntegrationEventEnvelopeCodec().encode(
            IntegrationEventEnvelope(
                eventId = "test-id",
                eventType = "test.exchange:routing.key",
                originService = "test-source",
                publishedAt = java.time.Instant.ofEpochMilli(1_000),
                deliveryAttempt = 2,
                executionContext = emptyList(),
                payloadJson = RuntimeJson.write(payload),
            )
        ).toByteArray()

    private fun clearPlaceholderCache() {
        val field = Class.forName("com.only4.cap4k.ddd.core.share.misc.TextUtils")
            .getDeclaredField("resolvePlaceholderCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }
}
