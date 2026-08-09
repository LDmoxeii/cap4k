package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import io.mockk.*
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import org.springframework.messaging.Message

@DisplayName("RocketMQ集成事件发布器测试")
class RocketMqIntegrationEventPublisherTest {

    private val rocketMQTemplate: RocketMQTemplate = mockk()
    private val environment: Environment = mockk()
    private val publishCallback: IntegrationEventPublisher.PublishCallback = mockk()
    private val eventRecord: EventRecord = mockk()

    private lateinit var publisher: RocketMqIntegrationEventPublisher

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { environment.resolvePlaceholders(any()) } answers { firstArg() }
        every { publishCallback.onSuccess(any()) } just Runs
        every { publishCallback.onException(any(), any()) } just Runs
        publisher = RocketMqIntegrationEventPublisher(rocketMQTemplate, environment)
    }

    @Test
    @DisplayName("应该能够成功发布集成事件-简化版本")
    fun `should successfully publish integration event - simplified`() {
        // given - 使用已解析的topic避免静态方法调用
        val eventId = "test-event-id"
        val eventType = "resolved-topic" // 直接使用已解析的topic
        every { eventRecord.id } returns eventId
        every { eventRecord.type } returns eventType
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>())
        } answers {
            thirdArg<SendCallback>().onSuccess(mockk(relaxed = true))
        }

        // when - 执行发布操作
        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 1) { publishCallback.onSuccess(eventRecord) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    @Test
    @DisplayName("应该能够处理RocketMQ发送异常-简化版本")
    fun `should handle RocketMQ sending exception - simplified`() {
        // given
        val eventId = "test-event-id"
        val eventType = "resolved-topic"
        val eventMessage = mockk<Message<Any>>()
        val exception = RuntimeException("RocketMQ发送失败")

        every { eventRecord.id } returns eventId
        every { eventRecord.type } returns eventType
        every { eventRecord.message } returns eventMessage
        every { rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>()) } throws exception

        // when - 执行发布操作
        publisher.publish(eventRecord, envelope(), publishCallback)

        // then - 验证异常被处理，方法正常返回
        verify(exactly = 1) { publishCallback.onException(eventRecord, exception) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
    }

    @Test
    @DisplayName("RocketMQ 异步发送失败时只报告一次发布失败")
    fun `should report exactly one failure for asynchronous RocketMQ error`() {
        val eventId = "test-event-id"
        val eventType = "resolved-topic"
        val failure = RuntimeException("异步发送失败")

        every { eventRecord.id } returns eventId
        every { eventRecord.type } returns eventType
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>())
        } answers {
            thirdArg<SendCallback>().onException(failure)
            thirdArg<SendCallback>().onSuccess(mockk(relaxed = true))
        }

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
    }

    @Test
    @DisplayName("集成事件发送回调应该在成功时调用成功回调")
    fun `integration event send callback should call success callback on success`() {
        // given
        val eventId = "test-event-id"
        val sendResult = mockk<SendResult>()
        val msgId = "msg-123"

        every { eventRecord.id } returns eventId
        every { sendResult.msgId } returns msgId
        every { publishCallback.onSuccess(eventRecord) } just Runs

        val callback = RocketMqIntegrationEventPublisher.IntegrationEventSendCallback(
            eventRecord,
            IntegrationEventPublishCompletion(eventRecord, publishCallback),
        )

        // when
        callback.onSuccess(sendResult)

        // then
        verify { publishCallback.onSuccess(eventRecord) }
    }

    @Test
    @DisplayName("成功回调异常不应改写为发布失败")
    fun `integration event send callback should call exception callback when success callback throws`() {
        // given
        val eventId = "test-event-id"
        val sendResult = mockk<SendResult>()
        val msgId = "msg-123"
        val exception = RuntimeException("回调异常")

        every { eventRecord.id } returns eventId
        every { sendResult.msgId } returns msgId
        every { publishCallback.onSuccess(eventRecord) } throws exception
        every { publishCallback.onException(eventRecord, exception) } just Runs

        val callback = RocketMqIntegrationEventPublisher.IntegrationEventSendCallback(
            eventRecord,
            IntegrationEventPublishCompletion(eventRecord, publishCallback),
        )

        // when
        callback.onSuccess(sendResult)

        // then
        verify { publishCallback.onSuccess(eventRecord) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    @Test
    @DisplayName("集成事件发送回调应该在异常时调用异常回调")
    fun `integration event send callback should call exception callback on exception`() {
        // given
        val eventId = "test-event-id"
        val eventPayload = mapOf("key" to "value")
        val exception = RuntimeException("发送失败")

        every { eventRecord.id } returns eventId
        every { eventRecord.payload } returns eventPayload
        every { publishCallback.onException(eventRecord, exception) } just Runs

        val callback = RocketMqIntegrationEventPublisher.IntegrationEventSendCallback(
            eventRecord,
            IntegrationEventPublishCompletion(eventRecord, publishCallback),
        )

        // when
        callback.onException(exception)

        // then
        verify { publishCallback.onException(eventRecord, exception) }
    }

    @Test
    @DisplayName("集成事件发送回调应该处理异常回调中的异常")
    fun `integration event send callback should handle exception in exception callback`() {
        // given
        val eventId = "test-event-id"
        val eventPayload = mapOf("key" to "value")
        val originalException = RuntimeException("原始异常")
        val callbackException = RuntimeException("回调异常")

        every { eventRecord.id } returns eventId
        every { eventRecord.payload } returns eventPayload
        every { publishCallback.onException(eventRecord, originalException) } throws callbackException

        val callback = RocketMqIntegrationEventPublisher.IntegrationEventSendCallback(
            eventRecord,
            IntegrationEventPublishCompletion(eventRecord, publishCallback),
        )

        // when
        callback.onException(originalException)

        // then
        verify { publishCallback.onException(eventRecord, originalException) }
    }

    @Test
    @DisplayName("应该能够创建发送回调实例")
    fun `should be able to create send callback instance`() {
        // given & when
        val callback = RocketMqIntegrationEventPublisher.IntegrationEventSendCallback(
            eventRecord,
            IntegrationEventPublishCompletion(eventRecord, publishCallback),
        )

        // then
        assertNotNull(callback)
        assertTrue(callback is SendCallback)
    }

    private fun envelope(): IntegrationEventEnvelope = IntegrationEventEnvelope(
        eventId = "test-event-id",
        eventType = "resolved-topic",
        originService = "test-service",
        publishedAt = java.time.Instant.parse("2026-08-04T00:00:00Z"),
        deliveryAttempt = null,
        executionContext = emptyList(),
        payloadJson = "{\"key\":\"value\"}",
    )
}
