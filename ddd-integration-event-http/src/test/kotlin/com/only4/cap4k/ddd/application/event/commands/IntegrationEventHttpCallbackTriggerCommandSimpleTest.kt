package com.only4.cap4k.ddd.application.event.commands

import com.only4.cap4k.ddd.application.event.HttpIntegrationEventSubscriberAdapter
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

@ExtendWith(MockKExtension::class)
@DisplayName("集成事件HTTP回调触发命令简单测试")
class IntegrationEventHttpCallbackTriggerCommandSimpleTest {

    @MockK
    private lateinit var restTemplate: RestTemplate

    private lateinit var handler: IntegrationEventHttpCallbackTriggerCommand.Handler

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        handler = IntegrationEventHttpCallbackTriggerCommand.Handler(
            restTemplate = restTemplate,
            eventParamName = "event",
            eventIdParamName = "eventId"
        )
    }

    @Test
    @DisplayName("成功触发HTTP回调")
    fun `should trigger HTTP callback successfully`() {
        // Arrange
        val request = IntegrationEventHttpCallbackTriggerCommand.Request(
            url = "http://localhost:8080/webhook",
            uuid = "event-123",
            event = "user.created",
            payload = mapOf("userId" to "user-456")
        )

        val expectedResponse = HttpIntegrationEventSubscriberAdapter.OperationResponse<String>(
            success = true,
            message = "处理成功"
        )

        val responseEntity = ResponseEntity.ok(expectedResponse)
        every {
            restTemplate.postForEntity(
                any<String>(),
                any(),
                eq(HttpIntegrationEventSubscriberAdapter.OperationResponse::class.java),
                any<Map<String, Any>>()
            )
        } returns responseEntity as ResponseEntity<HttpIntegrationEventSubscriberAdapter.OperationResponse<*>>

        // Act
        val result = handler.exec(request)

        // Assert
        assertTrue(result.success)
    }
}
