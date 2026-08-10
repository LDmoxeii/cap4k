package com.only4.cap4k.ddd.application.event

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Runtime-owned, deterministic RabbitMQ queue identity for one application/event pair. */
object RabbitMqQueueIdentity {
    fun derive(applicationName: String, eventName: String): String {
        require(applicationName.isNotBlank()) { "RabbitMQ application name must not be blank" }
        require(eventName.isNotBlank()) { "RabbitMQ Integration Event name must not be blank" }
        val hashInput = (applicationName + '\u0000' + eventName).toByteArray(StandardCharsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(hashInput)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "cap4k.${slug(applicationName, 48)}.${slug(eventName, 48)}.$hash"
    }

    private fun slug(value: String, maxLength: Int): String {
        val projected = buildString(value.length) {
            value.forEach { character ->
                append(
                    if (character in 'a'..'z' || character in 'A'..'Z' ||
                        character in '0'..'9' || character == '.' || character == '_' || character == '-'
                    ) character else '-'
                )
            }
        }.trim('-').ifBlank { "event" }
        return projected.take(maxLength).trim('-').ifBlank { "event" }
    }
}
