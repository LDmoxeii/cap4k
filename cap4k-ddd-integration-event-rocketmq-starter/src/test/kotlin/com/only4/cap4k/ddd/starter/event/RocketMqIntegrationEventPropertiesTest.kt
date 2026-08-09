package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.application.event.RocketMqIntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.application.event.configure.RocketMqIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.application.event.configure.RocketMqIntegrationEventRouteProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class RocketMqIntegrationEventPropertiesTest {
    @Test
    fun `typed bracket-key routes bind and resolve explicit topic and tag`() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "cap4k.ddd.integration-event.rocketmq.routes[content.published].topic" to "content",
                "cap4k.ddd.integration-event.rocketmq.routes[content.published].tag" to "published",
            )
        )
        val properties = Binder(source)
            .bind(
                "cap4k.ddd.integration-event.rocketmq",
                Bindable.of(RocketMqIntegrationEventAdapterProperties::class.java),
            )
            .get()

        val route = RocketMqIntegrationEventAutoConfiguration()
            .rocketMqIntegrationEventRouteResolver(properties)
            .resolve("content.published")

        assertEquals("content", route.topic)
        assertEquals("published", route.tag)
        assertEquals("content:published", route.destination)
    }

    @Test
    fun `route catalog rejects malformed topology during bean creation`() {
        val properties = RocketMqIntegrationEventAdapterProperties(
            linkedMapOf(
                "content.published" to RocketMqIntegrationEventRouteProperties(
                    topic = " ",
                    tag = "published",
                )
            )
        )

        assertThrows<IllegalArgumentException> {
            RocketMqIntegrationEventAutoConfiguration().rocketMqIntegrationEventRouteResolver(properties)
        }
    }
}
