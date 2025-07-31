package com.only4.cap4k.ddd.application.event

import io.mockk.mockk
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.MQPushConsumer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("RocketMQ集成事件配置接口测试")
class RocketMqIntegrationEventConfigureTest {

    @Test
    @DisplayName("应该能够为指定的集成事件类返回MQ消费者")
    fun `should return MQ consumer for specified integration event class`() {
        // given
        val integrationEventClass = TestIntegrationEvent::class.java
        val expectedConsumer = mockk<DefaultMQPushConsumer>()
        val configure = object : RocketMqIntegrationEventConfigure {
            override fun get(integrationEventClass: Class<*>): MQPushConsumer? {
                return if (integrationEventClass == TestIntegrationEvent::class.java) {
                    expectedConsumer
                } else {
                    null
                }
            }
        }

        // when
        val actualConsumer = configure.get(integrationEventClass)

        // then
        assertEquals(expectedConsumer, actualConsumer)
    }

    @Test
    @DisplayName("应该能够为不支持的集成事件类返回null")
    fun `should return null for unsupported integration event class`() {
        // given
        val integrationEventClass = UnsupportedIntegrationEvent::class.java
        val configure = object : RocketMqIntegrationEventConfigure {
            override fun get(integrationEventClass: Class<*>): MQPushConsumer? {
                return if (integrationEventClass == TestIntegrationEvent::class.java) {
                    mockk<DefaultMQPushConsumer>()
                } else {
                    null
                }
            }
        }

        // when
        val actualConsumer = configure.get(integrationEventClass)

        // then
        assertNull(actualConsumer)
    }

    @Test
    @DisplayName("应该能够处理多种不同的集成事件类")
    fun `should handle multiple different integration event classes`() {
        // given
        val consumer1 = mockk<DefaultMQPushConsumer>()
        val consumer2 = mockk<DefaultMQPushConsumer>()
        val configure = object : RocketMqIntegrationEventConfigure {
            override fun get(integrationEventClass: Class<*>): MQPushConsumer? {
                return when (integrationEventClass) {
                    TestIntegrationEvent::class.java -> consumer1
                    AnotherIntegrationEvent::class.java -> consumer2
                    else -> null
                }
            }
        }

        // when & then
        assertEquals(consumer1, configure.get(TestIntegrationEvent::class.java))
        assertEquals(consumer2, configure.get(AnotherIntegrationEvent::class.java))
        assertNull(configure.get(UnsupportedIntegrationEvent::class.java))
    }

    // 测试用的集成事件类
    private class TestIntegrationEvent
    private class AnotherIntegrationEvent
    private class UnsupportedIntegrationEvent
}