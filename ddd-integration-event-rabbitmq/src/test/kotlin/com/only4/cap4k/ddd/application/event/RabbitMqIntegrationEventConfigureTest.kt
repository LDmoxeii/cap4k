package com.only4.cap4k.ddd.application.event

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer

@DisplayName("RabbitMQ集成事件配置接口测试")
class RabbitMqIntegrationEventConfigureTest {

    @Test
    @DisplayName("应该能正确获取指定集成事件类的消息监听器容器")
    fun shouldReturnMessageListenerContainerForGivenEventClass() {
        // Arrange
        val configure = mockk<RabbitMqIntegrationEventConfigure>()
        val mockContainer = mockk<SimpleMessageListenerContainer>()
        val eventClass = TestEvent::class.java

        every { configure.get(eventClass) } returns mockContainer

        // Act
        val result = configure.get(eventClass)

        // Assert
        assertEquals(mockContainer, result)
    }

    @Test
    @DisplayName("当集成事件类未配置时应该返回null")
    fun shouldReturnNullWhenEventClassNotConfigured() {
        // Arrange
        val configure = mockk<RabbitMqIntegrationEventConfigure>()
        val eventClass = UnknownEvent::class.java

        every { configure.get(eventClass) } returns null

        // Act
        val result = configure.get(eventClass)

        // Assert
        assertNull(result)
    }

    @Test
    @DisplayName("应该能处理不同的集成事件类")
    fun shouldHandleDifferentEventClasses() {
        // Arrange
        val configure = mockk<RabbitMqIntegrationEventConfigure>()
        val container1 = mockk<SimpleMessageListenerContainer>()
        val container2 = mockk<SimpleMessageListenerContainer>()
        val eventClass1 = TestEvent::class.java
        val eventClass2 = AnotherTestEvent::class.java

        every { configure.get(eventClass1) } returns container1
        every { configure.get(eventClass2) } returns container2

        // Act & Assert
        assertEquals(container1, configure.get(eventClass1))
        assertEquals(container2, configure.get(eventClass2))
    }

    // 测试用的事件类
    private class TestEvent
    private class AnotherTestEvent
    private class UnknownEvent
}
