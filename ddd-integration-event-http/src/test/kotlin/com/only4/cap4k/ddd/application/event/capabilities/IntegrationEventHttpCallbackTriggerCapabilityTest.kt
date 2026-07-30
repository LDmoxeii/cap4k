package com.only4.cap4k.ddd.application.event.capabilities

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
@DisplayName("集成事件 HTTP 回调 Capability 测试")
class IntegrationEventHttpCallbackTriggerCapabilityTest {
    @MockK
    private lateinit var restTemplate: RestTemplate

    private lateinit var handler: IntegrationEventHttpCallbackTriggerCapability.Handler

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        handler = IntegrationEventHttpCallbackTriggerCapability.Handler(
            restTemplate = restTemplate,
            eventParamName = "event",
            eventIdParamName = "eventId",
        )
    }

    @Test
    fun `successfully invokes HTTP callback capability`() {
        val request = IntegrationEventHttpCallbackTriggerCapability.Request(
            url = "http://localhost:8080/webhook",
            uuid = "event-123",
            event = "user.created",
            payload = mapOf("userId" to "user-456"),
        )
        val expectedResponse = HttpIntegrationEventSubscriberAdapter.OperationResponse<String>(
            success = true,
            message = "处理成功",
        )
        every {
            restTemplate.postForEntity(
                any<String>(),
                any(),
                eq(HttpIntegrationEventSubscriberAdapter.OperationResponse::class.java),
                any<Map<String, Any>>(),
            )
        } returns ResponseEntity.ok(expectedResponse) as
            ResponseEntity<HttpIntegrationEventSubscriberAdapter.OperationResponse<*>>

        assertTrue(handler.call(request).success)
    }
}
