package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.env.Environment
import java.time.Instant
import java.util.concurrent.Executor

@ExtendWith(MockKExtension::class)
@DisplayName("HTTP集成事件发布器测试")
class HttpIntegrationEventPublisherTest {

    @MockK
    private lateinit var subscriberRegister: HttpIntegrationEventSubscriberRegister

    @MockK
    private lateinit var environment: Environment

    @MockK
    private lateinit var publishCallback: IntegrationEventPublisher.PublishCallback

    private lateinit var publisher: HttpIntegrationEventPublisher

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        publisher = HttpIntegrationEventPublisher(
            subscriberRegister = subscriberRegister,
            environment = environment,
            threadPoolSize = 2,
            executorOverride = Executor { command -> command.run() },
            capabilityCaller = {},
        )
        publisher.init()
    }

    @Test
    @DisplayName("初始化成功测试")
    fun `should initialize successfully`() {
        // Arrange & Act - 初始化在setUp中完成

        // Assert - 没有异常抛出即为成功
        assertTrue(true)
    }

    @Test
    @DisplayName("没有订阅者时报告发布失败")
    fun `should report publication failure when no subscribers exist`() {
        // Arrange
        val eventRecord = createMockEventRecord("test-event", "user.created")

        // 模拟环境变量解析
        every { environment.resolvePlaceholders("user.created") } returns "user.created"
        every { subscriberRegister.subscribers("user.created") } returns emptyList()
        every { publishCallback.onSuccess(any()) } just runs
        every { publishCallback.onException(any(), any()) } just runs

        // Act
        publisher.publish(eventRecord, envelope(eventRecord), publishCallback)

        // Assert - 没有任何发送目标意味着本次可靠投递没有发出，应进入重试
        verify(exactly = 1) { subscriberRegister.subscribers("user.created") }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) {
            publishCallback.onException(
                eventRecord,
                match { it is IllegalStateException && it.message?.contains("user.created") == true },
            )
        }
    }

    @Test
    @DisplayName("有订阅者时应处理发布逻辑")
    fun `should handle publish logic when subscribers exist`() {
        // Arrange
        val eventRecord = createMockEventRecord("test-event", "user.created")
        val subscribers = listOf(
            HttpIntegrationEventSubscriberRegister.SubscriberInfo(
                event = "user.created",
                subscriber = "test-service",
                callbackUrl = "http://localhost:8080/webhook"
            )
        )

        every { environment.resolvePlaceholders("user.created") } returns "user.created"
        every { subscriberRegister.subscribers("user.created") } returns subscribers
        every { publishCallback.onSuccess(any()) } just runs
        every { publishCallback.onException(any(), any()) } just runs

        // Act
        publisher.publish(eventRecord, envelope(eventRecord), publishCallback)

        // Assert
        verify(exactly = 1) { subscriberRegister.subscribers("user.created") }
        verify(exactly = 1) { publishCallback.onSuccess(eventRecord) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    @Test
    @DisplayName("HTTP Capability 失败时只报告一次发布失败")
    fun `should report exactly one failure when HTTP capability fails`() {
        val eventRecord = createMockEventRecord("test-event", "user.created")
        val failure = IllegalStateException("subscriber unavailable")
        val subscribers = listOf(
            HttpIntegrationEventSubscriberRegister.SubscriberInfo(
                event = "user.created",
                subscriber = "test-service",
                callbackUrl = "http://localhost:8080/webhook",
            )
        )
        val failingPublisher = HttpIntegrationEventPublisher(
            subscriberRegister = subscriberRegister,
            environment = environment,
            executorOverride = Executor { command -> command.run() },
            capabilityCaller = { throw failure },
        )

        every { environment.resolvePlaceholders("user.created") } returns "user.created"
        every { subscriberRegister.subscribers("user.created") } returns subscribers
        every { publishCallback.onSuccess(any()) } just runs
        every { publishCallback.onException(any(), any()) } just runs

        failingPublisher.publish(eventRecord, envelope(eventRecord), publishCallback)

        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
    }

    private fun createMockEventRecord(id: String, type: String): EventRecord {
        return mockk<EventRecord>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.type } returns type
            every { payload } returns mapOf("userId" to "123", "action" to "created")
            every { publishedAt } returns Instant.parse("2026-08-04T00:00:00Z")
        }
    }

    private fun envelope(event: EventRecord): IntegrationEventEnvelope = IntegrationEventEnvelope(
        eventId = event.id,
        eventType = event.type,
        originService = "test-service",
        publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
        deliveryAttempt = null,
        executionContext = emptyList(),
        payloadJson = "{\"action\":\"created\",\"userId\":\"123\"}",
    )
}
