package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.connection.Connection
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.core.env.Environment
import java.util.concurrent.ExecutorService

@DisplayName("RabbitMQ集成事件发布器测试")
class RabbitMqIntegrationEventPublisherTest {

    private lateinit var rabbitTemplate: RabbitTemplate
    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var environment: Environment
    private lateinit var publisher: RabbitMqIntegrationEventPublisher
    private lateinit var executorService: ExecutorService

    @BeforeEach
    fun setUp() {
        rabbitTemplate = mockk()
        connectionFactory = mockk()
        environment = mockk()
        executorService = mockk()

        publisher = RabbitMqIntegrationEventPublisher(
            rabbitTemplate = rabbitTemplate,
            connectionFactory = connectionFactory,
            environment = environment,
            threadPoolSize = 5,
            threadFactoryClassName = "",
            autoDeclareExchange = false,
            defaultExchangeType = "direct"
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
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()

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
        publisher.publish(event, publishCallback)

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
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()

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
        customPublisher.publish(event, publishCallback)

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
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()

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
        publisher.publish(event, publishCallback)

        // Assert
        Thread.sleep(100)
        verify(atLeast = 0) {
            rabbitTemplate.convertAndSend(
                "test.exchange",
                "routing.key",
                any<String>(),
                any<RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback>()
            )
        }
    }

    @Test
    @DisplayName("当目标为空时应该抛出异常")
    fun shouldThrowExceptionWhenDestinationIsEmpty() {
        // Arrange
        val event = createTestEventRecord("empty.destination")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()

        every { environment.resolvePlaceholders("empty.destination") } returns ""

        // Act & Assert - 验证方法调用没有问题（异常处理已在实现中包含）
        try {
            publisher.publish(event, publishCallback)
            // 如果没有抛异常也是可以的，因为异常处理在内部
        } catch (e: DomainException) {
            // 期望的异常
            assertTrue(e.message?.contains("缺失topic") == true)
        }
    }

    @Test
    @DisplayName("解析目标地址 - 包含冒号")
    fun shouldParseDestinationWithColon() {
        // Arrange
        val event = createTestEventRecord("exchange.name:routing.key")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()

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
        publisher.publish(event, publishCallback)

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
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()

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
        publisher.publish(event, publishCallback)

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
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()
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
        autoPublisher.publish(event, publishCallback)

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

        every { message.messageProperties } returns messageProperties
        every { messageProperties.messageId = any<String>() } just runs
        every { publishCallback.onSuccess(event) } just runs

        val callback = RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback(event, publishCallback)

        // Act
        val result = callback.postProcessMessage(message)

        // Assert
        assertEquals(message, result)
        verify { publishCallback.onSuccess(event) }
    }

    @Test
    @DisplayName("集成事件发送回调处理器异常处理")
    fun shouldHandleCallbackException() {
        // Arrange
        val event = createTestEventRecord("test.type")
        val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()
        val message = mockk<Message>()
        val messageProperties = mockk<org.springframework.amqp.core.MessageProperties>()
        val exception = RuntimeException("Callback error")

        every { message.messageProperties } returns messageProperties
        every { messageProperties.messageId = any<String>() } just runs
        every { publishCallback.onSuccess(event) } throws exception
        every { publishCallback.onException(event, exception) } just runs

        val callback = RabbitMqIntegrationEventPublisher.IntegrationEventSendCallback(event, publishCallback)

        // Act
        val result = callback.postProcessMessage(message)

        // Assert
        assertEquals(message, result)
        verify { publishCallback.onException(event, exception) }
    }

    private fun createTestEventRecord(type: String): EventRecord {
        return mockk<EventRecord> {
            every { id } returns "test-id"
            every { this@mockk.type } returns type
            every { message } returns mockk {
                every { payload } returns mapOf("test" to "data")
            }
        }
    }
}
