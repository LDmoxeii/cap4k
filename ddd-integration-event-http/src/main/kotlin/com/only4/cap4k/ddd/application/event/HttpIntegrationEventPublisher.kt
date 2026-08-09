package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.share.misc.createFixedThreadPool
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/** HTTP experience-mode Integration Event publisher. */
class HttpIntegrationEventPublisher(
    private val routeResolver: IntegrationEventRouteResolver<URI>,
    private val threadPoolSize: Int = 10,
    private val threadFactoryClassName: String = "",
    private val envelopeCodec: IntegrationEventEnvelopeCodec = IntegrationEventEnvelopeCodec(),
    private val executorOverride: Executor? = null,
    private val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    private val responseTimeout: Duration = DEFAULT_RESPONSE_TIMEOUT,
    private val providerState: RuntimeProviderStateReporter,
    private val httpPoster: (URI, String) -> Unit = defaultHttpPoster(connectTimeout, responseTimeout),
) : IntegrationEventPublisher, AutoCloseable {

    private val log = LoggerFactory.getLogger(HttpIntegrationEventPublisher::class.java)

    private val executorDelegate = lazy {
        executorOverride ?: createFixedThreadPool(
            threadPoolSize,
            threadFactoryClassName,
            javaClass.classLoader,
        )
    }
    private val executor: Executor
        get() = executorDelegate.value

    init {
        require(threadPoolSize > 0) { "HTTP Integration Event publish thread pool size must be positive" }
        require(!connectTimeout.isZero && !connectTimeout.isNegative) {
            "HTTP Integration Event connect timeout must be positive"
        }
        require(!responseTimeout.isZero && !responseTimeout.isNegative) {
            "HTTP Integration Event response timeout must be positive"
        }
    }

    fun init() {
        executor
    }

    override fun close() {
        if (executorOverride == null && executorDelegate.isInitialized()) {
            (executorDelegate.value as? ExecutorService)?.shutdown()
        }
    }

    override fun publish(
        event: EventRecord,
        envelope: IntegrationEventEnvelope,
        publishCallback: IntegrationEventPublisher.PublishCallback,
    ) {
        val completion = IntegrationEventPublishCompletion(event, publishCallback)
        val destination = try {
            HttpIntegrationEventRouteResolver.endpoint(routeResolver.resolve(event.type))
        } catch (throwable: Throwable) {
            fail(event, completion, HttpIntegrationEventFailureCategory.ROUTE, throwable)
            return
        }
        val envelopeJson = try {
            envelopeCodec.encode(envelope)
        } catch (throwable: Throwable) {
            fail(event, completion, HttpIntegrationEventFailureCategory.ENVELOPE, throwable)
            return
        }
        try {
            executor.execute {
                reportState(RuntimeProviderState.RECOVERING, "handoff-started")
                try {
                    httpPoster(destination, envelopeJson)
                    reportState(RuntimeProviderState.HEALTHY, "handoff-succeeded")
                    completion.success()
                } catch (throwable: Throwable) {
                    fail(event, completion, categoryOf(throwable), throwable)
                }
            }
        } catch (throwable: Throwable) {
            fail(event, completion, HttpIntegrationEventFailureCategory.CLIENT_EXECUTION, throwable)
        }
    }

    private fun fail(
        event: EventRecord,
        completion: IntegrationEventPublishCompletion,
        category: HttpIntegrationEventFailureCategory,
        throwable: Throwable,
    ) {
        reportState(RuntimeProviderState.DEGRADED, category.name)
        val safeFailure = throwable as? HttpIntegrationEventHandoffException
            ?: HttpIntegrationEventHandoffException(category, throwable.javaClass.name)
        log.error(
            "Integration Event HTTP handoff failed: eventId={}, eventName={}, category={}, failureType={}",
            event.id,
            event.type,
            category,
            safeFailure.providerFailureType,
        )
        completion.failure(safeFailure)
    }

    private fun reportState(state: RuntimeProviderState, category: String) {
        runCatching { providerState.report(state, category) }
            .onFailure { failure ->
                log.warn(
                    "Integration Event HTTP provider state update failed: providerId={}, failureType={}",
                    providerState.providerId,
                    failure.javaClass.name,
                )
            }
    }

    companion object {
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val DEFAULT_RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(10)

        private fun defaultHttpPoster(
            connectTimeout: Duration,
            responseTimeout: Duration,
        ): (URI, String) -> Unit {
            val requestFactory = SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeout)
                setReadTimeout(responseTimeout)
            }
            val restTemplate = RestTemplate(requestFactory)
            return { url, envelopeJson ->
                try {
                    val response = restTemplate.postForEntity(
                        url,
                        HttpEntity(
                            envelopeJson.toByteArray(StandardCharsets.UTF_8),
                            HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
                        ),
                        String::class.java,
                    )
                    if (!response.statusCode.is2xxSuccessful) {
                        throw HttpIntegrationEventHandoffException(
                            HttpIntegrationEventFailureCategory.NON_2XX,
                            "http-status",
                            response.statusCode.value(),
                        )
                    }
                } catch (failure: HttpIntegrationEventHandoffException) {
                    throw failure
                } catch (failure: RestClientResponseException) {
                    throw HttpIntegrationEventHandoffException(
                        HttpIntegrationEventFailureCategory.NON_2XX,
                        failure.javaClass.name,
                        failure.statusCode.value(),
                    )
                } catch (failure: ResourceAccessException) {
                    throw HttpIntegrationEventHandoffException(
                        categoryOf(failure),
                        failure.javaClass.name,
                    )
                } catch (failure: Throwable) {
                    throw HttpIntegrationEventHandoffException(
                        HttpIntegrationEventFailureCategory.HANDOFF,
                        failure.javaClass.name,
                    )
                }
            }
        }

        private fun categoryOf(throwable: Throwable): HttpIntegrationEventFailureCategory = when {
            throwable is HttpIntegrationEventHandoffException -> throwable.category
            throwable is RejectedExecutionException -> HttpIntegrationEventFailureCategory.CLIENT_EXECUTION
            throwable.hasCause<SocketTimeoutException>() -> HttpIntegrationEventFailureCategory.TIMEOUT
            throwable.hasCause<ConnectException>() -> HttpIntegrationEventFailureCategory.CONNECTION
            else -> HttpIntegrationEventFailureCategory.HANDOFF
        }

        private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
            generateSequence(this) { it.cause }.any { it is T }
    }
}

enum class HttpIntegrationEventFailureCategory {
    ROUTE,
    ENVELOPE,
    CLIENT_EXECUTION,
    NON_2XX,
    CONNECTION,
    TIMEOUT,
    HANDOFF,
}

class HttpIntegrationEventHandoffException(
    val category: HttpIntegrationEventFailureCategory,
    val providerFailureType: String,
    val statusCode: Int? = null,
) : RuntimeException(
    buildString {
        append("HTTP Integration Event handoff failed: category=")
        append(category)
        if (statusCode != null) append(", status=").append(statusCode)
    },
)
