package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisorSupport
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_ROCKETMQ_NAME_SERVER
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@AutoConfiguration
@ImportAutoConfiguration(RocketMQAutoConfiguration::class)
class RocketMqIntegrationEventAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IntegrationEventSupervisor::class)
    fun integrationEventSupervisor(
        eventPublisher: EventPublisher,
        eventRecordRepository: EventRecordRepository,
        interceptorManager: IntegrationEventInterceptorManager,
        applicationEventPublisher: ApplicationEventPublisher,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
    ): DefaultIntegrationEventSupervisor = DefaultIntegrationEventSupervisor(
        eventPublisher,
        eventRecordRepository,
        interceptorManager,
        applicationEventPublisher,
        serviceName,
    ).also {
        IntegrationEventSupervisorSupport.configure(it as IntegrationEventSupervisor)
        IntegrationEventSupervisorSupport.configure(it as IntegrationEventManager)
    }

    @Bean
    fun rocketMqIntegrationEventPublisher(
        rocketMQTemplate: RocketMQTemplate,
        environment: Environment,
    ): IntegrationEventPublisher = RocketMqIntegrationEventPublisher(rocketMQTemplate, environment)

    @Bean(destroyMethod = "shutdown")
    fun rocketMqIntegrationEventSubscriberAdapter(
        eventSubscriberManager: EventSubscriberManager,
        eventMessageInterceptors: List<EventMessageInterceptor>,
        configureProvider: ObjectProvider<RocketMqIntegrationEventConfigure>,
        environment: Environment,
        eventTypeCatalog: EventTypeCatalog,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        @Value(CONFIG_KEY_4_ROCKETMQ_NAME_SERVER) defaultNameServer: String,
        @Value(CONFIG_KEY_4_ROCKETMQ_MSG_CHARSET) messageCharset: String,
    ): RocketMqIntegrationEventSubscriberAdapter = RocketMqIntegrationEventSubscriberAdapter(
        eventSubscriberManager,
        eventMessageInterceptors,
        configureProvider.getIfAvailable(),
        environment,
        eventTypeCatalog,
        serviceName,
        defaultNameServer,
        messageCharset,
    ).apply { init() }
}
