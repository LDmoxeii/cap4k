package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventListenerFactory
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventSubscriberManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [CoreRuntimeAutoConfiguration::class])
class CoreDomainEventAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(EventSubscriberManager::class)
    fun defaultEventSubscriberManager(
        eventSubscribers: List<EventSubscriber<*>>,
        applicationEventPublisher: ApplicationEventPublisher,
    ): DefaultEventSubscriberManager = DefaultEventSubscriberManager(
        eventSubscribers,
        applicationEventPublisher,
    ).apply { init() }

    @Bean
    @ConditionalOnMissingBean(DomainEventInterceptorManager::class)
    fun defaultDomainEventInterceptorManager(
        eventInterceptors: List<EventInterceptor>,
    ): DefaultDomainEventInterceptorManager = DefaultDomainEventInterceptorManager(eventInterceptors)

    @Bean
    @ConditionalOnMissingBean(DomainEventSupervisor::class)
    fun defaultDomainEventSupervisor(
        interceptorManager: DomainEventInterceptorManager,
        eventSubscriberManager: EventSubscriberManager,
        reliableProvider: ObjectProvider<ReliableDomainEventProvider>,
        integrationEventManager: ObjectProvider<IntegrationEventManager>,
    ): DefaultDomainEventSupervisor = DefaultDomainEventSupervisor(
        interceptorManager,
        eventSubscriberManager,
        reliableProvider.getIfUnique(),
        integrationEventManager.getIfUnique(),
    )

    @Bean
    @ConditionalOnMissingBean(Cap4kEventListenerFactory::class)
    fun cap4kEventListenerFactory(): Cap4kEventListenerFactory = Cap4kEventListenerFactory()
}
