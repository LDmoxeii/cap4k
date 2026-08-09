package com.only4.cap4k.ddd.application.event

import org.apache.rocketmq.client.Validators
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class RocketMqIntegrationEventRoute(
    val topic: String,
    val tag: String,
) {
    init {
        require(topic.isNotBlank()) { "RocketMQ route topic must not be blank" }
        require(tag.isNotBlank()) { "RocketMQ route tag must not be blank" }
        runCatching { Validators.checkTopic(topic) }
            .getOrElse { throw IllegalArgumentException("Invalid RocketMQ route topic '$topic'", it) }
    }

    val destination: String get() = "$topic:$tag"
}

class RocketMqConsumerGroupResolver {
    fun resolve(applicationName: String, eventName: String): String {
        require(applicationName.isNotBlank()) { "applicationName must not be blank" }
        require(eventName.isNotBlank()) { "eventName must not be blank" }

        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$applicationName\u0000$eventName".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val group = "cap4k-${slug(applicationName, 48)}-${slug(eventName, 80)}-$hash"
        runCatching { Validators.checkGroup(group) }
            .getOrElse { throw IllegalArgumentException("Cannot derive a valid RocketMQ Consumer Group", it) }
        return group
    }

    private fun slug(value: String, maxLength: Int): String = value
        .map { char -> if (char.isAsciiGroupCharacter()) char else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "x" }
        .take(maxLength)

    private fun Char.isAsciiGroupCharacter(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_' || this == '%' || this == '|'
}
