package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.configure.HttpIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.HttpRequestHandler
import java.net.URI
import java.nio.charset.StandardCharsets

@AutoConfiguration
@EnableConfigurationProperties(HttpIntegrationEventAdapterProperties::class)
class HttpIntegrationEventAutoConfiguration {
    companion object {
        private val log = LoggerFactory.getLogger(HttpIntegrationEventAutoConfiguration::class.java)

        const val CONSUME_PATH = "/cap4k/integration-events"
        const val HTTP_PROVIDER_ID = "integration-event-transport.http"
        const val HTTP_PROVIDER_STATE_BEAN = "cap4kHttpIntegrationEventProviderState"
    }

    @Bean
    @ConditionalOnMissingBean(IntegrationEventSupervisor::class)
    fun integrationEventSupervisor(
        reliableEventCoordinator: ReliableEventCoordinator,
        eventRecordRepository: EventRecordRepository,
        interceptorManager: IntegrationEventInterceptorManager,
        applicationEventPublisher: ApplicationEventPublisher,
        executionContextAccessor: ExecutionContextAccessor,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        invocationScopeAccessor: InvocationScopeAccessor,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
    ): DefaultIntegrationEventSupervisor = DefaultIntegrationEventSupervisor(
        reliableEventCoordinator,
        eventRecordRepository,
        interceptorManager,
        applicationEventPublisher,
        serviceName,
        executionContextAccessor,
        executionContextCodecRegistry,
        invocationScopeAccessor,
    )

    @Bean
    fun httpIntegrationEventRouteResolver(
        properties: HttpIntegrationEventAdapterProperties,
    ): IntegrationEventRouteResolver<URI> = HttpIntegrationEventRouteResolver(properties.routes)

    @Bean
    fun httpIntegrationEventRouteInterceptor(
        routeResolver: IntegrationEventRouteResolver<URI>,
    ): IntegrationEventInterceptor = HttpIntegrationEventRouteInterceptor(routeResolver)

    @Bean(name = [HTTP_PROVIDER_STATE_BEAN], destroyMethod = "close")
    fun httpIntegrationEventProviderState(
        registry: RuntimeProviderStateRegistry,
    ): RuntimeProviderStateReporter = registry.register(HTTP_PROVIDER_ID)

    @Bean(destroyMethod = "close")
    fun httpIntegrationEventPublisher(
        routeResolver: IntegrationEventRouteResolver<URI>,
        properties: HttpIntegrationEventAdapterProperties,
        @Qualifier(HTTP_PROVIDER_STATE_BEAN) providerState: RuntimeProviderStateReporter,
    ): IntegrationEventPublisher = HttpIntegrationEventPublisher(
        routeResolver,
        properties.publishThreadPoolSize,
        properties.publishThreadFactoryClassName,
        providerState = providerState,
    ).apply(HttpIntegrationEventPublisher::init)
        .also { log.info("集成事件适配类型：HTTP") }

    @Bean
    fun httpIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher: EventHandlerDispatcher,
        eventMessageInterceptors: List<EventMessageInterceptor>,
        eventTypeCatalog: InboundIntegrationEventRegistrationView,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        executionContextScopeManager: ExecutionContextScopeManager,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    ): HttpIntegrationEventSubscriberAdapter = HttpIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher,
        eventMessageInterceptors,
        eventTypeCatalog,
        executionContextCodecRegistry,
        executionContextScopeManager,
        reliableEventDeliveryContextScopeManager,
    )

    @Bean(name = [CONSUME_PATH])
    @ConditionalOnWebApplication
    fun httpIntegrationEventConsumeHandler(
        subscriberAdapter: HttpIntegrationEventSubscriberAdapter,
    ): HttpRequestHandler = HttpRequestHandler { request, response ->
        if (request.method != HttpMethod.POST.name()) {
            response.status = jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED
            response.setHeader("Allow", HttpMethod.POST.name())
            writeJson(
                response,
                HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                    success = false,
                    message = "method_not_allowed",
                ),
            )
        } else {
            val payload = request.inputStream.bufferedReader().use { it.readText() }
            log.info("Integration Event HTTP envelope received")

            val headers = mutableMapOf<String, Any>()
            runCatching {
                val headerNames = request.headerNames
                while (headerNames.hasMoreElements()) {
                    val headerName = headerNames.nextElement()
                    val values = request.getHeaders(headerName).toList()
                    if (values.isNotEmpty()) headers[headerName] = values.singleOrNull() ?: values
                }
            }.onFailure { throwable ->
                log.warn("Integration Event HTTP request header read failed: failureType={}", throwable.javaClass.name)
            }

            val result = subscriberAdapter.consume(payload, headers)
            response.status = when (result.category) {
                HttpIntegrationEventConsumeCategory.SUCCESS -> jakarta.servlet.http.HttpServletResponse.SC_OK
                HttpIntegrationEventConsumeCategory.MALFORMED_ENVELOPE ->
                    jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST
                HttpIntegrationEventConsumeCategory.UNKNOWN_EVENT ->
                    HttpStatus.UNPROCESSABLE_ENTITY.value()
                HttpIntegrationEventConsumeCategory.DELIVERY_FAILED ->
                    jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            }
            writeJson(
                response,
                HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                    success = result.success,
                    message = result.category.name.lowercase(),
                ),
            )
        }
    }

    private fun writeJson(response: jakarta.servlet.http.HttpServletResponse, value: Any) {
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = "application/json; charset=utf-8"
        response.writer.use { writer -> writer.write(RuntimeJson.write(value)) }
    }
}
