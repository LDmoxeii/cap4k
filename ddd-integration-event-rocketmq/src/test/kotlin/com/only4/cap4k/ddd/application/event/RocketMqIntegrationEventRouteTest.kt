package com.only4.cap4k.ddd.application.event

import org.apache.rocketmq.client.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class RocketMqIntegrationEventRouteTest {
    @Test
    fun `route requires explicit valid topic and non-blank tag`() {
        assertEquals("content:published", RocketMqIntegrationEventRoute("content", "published").destination)
        assertThrows<IllegalArgumentException> { RocketMqIntegrationEventRoute(" ", "published") }
        assertThrows<IllegalArgumentException> { RocketMqIntegrationEventRoute("invalid.topic", "published") }
        assertThrows<IllegalArgumentException> { RocketMqIntegrationEventRoute("content", " ") }
    }

    @Test
    fun `consumer group is stable independent collision resistant and RocketMQ legal`() {
        val resolver = RocketMqConsumerGroupResolver()
        val first = resolver.resolve("content-service", "content.published")
        val same = resolver.resolve("content-service", "content.published")
        val otherApplication = resolver.resolve("media-service", "content.published")
        val otherEvent = resolver.resolve("content-service", "content.archived")
        val sanitizedCollisionA = resolver.resolve("service.a", "event")
        val sanitizedCollisionB = resolver.resolve("service-a", "event")
        val longUnicode = resolver.resolve("服务".repeat(200), "事件".repeat(200))

        assertEquals(first, same)
        assertNotEquals(first, otherApplication)
        assertNotEquals(first, otherEvent)
        assertNotEquals(sanitizedCollisionA, sanitizedCollisionB)
        assertTrue(longUnicode.length <= 255)
        assertDoesNotThrow { Validators.checkGroup(first) }
        assertDoesNotThrow { Validators.checkGroup(longUnicode) }
        assertThrows<IllegalArgumentException> { resolver.resolve(" ", "event") }
        assertThrows<IllegalArgumentException> { resolver.resolve("service", " ") }
    }
}
