package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.configure.RabbitMqIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisorSupport
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@AutoConfiguration
@EnableConfigurationProperties(RabbitMqIntegrationEventAdapterProperties::class)
class RabbitMqIntegrationEventAutoConfiguration {
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
    ).also {
        IntegrationEventSupervisorSupport.configure(it as IntegrationEventSupervisor)
        IntegrationEventSupervisorSupport.configure(it as IntegrationEventManager)
    }

    @Bean
    fun rabbitMqIntegrationEventPublisher(
        rabbitTemplate: RabbitTemplate,
        connectionFactory: ConnectionFactory,
        environment: Environment,
        properties: RabbitMqIntegrationEventAdapterProperties,
    ): IntegrationEventPublisher = RabbitMqIntegrationEventPublisher(
        rabbitTemplate,
        connectionFactory,
        environment,
        properties.publishThreadPoolSize,
        properties.publishThreadFactoryClassName,
        properties.autoDeclareExchange,
        properties.defaultExchangeType,
    ).apply { init() }

    @Bean(destroyMethod = "shutdown")
    fun rabbitMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher: EventHandlerDispatcher,
        eventMessageInterceptors: List<EventMessageInterceptor>,
        configureProvider: ObjectProvider<RabbitMqIntegrationEventConfigure>,
        listenerContainerFactory: SimpleRabbitListenerContainerFactory,
        connectionFactory: ConnectionFactory,
        environment: Environment,
        eventTypeCatalog: EventTypeCatalog,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        executionContextScopeManager: ExecutionContextScopeManager,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        @Value(CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET) messageCharset: String,
        properties: RabbitMqIntegrationEventAdapterProperties,
    ): RabbitMqIntegrationEventSubscriberAdapter = RabbitMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher,
        eventMessageInterceptors,
        configureProvider.getIfAvailable(),
        listenerContainerFactory,
        connectionFactory,
        environment,
        eventTypeCatalog,
        serviceName,
        messageCharset,
        properties.autoDeclareQueue,
        executionContextCodecRegistry,
        executionContextScopeManager,
    ).apply { init() }
}
