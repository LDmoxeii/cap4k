package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Binding.DestinationType
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.AmqpException
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns only repeatable RabbitMQ exchange/queue/binding declarations.
 * It has no retry records; RabbitAdmin re-declares manual declarations after reconnect.
 */
class RabbitMqTopologyManager(
    private val admin: AmqpAdmin,
    exchangeType: String,
    private val stateReporter: RuntimeProviderStateReporter,
) {
    private val exchangeType = exchangeType.trim().lowercase().also {
        require(it in SUPPORTED_EXCHANGE_TYPES) {
            "Unsupported RabbitMQ exchange type '$exchangeType'"
        }
    }
    private val declarations = ConcurrentHashMap<String, Declaration>()
    private val queueRoutes = ConcurrentHashMap<String, RabbitMqIntegrationEventRoute>()

    fun registerExchange(route: RabbitMqIntegrationEventRoute) {
        val declaration = Declaration(route, null)
        declarations[declaration.key] = declaration
        declare(declaration)
    }

    fun register(route: RabbitMqIntegrationEventRoute, queue: String) {
        require(queue.isNotBlank()) { "RabbitMQ Integration Event queue must not be blank" }
        val existingRoute = queueRoutes.putIfAbsent(queue, route)
        check(existingRoute == null || existingRoute == route) {
            "RabbitMQ queue '$queue' is already registered with contradictory topology"
        }
        val declaration = Declaration(route, queue)
        declarations[declaration.key] = declaration
        declare(declaration)
    }

    fun declareAll() {
        declarations.values.forEach(::declare)
    }

    private fun declare(declaration: Declaration) {
        try {
            admin.declareExchange(exchange(declaration.route.exchange))
            declaration.queue?.let { queue ->
                admin.declareQueue(Queue(queue, true, false, false))
                admin.declareBinding(
                    Binding(
                        queue,
                        DestinationType.QUEUE,
                        declaration.route.exchange,
                        declaration.route.routingKey,
                        emptyMap(),
                    )
                )
            }
            stateReporter.report(RuntimeProviderState.HEALTHY, "topology-declared")
        } catch (failure: AmqpException) {
            if (!RabbitMqFailureClassifier.isTemporaryUnavailability(failure)) throw failure
            stateReporter.report(RuntimeProviderState.DEGRADED, "topology-unavailable")
            log.debug(
                "RabbitMQ topology declaration unavailable for exchange={}, queue={}, category={}",
                declaration.route.exchange,
                declaration.queue ?: "<exchange-only>",
                failure::class.java.simpleName,
            )
        }
    }

    private fun exchange(name: String): Exchange = when (exchangeType) {
        "direct" -> DirectExchange(name, true, false)
        "topic" -> TopicExchange(name, true, false)
        "fanout" -> FanoutExchange(name, true, false)
        else -> error("Unsupported RabbitMQ exchange type '$exchangeType'")
    }

    private data class Declaration(
        val route: RabbitMqIntegrationEventRoute,
        val queue: String?,
    ) {
        val key: String = "${route.exchange}\u0000${route.routingKey}\u0000${queue ?: "<exchange>"}"
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RabbitMqTopologyManager::class.java)
        private val SUPPORTED_EXCHANGE_TYPES = setOf("direct", "topic", "fanout")
    }
}
