package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpCallbackTriggerCapability
import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpSubscribeCapability
import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpUnsubscribeCapability
import com.only4.cap4k.ddd.application.event.configure.HttpIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.application.event.impl.DefaultHttpIntegrationEventSubscriberRegister
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_TIMESTAMP
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.web.HttpRequestHandler
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.time.Instant

@AutoConfiguration
@EnableConfigurationProperties(HttpIntegrationEventAdapterProperties::class)
class HttpIntegrationEventAutoConfiguration {
    companion object {
        private val log = LoggerFactory.getLogger(HttpIntegrationEventAutoConfiguration::class.java)

        const val EVENT_PARAM = "event"
        const val EVENT_ID_PARAM = "uuid"
        const val SUBSCRIBER_PARAM = "subscriber"
        const val CONSUME_PATH = "/cap4k/integration-event/http/consume"
        const val SUBSCRIBE_PATH = "/cap4k/integration-event/http/subscribe"
        const val UNSUBSCRIBE_PATH = "/cap4k/integration-event/http/unsubscribe"
        const val EVENTS_PATH = "/cap4k/integration-event/http/events"
        const val SUBSCRIBERS_PATH = "/cap4k/integration-event/http/subscribers"
    }

    @Bean
    @ConditionalOnMissingBean(IntegrationEventSupervisor::class)
    fun integrationEventSupervisor(
        eventPublisher: EventPublisher,
        eventRecordRepository: EventRecordRepository,
        interceptorManager: IntegrationEventInterceptorManager,
        applicationEventPublisher: ApplicationEventPublisher,
        executionContextAccessor: ExecutionContextAccessor,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        invocationScopeAccessor: InvocationScopeAccessor,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
    ): DefaultIntegrationEventSupervisor = DefaultIntegrationEventSupervisor(
        eventPublisher,
        eventRecordRepository,
        interceptorManager,
        applicationEventPublisher,
        serviceName,
        executionContextAccessor,
        executionContextCodecRegistry,
        invocationScopeAccessor,
    )

    @Bean
    fun httpIntegrationEventCallbackTriggerCapabilityHandler(): IntegrationEventHttpCallbackTriggerCapability.Handler =
        IntegrationEventHttpCallbackTriggerCapability.Handler(RestTemplate(), EVENT_PARAM, EVENT_ID_PARAM)

    @Bean
    fun httpIntegrationEventSubscribeCapabilityHandler(): IntegrationEventHttpSubscribeCapability.Handler =
        IntegrationEventHttpSubscribeCapability.Handler(RestTemplate(), EVENT_PARAM, SUBSCRIBER_PARAM)

    @Bean
    fun httpIntegrationEventUnsubscribeCapabilityHandler(): IntegrationEventHttpUnsubscribeCapability.Handler =
        IntegrationEventHttpUnsubscribeCapability.Handler(RestTemplate(), EVENT_PARAM, SUBSCRIBER_PARAM)

    @Bean
    @ConditionalOnMissingBean(HttpIntegrationEventSubscriberRegister::class)
    fun httpIntegrationEventSubscriberRegister(): HttpIntegrationEventSubscriberRegister =
        DefaultHttpIntegrationEventSubscriberRegister()

    @Bean
    fun httpIntegrationEventPublisher(
        subscriberRegister: HttpIntegrationEventSubscriberRegister,
        environment: Environment,
        properties: HttpIntegrationEventAdapterProperties,
    ): IntegrationEventPublisher = HttpIntegrationEventPublisher(
        subscriberRegister,
        environment,
        properties.publishThreadPoolSize,
        properties.publishThreadFactoryClassName,
    ).apply {
        init()
        log.info("集成事件适配类型：HTTP")
    }

