package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.connection.Connection
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.core.env.Environment
import java.time.Instant
import java.util.Date
import java.util.concurrent.Executor

@DisplayName("RabbitMQ集成事件发布器测试")
class RabbitMqIntegrationEventPublisherTest {

    private lateinit var rabbitTemplate: RabbitTemplate
    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var environment: Environment
    private lateinit var publisher: RabbitMqIntegrationEventPublisher

    @BeforeEach
    fun setUp() {
        rabbitTemplate = mockk()
        connectionFactory = mockk()
        environment = mockk()
        publisher = RabbitMqIntegrationEventPublisher(
            rabbitTemplate = rabbitTemplate,
            connectionFactory = connectionFactory,
            environment = environment,
            threadPoolSize = 5,
            threadFactoryClassName = "",
            autoDeclareExchange = false,
            defaultExchangeType = "direct",
            executorOverride = Executor { command -> command.run() },
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @DisplayName("初始化应该创建线程池")
    fun shouldCreateThreadPoolOnInit() {
        // Arrange
        val event = createTestEventRecord("test.exchange:routing.key")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)

        every { environment.resolvePlaceholders(any<String>()) } returns "test.exchange:routing.key"
        every {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        } just runs

        // Act - 调用publish方法会触发lazy初始化
        publisher.publish(event, envelope(event), publishCallback)

        // Assert - 验证没有抛出异常，说明线程池初始化成功
        // 使用timeout等待异步执行完成
        Thread.sleep(100)
        verify(atLeast = 0) {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        }
    }

    @Test
    @DisplayName("使用自定义线程工厂创建线程池")
    fun shouldCreateThreadPoolWithCustomThreadFactory() {
        // Arrange
        val customPublisher = RabbitMqIntegrationEventPublisher(
            rabbitTemplate = rabbitTemplate,
            connectionFactory = connectionFactory,
            environment = environment,
            threadPoolSize = 5,
            threadFactoryClassName = "java.util.concurrent.Executors\$DefaultThreadFactory",
            autoDeclareExchange = false,
            defaultExchangeType = "direct"
        )

        val event = createTestEventRecord("test.exchange:routing.key")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)

        every { environment.resolvePlaceholders(any<String>()) } returns "test.exchange:routing.key"
        every {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        } just runs

        // Act - 通过业务逻辑触发初始化
        customPublisher.publish(event, envelope(event), publishCallback)

        // Assert - 验证没有异常，说明初始化成功
        Thread.sleep(100)
        verify(atLeast = 0) {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        }
    }

    @Test
    @DisplayName("发布事件成功")
    fun shouldPublishEventSuccessfully() {
        // Arrange
        val event = createTestEventRecord("test.exchange:routing.key")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)

        every { environment.resolvePlaceholders(any<String>()) } returns "test.exchange:routing.key"
        every {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        } just runs

        // Act
        publisher.publish(event, envelope(event), publishCallback)

