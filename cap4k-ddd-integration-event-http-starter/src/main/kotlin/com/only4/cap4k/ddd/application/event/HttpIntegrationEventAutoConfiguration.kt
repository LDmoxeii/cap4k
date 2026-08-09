package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.configure.HttpIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.web.HttpRequestHandler
import java.nio.charset.StandardCharsets

@AutoConfiguration
@EnableConfigurationProperties(HttpIntegrationEventAdapterProperties::class)
class HttpIntegrationEventAutoConfiguration {
    companion object {
        private val log = LoggerFactory.getLogger(HttpIntegrationEventAutoConfiguration::class.java)

        const val CONSUME_PATH = "/cap4k/integration-events"
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
    ): IntegrationEventRouteResolver<String> = StaticIntegrationEventRouteResolver(
        routes = properties.routes,
        providerIdentity = "http",
    )

    @Bean
    fun httpIntegrationEventPublisher(
        routeResolver: IntegrationEventRouteResolver<String>,
        properties: HttpIntegrationEventAdapterProperties,
    ): IntegrationEventPublisher = HttpIntegrationEventPublisher(
        routeResolver,
        properties.publishThreadPoolSize,
        properties.publishThreadFactoryClassName,
    ).also { log.info("集成事件适配类型：HTTP") }

    @Bean
    fun httpIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher: EventHandlerDispatcher,
        eventMessageInterceptors: List<EventMessageInterceptor>,
        eventTypeCatalog: InboundIntegrationEventRegistrationView,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        executionContextScopeManager: ExecutionContextScopeManager,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
    ): HttpIntegrationEventSubscriberAdapter = HttpIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher,
        eventMessageInterceptors,
        eventTypeCatalog,
        serviceName,
        executionContextCodecRegistry,
        executionContextScopeManager,
        reliableEventDeliveryContextScopeManager,
    )

    @Bean(name = [CONSUME_PATH])
    @ConditionalOnWebApplication
    fun httpIntegrationEventConsumeHandler(
        subscriberAdapter: HttpIntegrationEventSubscriberAdapter,
    ): HttpRequestHandler = HttpRequestHandler { request, response ->
        val payload = request.inputStream.bufferedReader().use { it.readText() }
        log.info("IntegrationEvent envelope received")

        val headers = mutableMapOf<String, Any>()
        runCatching {
            val headerNames = request.headerNames
            while (headerNames.hasMoreElements()) {
                val headerName = headerNames.nextElement()
                val values = request.getHeaders(headerName).toList()
                if (values.isNotEmpty()) headers[headerName] = values.singleOrNull() ?: values
            }
        }.onFailure { throwable -> log.warn("读取请求头异常", throwable) }

        val success = subscriberAdapter.consume(payload, headers)
        writeJson(
            response,
            HttpIntegrationEventSubscriberAdapter.OperationResponse<Any>(
                success = success,
                message = if (success) "ok" else "fail",
            ),
        )
    }

    private fun writeJson(response: jakarta.servlet.http.HttpServletResponse, value: Any) {
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = "application/json; charset=utf-8"
        response.writer.use { writer -> writer.write(RuntimeJson.write(value)) }
    }
}