    @Bean
    fun httpIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher: EventHandlerDispatcher,
        eventMessageInterceptors: List<EventMessageInterceptor>,
        subscriberRegister: HttpIntegrationEventSubscriberRegister,
        environment: Environment,
        eventTypeCatalog: EventTypeCatalog,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        executionContextScopeManager: ExecutionContextScopeManager,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContextPath: String,
    ): HttpIntegrationEventSubscriberAdapter = HttpIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher,
        eventMessageInterceptors,
        subscriberRegister,
        environment,
        eventTypeCatalog,
        serviceName,
        "http://localhost:$serverPort$serverServletContextPath",
        SUBSCRIBE_PATH,
        CONSUME_PATH,
        executionContextCodecRegistry,
        executionContextScopeManager,
        reliableEventDeliveryContextScopeManager,
    ).apply { init() }

    @Bean(name = [SUBSCRIBE_PATH])
    @ConditionalOnWebApplication
    fun httpIntegrationEventSubscribeHandler(
        subscriberRegister: HttpIntegrationEventSubscriberRegister,
    ): HttpRequestHandler = HttpRequestHandler { request, response ->
        val event = request.getParameter(EVENT_PARAM).orEmpty()
        val subscriber = request.getParameter(SUBSCRIBER_PARAM).orEmpty()
        val callbackUrl = RuntimeJson.read(
            request.inputStream.bufferedReader().use { it.readText() },
            String::class.java,
        )
            .orEmpty()
        val success = event.isNotBlank() && subscriber.isNotBlank() && callbackUrl.isNotBlank() &&
            subscriberRegister.subscribe(event, subscriber, callbackUrl)
        writeJson(
            response,
            HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                success = success,
                message = if (success) "ok" else if (event.isBlank() || subscriber.isBlank() || callbackUrl.isBlank()) {
                    "必要参数缺失"
                } else {
                    "fail"
                },
            ),
        )
    }

    @Bean(name = [UNSUBSCRIBE_PATH])
    @ConditionalOnWebApplication
    fun httpIntegrationEventUnsubscribeHandler(
        subscriberRegister: HttpIntegrationEventSubscriberRegister,
    ): HttpRequestHandler = HttpRequestHandler { request, response ->
        val success = subscriberRegister.unsubscribe(
            request.getParameter(EVENT_PARAM).orEmpty(),
            request.getParameter(SUBSCRIBER_PARAM).orEmpty(),
        )
        writeJson(
            response,
            HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                success = success,
                message = if (success) "ok" else "fail",
            ),
        )
    }

    @Bean(name = [EVENTS_PATH])
    @ConditionalOnWebApplication
    fun httpIntegrationEventEventsHandler(
        subscriberRegister: HttpIntegrationEventSubscriberRegister,
    ): HttpRequestHandler = HttpRequestHandler { _, response ->
        val operationResponse = runCatching { subscriberRegister.events() }
            .fold(
                onSuccess = { events ->
                    HttpIntegrationEventSubscriberAdapter.OperationResponse(
                        success = true,
                        message = "ok",
                        data = events,
                    )
                },
                onFailure = { throwable ->
                    HttpIntegrationEventSubscriberAdapter.OperationResponse<List<String>>(
                        success = false,
                        message = throwable.message,
                    )
                },
            )
        writeJson(response, operationResponse)
    }

    @Bean(name = [SUBSCRIBERS_PATH])
    @ConditionalOnWebApplication
    fun httpIntegrationEventSubscribersHandler(
        subscriberRegister: HttpIntegrationEventSubscriberRegister,
    ): HttpRequestHandler = HttpRequestHandler { request, response ->
        val operationResponse = runCatching {
            subscriberRegister.subscribers(request.getParameter(EVENT_PARAM).orEmpty())
        }.fold(
            onSuccess = { subscribers ->
                HttpIntegrationEventSubscriberAdapter.OperationResponse(
                    success = true,
                    message = "ok",
                    data = subscribers,
                )
            },
            onFailure = { throwable ->
                HttpIntegrationEventSubscriberAdapter.OperationResponse<List<HttpIntegrationEventSubscriberRegister.SubscriberInfo>>(
                    success = false,
                    message = throwable.message,
                )
            },
        )
        writeJson(response, operationResponse)
    }

    @Bean(name = [CONSUME_PATH])
    @ConditionalOnWebApplication
    fun httpIntegrationEventConsumeHandler(
        subscriberAdapter: HttpIntegrationEventSubscriberAdapter,
    ): HttpRequestHandler = HttpRequestHandler { request, response ->
        val eventId = request.singleNonBlankParameter(EVENT_ID_PARAM)
        val eventName = request.singleNonBlankParameter(EVENT_PARAM)
        val publishedAt = request.strictEpochMillisHeader()
        val payload = request.inputStream.bufferedReader().use { it.readText() }
        log.info("IntegrationEvent uuid={} event={} publishedAt={}", eventId, eventName, publishedAt)

        val headers = mutableMapOf<String, Any>()
        eventId?.let { headers[EVENT_ID_PARAM] = it }
        runCatching {
            val headerNames = request.headerNames
            while (headerNames.hasMoreElements()) {
                val headerName = headerNames.nextElement()
                val values = request.getHeaders(headerName).toList()
                if (values.isNotEmpty()) headers[headerName] = values.singleOrNull() ?: values
            }
        }.onFailure { throwable -> log.warn("读取请求头异常", throwable) }

        val success = if (eventId == null || eventName == null || publishedAt == null) {
            false
        } else {
            subscriberAdapter.consume(eventId, eventName, publishedAt, payload, headers)
        }
        writeJson(
            response,
            HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                success = success,
                message = if (success) "ok" else "fail",
            ),
        )
    }

    private fun jakarta.servlet.http.HttpServletRequest.singleNonBlankParameter(name: String): String? =
        getParameterValues(name)?.singleOrNull()?.takeIf(String::isNotBlank)

    private fun jakarta.servlet.http.HttpServletRequest.strictEpochMillisHeader(): Instant? {
        val raw = getHeaders(HEADER_KEY_CAP4K_TIMESTAMP).toList().singleOrNull() ?: return null
        val millis = raw.toLongOrNull() ?: return null
        if (raw != millis.toString()) return null
        return runCatching { Instant.ofEpochMilli(millis) }.getOrNull()
    }

    private fun writeJson(response: jakarta.servlet.http.HttpServletResponse, value: Any) {
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = "application/json; charset=utf-8"
        response.writer.use { writer -> writer.write(RuntimeJson.write(value)) }
    }
}
