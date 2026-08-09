package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.misc.createFixedThreadPool
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

/** HTTP experience-mode Integration Event publisher. */
class HttpIntegrationEventPublisher(
    private val routeResolver: IntegrationEventRouteResolver<String>,
    private val threadPoolSize: Int = 10,
    private val threadFactoryClassName: String = "",
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
    private val executorOverride: Executor? = null,
    private val httpPoster: (String, String) -> Unit = defaultHttpPoster(),
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
            val destination = routeResolver.resolve(event.type)
            val envelopeJson = envelopeCodec.encode(envelope)
            executor.execute {
                runCatching {
                    httpPoster(destination.trimEnd('/') + RECEIVE_PATH, envelopeJson)
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

    companion object {
        const val RECEIVE_PATH = "/cap4k/integration-events"

        private fun defaultHttpPoster(): (String, String) -> Unit {
            val restTemplate = RestTemplate()
            return { url, envelopeJson ->
                val response = restTemplate.postForEntity(
                    url,
                    HttpEntity(
                        envelopeJson.toByteArray(StandardCharsets.UTF_8),
                        HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
                    ),
                    String::class.java,
                )
                check(response.statusCode.is2xxSuccessful) {
                    "HTTP Integration Event handoff failed with status=${response.statusCode.value()}"
                }
            }
        }
    }
}
