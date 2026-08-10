package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.misc.createFixedThreadPool
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Duration
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** RabbitMQ adapter for the shared Integration Event envelope. */
class RabbitMqIntegrationEventPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val connectionFactory: ConnectionFactory,
    private val routeResolver: IntegrationEventRouteResolver<RabbitMqIntegrationEventRoute>,
    private val topologyManager: RabbitMqTopologyManager,
    private val stateReporter: RuntimeProviderStateReporter,
    private val threadPoolSize: Int,
    private val confirmTimeout: Duration,
    private val threadFactoryClassName: String = "",
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
    private val executorOverride: Executor? = null,
) : IntegrationEventPublisher {
    private val executor: Executor by lazy {
        executorOverride ?: createFixedThreadPool(threadPoolSize, threadFactoryClassName, this::class.java.classLoader)
    }

    fun init() {
        require(threadPoolSize > 0) { "RabbitMQ publish thread pool size must be positive" }
        require(!confirmTimeout.isZero && !confirmTimeout.isNegative) {
            "RabbitMQ publisher confirm timeout must be positive"
        }
        val publisherFactory = checkNotNull(connectionFactory.publisherConnectionFactory) {
            "RabbitMQ Integration Event publisher connection factory is unavailable"
        }
        check(publisherFactory.isPublisherConfirms && !publisherFactory.isSimplePublisherConfirms) {
            "RabbitMQ Integration Event transport requires correlated publisher confirms"
        }
        check(publisherFactory.isPublisherReturns) {
            "RabbitMQ Integration Event transport requires publisher returns"
        }
        rabbitTemplate.setMandatory(true)
        executor
        stateReporter.report(RuntimeProviderState.RECOVERING, "publisher-enrolled")
    }

    override fun publish(
        event: EventRecord,
        envelope: IntegrationEventEnvelope,
        publishCallback: IntegrationEventPublisher.PublishCallback,
    ) {
        val completion = IntegrationEventPublishCompletion(event, publishCallback)
        try {
            val route = routeResolver.resolve(event.type)
            topologyManager.registerExchange(route)
            val body = envelopeCodec.encode(envelope)
            executor.execute { publishConfirmed(event, route, body, completion) }
        } catch (failure: Throwable) {
            fail(event, completion, failure, category(failure))
        }
    }

    private fun publishConfirmed(
        event: EventRecord,
        route: RabbitMqIntegrationEventRoute,
        body: String,
        completion: IntegrationEventPublishCompletion,
    ) {
        try {
            val correlation = CorrelationData("${event.id}:${UUID.randomUUID()}")
            rabbitTemplate.convertAndSend(
                route.exchange,
                route.routingKey,
                body,
                IntegrationEventMessagePostProcessor(event),
                correlation,
            )
            val confirm = correlation.future.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!confirm.isAck) {
                throw RabbitMqPublishFailure("publisher-nack", event.id)
            }
            if (correlation.returned != null) {
                throw RabbitMqPublishFailure("unroutable-return", event.id)
            }
            stateReporter.report(RuntimeProviderState.HEALTHY, "publisher-confirm-ack")
            completion.success()
        } catch (failure: Throwable) {
            fail(event, completion, failure, category(failure))
        }
    }

    private fun fail(
        event: EventRecord,
        completion: IntegrationEventPublishCompletion,
        failure: Throwable,
        category: String,
    ) {
        stateReporter.report(RuntimeProviderState.DEGRADED, category)
        log.warn(
            "RabbitMQ Integration Event publish failed: eventId={}, eventName={}, category={}, exceptionType={}",
            event.id,
            event.type,
            category,
            failure::class.java.name,
        )
        completion.failure(failure)
    }

    private fun category(failure: Throwable): String = when (failure) {
        is RabbitMqPublishFailure -> failure.category
        is java.util.concurrent.TimeoutException -> "publisher-confirm-timeout"
        is java.util.concurrent.ExecutionException -> "publisher-confirm-exception"
        is java.util.concurrent.RejectedExecutionException -> "publisher-executor-rejected"
        else -> "publisher-exception"
    }

    class IntegrationEventMessagePostProcessor(
        private val event: EventRecord,
    ) : MessagePostProcessor {
        override fun postProcessMessage(message: Message): Message {
            message.messageProperties.messageId = event.id
            message.messageProperties.timestamp = Date.from(event.publishedAt)
            return message
        }
    }

    private class RabbitMqPublishFailure(
        val category: String,
        eventId: String,
    ) : IllegalStateException("RabbitMQ Integration Event publish failed: eventId=$eventId, category=$category")

    private companion object {
        private val log = LoggerFactory.getLogger(RabbitMqIntegrationEventPublisher::class.java)
    }
}
