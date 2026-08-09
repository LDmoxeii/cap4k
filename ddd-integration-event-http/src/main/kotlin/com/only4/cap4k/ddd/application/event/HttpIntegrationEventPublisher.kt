package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpCallbackTriggerCapability
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.misc.createFixedThreadPool
import com.only4.cap4k.ddd.core.share.misc.resolvePlaceholderWithCache
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import java.util.concurrent.Executor

/** HTTP experience-mode Integration Event publisher. */
class HttpIntegrationEventPublisher(
    private val subscriberRegister: HttpIntegrationEventSubscriberRegister,
    private val environment: Environment,
    private val threadPoolSize: Int = 10,
    private val threadFactoryClassName: String = "",
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
    private val executorOverride: Executor? = null,
    private val capabilityCaller: (IntegrationEventHttpCallbackTriggerCapability.Request) -> Unit = { request ->
        Mediator.capabilities.call<
            IntegrationEventHttpCallbackTriggerCapability.Request,
            IntegrationEventHttpCallbackTriggerCapability.Response
            >(request)
    },
) : IntegrationEventPublisher {

    private val log = LoggerFactory.getLogger(HttpIntegrationEventPublisher::class.java)

    private val executor: Executor by lazy {
        executorOverride ?: createFixedThreadPool(
            threadPoolSize,
            threadFactoryClassName,
            javaClass.classLoader,
        )
    }

    fun init() {
        executor
    }

    override fun publish(
        event: EventRecord,
        envelope: IntegrationEventEnvelope,
        publishCallback: IntegrationEventPublisher.PublishCallback,
    ) {
        val completion = IntegrationEventPublishCompletion(event, publishCallback)
        try {
            val destination = resolveDestination(event)
            val subscribers = subscriberRegister.subscribers(destination)
            if (subscribers.isEmpty()) {
                completion.failure(
                    IllegalStateException("No HTTP Integration Event subscriber for eventType=$destination"),
                )
                return
            }

            val envelopeJson = envelopeCodec.encode(envelope)
            executor.execute {
                runCatching {
                    subscribers.forEach { subscriber ->
                        capabilityCaller(
                            IntegrationEventHttpCallbackTriggerCapability.Request(
                                url = subscriber.callbackUrl,
                                uuid = envelope.eventId,
                                event = destination,
                                payload = null,
                                publishedAt = envelope.publishedAt,
                                envelopeJson = envelopeJson,
                            )
                        )
                    }
                    completion.success()
                }.onFailure { throwable ->
                    log.error("集成事件发布失败, ${event.id}", throwable)
                    completion.failure(throwable)
                }
            }
        } catch (throwable: Throwable) {
            log.error("集成事件发布失败, ${event.id}", throwable)
            completion.failure(throwable)
        }
    }

    private fun resolveDestination(event: EventRecord): String =
        resolvePlaceholderWithCache(event.type, environment).split("@")[0]
}
