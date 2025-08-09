package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
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
            threadPoolSize = 2
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
    @DisplayName("没有订阅者时不调用命令发送")
    fun `should not send commands when no subscribers exist`() {
        // Arrange
        val eventRecord = createMockEventRecord("test-event", "user.created")

        // 模拟环境变量解析
        every { environment.resolvePlaceholders("user.created") } returns "user.created"
        every { subscriberRegister.subscribers("user.created") } returns emptyList()
        every { publishCallback.onSuccess(any()) } just runs
        every { publishCallback.onException(any(), any()) } just runs

        // Act
        publisher.publish(eventRecord, publishCallback)

        // Assert - 验证没有调用订阅者相关操作
        verify(exactly = 1) { subscriberRegister.subscribers("user.created") }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
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
        publisher.publish(eventRecord, publishCallback)

        // Assert - 验证调用了订阅者查询
        verify(exactly = 1) { subscriberRegister.subscribers("user.created") }
        // 注意：由于异步执行和没有真实的Mediator，我们无法验证回调的调用
        // 但至少验证了基本的订阅者查询逻辑
    }

    private fun createMockEventRecord(id: String, type: String): EventRecord {
        return mockk<EventRecord>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.type } returns type
            every { payload } returns mapOf("userId" to "123", "action" to "created")
        }
    }
}
