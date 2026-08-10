package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.configure.RocketMqIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_ROCKETMQ_NAME_SERVER
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ImportAutoConfiguration(RocketMQAutoConfiguration::class)
@EnableConfigurationProperties(RocketMqIntegrationEventAdapterProperties::class)
class RocketMqIntegrationEventAutoConfiguration {
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
    fun rocketMqIntegrationEventRouteResolver(
        properties: RocketMqIntegrationEventAdapterProperties,
    ): IntegrationEventRouteResolver<RocketMqIntegrationEventRoute> = StaticIntegrationEventRouteResolver(
        routes = properties.routes.mapValues { (_, route) ->
            RocketMqIntegrationEventRoute(route.topic, route.tag)
        },
        providerIdentity = "rocketmq",
    )

    @Bean
    fun rocketMqIntegrationEventRouteInterceptor(
        routeResolver: IntegrationEventRouteResolver<RocketMqIntegrationEventRoute>,
    ): IntegrationEventInterceptor = RocketMqIntegrationEventRouteInterceptor(routeResolver)

    @Bean
    fun rocketMqConsumerGroupResolver(): RocketMqConsumerGroupResolver = RocketMqConsumerGroupResolver()

    @Bean(destroyMethod = "close")
    fun rocketMqIntegrationEventProviderState(
        registry: RuntimeProviderStateRegistry,
    ): RuntimeProviderStateReporter = registry.register(PROVIDER_ID)

    @Bean
    fun rocketMqProviderStateCoordinator(
        @Qualifier(PROVIDER_STATE_BEAN) stateReporter: RuntimeProviderStateReporter,
    ): RocketMqProviderStateCoordinator = RocketMqProviderStateCoordinator(stateReporter)

    @Bean(destroyMethod = "close")
    fun rocketMqRecoveryScheduler(): RocketMqRecoveryScheduler = ScheduledRocketMqRecoveryScheduler()

    @Bean
    fun rocketMqIntegrationEventPublisher(
        rocketMQTemplate: RocketMQTemplate,
        routeResolver: IntegrationEventRouteResolver<RocketMqIntegrationEventRoute>,
        stateCoordinator: RocketMqProviderStateCoordinator,
    ): IntegrationEventPublisher = RocketMqIntegrationEventPublisher(
        rocketMQTemplate = rocketMQTemplate,
        routeResolver = routeResolver,
        deliveryTimeoutMillis = rocketMQTemplate.producer.sendMsgTimeout.toLong(),
        stateReporter = stateCoordinator.publisher,
    ).apply { init() }

    @Bean(destroyMethod = "shutdown")
    fun rocketMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher: EventHandlerDispatcher,
        eventMessageInterceptors: List<EventMessageInterceptor>,
        routeResolver: IntegrationEventRouteResolver<RocketMqIntegrationEventRoute>,
        consumerGroupResolver: RocketMqConsumerGroupResolver,
        eventTypeCatalog: InboundIntegrationEventRegistrationView,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        executionContextScopeManager: ExecutionContextScopeManager,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
        stateCoordinator: RocketMqProviderStateCoordinator,
        recoveryScheduler: RocketMqRecoveryScheduler,
        properties: RocketMqIntegrationEventAdapterProperties,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        @Value(CONFIG_KEY_4_ROCKETMQ_NAME_SERVER) defaultNameServer: String,
        @Value(CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET) messageCharset: String,
    ): RocketMqIntegrationEventSubscriberAdapter = RocketMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher,
        eventMessageInterceptors,
        routeResolver,
        consumerGroupResolver,
        eventTypeCatalog,
        serviceName,
        defaultNameServer,
        messageCharset,
        stateCoordinator.subscriber,
        properties.recoveryInterval,
        executionContextCodecRegistry,
        executionContextScopeManager,
        reliableEventDeliveryContextScopeManager,
        recoveryScheduler = recoveryScheduler,
    ).apply { init() }

    private companion object {
        const val PROVIDER_ID = RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY
        const val PROVIDER_STATE_BEAN = "rocketMqIntegrationEventProviderState"
    }
}
