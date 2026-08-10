package com.only4.cap4k.ddd.application.event

import java.nio.charset.StandardCharsets

/** RabbitMQ topology selected by a stable Integration Event name. */
data class RabbitMqIntegrationEventRoute(
    val exchange: String,
    val routingKey: String,
) {
    init {
        require(exchange.isNotBlank()) { "RabbitMQ Integration Event exchange must not be blank" }
        require(routingKey.isNotBlank()) { "RabbitMQ Integration Event routing key must not be blank" }
        require('\u0000' !in exchange && exchange.toByteArray(StandardCharsets.UTF_8).size <= MAX_SHORT_STRING_BYTES) {
            "RabbitMQ Integration Event exchange must be a valid AMQP short string"
        }
        require('\u0000' !in routingKey && routingKey.toByteArray(StandardCharsets.UTF_8).size <= MAX_SHORT_STRING_BYTES) {
            "RabbitMQ Integration Event routing key must be a valid AMQP short string"
        }
    }

    private companion object {
        const val MAX_SHORT_STRING_BYTES = 255
    }
}
