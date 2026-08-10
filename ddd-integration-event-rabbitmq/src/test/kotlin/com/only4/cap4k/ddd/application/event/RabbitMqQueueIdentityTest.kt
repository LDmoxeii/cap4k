package com.only4.cap4k.ddd.application.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class RabbitMqQueueIdentityTest {
    @Test
    fun `queue identity is stable distinct readable and bounded`() {
        val applicationName = "媒体 worker / ${"x".repeat(80)}"
        val eventName = "content.published/版本-${"y".repeat(80)}"
        val queue = RabbitMqQueueIdentity.derive(applicationName, eventName)

        assertEquals(queue, RabbitMqQueueIdentity.derive(applicationName, eventName))
        assertNotEquals(queue, RabbitMqQueueIdentity.derive("$applicationName-2", eventName))
        assertNotEquals(queue, RabbitMqQueueIdentity.derive(applicationName, "$eventName-2"))
        assertTrue(queue.matches(Regex("cap4k\\.[A-Za-z0-9._-]+\\.[A-Za-z0-9._-]+\\.[0-9a-f]{64}")))
        assertTrue(queue.toByteArray(StandardCharsets.UTF_8).size < 255)

        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest((applicationName + '\u0000' + eventName).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertTrue(queue.endsWith(".$expectedHash"))
    }
}
