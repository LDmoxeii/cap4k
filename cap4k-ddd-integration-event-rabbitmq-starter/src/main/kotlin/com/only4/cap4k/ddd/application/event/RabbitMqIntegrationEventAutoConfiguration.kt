package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.configure.RabbitMqIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(RabbitMqIntegrationEventAdapterProperties::class)
class RabbitMqIntegrationEventAutoConfiguration {
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
    fun rabbitMqIntegrationEventRouteResolver(
        properties: RabbitMqIntegrationEventAdapterProperties,
    ): IntegrationEventRouteResolver<RabbitMqIntegrationEventRoute> = StaticIntegrationEventRouteResolver(
        routes = properties.routes.toMap(),
        providerIdentity = "rabbitmq",
    )

    @Bean(destroyMethod = "close")
    fun rabbitMqIntegrationEventProviderState(
        registry: RuntimeProviderStateRegistry,
    ): RuntimeProviderStateReporter = registry.register(PROVIDER_ID)

    @Bean
    fun rabbitMqProviderStateCoordinator(
        @Qualifier(PROVIDER_STATE_BEAN) stateReporter: RuntimeProviderStateReporter,
    ): RabbitMqProviderStateCoordinator = RabbitMqProviderStateCoordinator(stateReporter)

    @Bean
    @ConditionalOnMissingBean(AmqpAdmin::class)
    fun rabbitMqIntegrationEventAmqpAdmin(
        connectionFactory: ConnectionFactory,
    ): AmqpAdmin = RabbitAdmin(connectionFactory).apply {
        setRedeclareManualDeclarations(true)
    }

    @Bean
    fun rabbitMqTopologyManager(
        amqpAdmin: AmqpAdmin,
        stateCoordinator: RabbitMqProviderStateCoordinator,
        properties: RabbitMqIntegrationEventAdapterProperties,
    ): RabbitMqTopologyManager {
        if (amqpAdmin is RabbitAdmin) {
            amqpAdmin.setRedeclareManualDeclarations(true)
        }
        return RabbitMqTopologyManager(amqpAdmin, properties.exchangeType, stateCoordinator.topology)
    }

    @Bean
    fun rabbitMqIntegrationEventPublisher(
        rabbitTemplate: RabbitTemplate,
        connectionFactory: ConnectionFactory,
        routeResolver: IntegrationEventRouteResolver<RabbitMqIntegrationEventRoute>,
        topologyManager: RabbitMqTopologyManager,
        stateCoordinator: RabbitMqProviderStateCoordinator,
        properties: RabbitMqIntegrationEventAdapterProperties,
    ): IntegrationEventPublisher = RabbitMqIntegrationEventPublisher(
        rabbitTemplate,
        connectionFactory,
        routeResolver,
        topologyManager,
        stateCoordinator.publisher,
        properties.publishThreadPoolSize,
        properties.confirmTimeout,
        properties.publishThreadFactoryClassName,
    ).apply { init() }

    @Bean(destroyMethod = "shutdown")
    fun rabbitMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher: EventHandlerDispatcher,
        eventMessageInterceptors: List<EventMessageInterceptor>,
        listenerContainerFactory: SimpleRabbitListenerContainerFactory,
        connectionFactory: ConnectionFactory,
        amqpAdmin: AmqpAdmin,
        routeResolver: IntegrationEventRouteResolver<RabbitMqIntegrationEventRoute>,
        topologyManager: RabbitMqTopologyManager,
        stateCoordinator: RabbitMqProviderStateCoordinator,
        eventTypeCatalog: InboundIntegrationEventRegistrationView,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        executionContextScopeManager: ExecutionContextScopeManager,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        properties: RabbitMqIntegrationEventAdapterProperties,
    ): RabbitMqIntegrationEventSubscriberAdapter = RabbitMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher,
        eventMessageInterceptors,
        listenerContainerFactory,
        connectionFactory,
        amqpAdmin,
        routeResolver,
        topologyManager,
        stateCoordinator.subscriber,
        eventTypeCatalog,
        serviceName,
        properties.messageCharset,
        properties.recoveryInterval,
        executionContextCodecRegistry,
        executionContextScopeManager,
        reliableEventDeliveryContextScopeManager,
    ).apply { init() }

    private companion object {
        const val PROVIDER_ID = "integration-event-transport.rabbitmq"
        const val PROVIDER_STATE_BEAN = "rabbitMqIntegrationEventProviderState"
    }
}