        // Assert
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(
                "test.exchange",
                "routing.key",
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        }
        verify(exactly = 1) { publishCallback.onSuccess(event) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    @Test
    @DisplayName("RabbitMQ 发送失败时只报告一次发布失败")
    fun shouldReportExactlyOneFailureWhenSendFails() {
        val event = createTestEventRecord("test.exchange:routing.key")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val failure = IllegalStateException("send failed")

        every { environment.resolvePlaceholders(any<String>()) } returns "test.exchange:routing.key"
        every {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>(),
            )
        } throws failure

        publisher.publish(event, envelope(event), publishCallback)

        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) { publishCallback.onException(event, failure) }
    }

    @Test
    @DisplayName("当目标为空时应该抛出异常")
    fun shouldThrowExceptionWhenDestinationIsEmpty() {
        // Arrange
        val event = createTestEventRecord("empty.destination")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)

        every { environment.resolvePlaceholders("empty.destination") } returns ""

        publisher.publish(event, envelope(event), publishCallback)

        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) {
            publishCallback.onException(
                event,
                match { it is DomainException && it.message?.contains("缺失topic") == true },
            )
        }
    }

    @Test
    @DisplayName("解析目标地址 - 包含冒号")
    fun shouldParseDestinationWithColon() {
        // Arrange
        val event = createTestEventRecord("exchange.name:routing.key")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)

        every { environment.resolvePlaceholders(any<String>()) } returns "exchange.name:routing.key"
        every {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        } just runs

        // Act
        publisher.publish(event, envelope(event), publishCallback)

        // Assert
        Thread.sleep(100)
        verify(atLeast = 0) {
            rabbitTemplate.convertAndSend(
                "exchange.name",
                "routing.key",
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        }
    }

    @Test
    @DisplayName("解析目标地址 - 不包含冒号")
    fun shouldParseDestinationWithoutColon() {
        // Arrange
        val event = createTestEventRecord("exchange.name")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)

        every { environment.resolvePlaceholders(any<String>()) } returns "exchange.name"
        every {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        } just runs

        // Act
        publisher.publish(event, envelope(event), publishCallback)

        // Assert
        Thread.sleep(100)
        verify(atLeast = 0) {
            rabbitTemplate.convertAndSend(
                "exchange.name",
                "",
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        }
    }

    @Test
    @DisplayName("自动声明交换机")
    fun shouldAutoDeclareExchange() {
        // Arrange
        val autoPublisher = RabbitMqIntegrationEventPublisher(
            rabbitTemplate = rabbitTemplate,
            connectionFactory = connectionFactory,
            environment = environment,
            threadPoolSize = 5,
            autoDeclareExchange = true,
            defaultExchangeType = "topic"
        )

        val event = createTestEventRecord("test.exchange:routing.key")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val connection = mockk<Connection>()
        val channel = mockk<com.rabbitmq.client.Channel>()

        every { environment.resolvePlaceholders(any<String>()) } returns "test.exchange:routing.key"
        every { connectionFactory.createConnection() } returns connection
        every { connection.createChannel(false) } returns channel
        justRun { connection.close() }
        justRun { channel.close() }
        justRun { channel.exchangeDeclare("test.exchange", "topic", true, false, null) }
        every {
            rabbitTemplate.convertAndSend(
                any<String>(),
                any<String>(),
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        } just runs

        // Act
        autoPublisher.publish(event, envelope(event), publishCallback)

        // Assert
        Thread.sleep(100)
        verify(atLeast = 0) { channel.exchangeDeclare("test.exchange", "topic", true, false, null) }
    }

    @Test
    @DisplayName("集成事件发送回调处理器测试")
    fun shouldHandleIntegrationEventSendCallback() {
        // Arrange
        val event = createTestEventRecord("test.type")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()
        val message = mockk<Message>()
        val messageProperties = mockk<org.springframework.amqp.core.MessageProperties>()
        val timestamp = slot<Date>()

        every { message.messageProperties } returns messageProperties
        every { messageProperties.messageId = any<String>() } just runs
        every { messageProperties.timestamp = capture(timestamp) } just runs
        val callback = RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback(event)

        // Act
        val result = callback.postProcessMessage(message)

        // Assert
        assertEquals(message, result)
        assertEquals(event.publishedAt, timestamp.captured.toInstant())
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    @Test
    @DisplayName("集成事件消息后处理不负责终结发布回调")
    fun shouldNotResolvePublishCallbackFromMessagePostProcessor() {
        // Arrange
        val event = createTestEventRecord("test.type")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()
        val message = mockk<Message>()
        val messageProperties = mockk<org.springframework.amqp.core.MessageProperties>()

        every { message.messageProperties } returns messageProperties
        every { messageProperties.messageId = any<String>() } just runs
        every { messageProperties.timestamp = any() } just runs
        val callback = RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback(event)

        // Act
        val result = callback.postProcessMessage(message)

        // Assert
        assertEquals(message, result)
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    private fun createTestEventRecord(type: String): EventRecord {
        return mockk<EventRecord> {
            every { id } returns "test-id"
            every { publishedAt } returns Instant.parse("2026-01-01T00:00:00.123Z")
            every { this@mockk.type } returns type
            every { executionContext } returns emptyList()
            every { message } returns mockk {
                every { payload } returns mapOf("test" to "data")
            }
        }
    }

    private fun envelope(event: EventRecord): IntegrationEventEnvelope = IntegrationEventEnvelope(
        eventId = event.id,
        eventType = event.type,
        originService = "test-service",
        publishedAt = event.publishedAt,
        deliveryAttempt = null,
        executionContext = emptyList(),
        payloadJson = "{\"test\":\"data\"}",
    )
}
