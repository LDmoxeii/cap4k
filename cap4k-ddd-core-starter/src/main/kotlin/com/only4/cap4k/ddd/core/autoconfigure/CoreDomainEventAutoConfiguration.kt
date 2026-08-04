package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventListenerFactory
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerDescriptorResolver
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerRegistry
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventHandlerDispatcher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [CoreRuntimeAutoConfiguration::class])
class CoreDomainEventAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(EventHandlerDispatcher::class)
    fun defaultEventHandlerDispatcher(
        registry: Cap4kEventHandlerRegistry,
    ): DefaultEventHandlerDispatcher = DefaultEventHandlerDispatcher(registry)

    @Bean
    @ConditionalOnMissingBean(DomainEventInterceptorManager::class)
    fun defaultDomainEventInterceptorManager(
        eventInterceptors: List<EventInterceptor>,
    ): DefaultDomainEventInterceptorManager = DefaultDomainEventInterceptorManager(eventInterceptors)

    @Bean
    @ConditionalOnMissingBean(DomainEventSupervisor::class)
    fun defaultDomainEventSupervisor(
        interceptorManager: DomainEventInterceptorManager,
        eventHandlerDispatcher: EventHandlerDispatcher,
        reliableProvider: ObjectProvider<ReliableDomainEventProvider>,
        integrationEventManager: ObjectProvider<IntegrationEventManager>,
        executionContextAccessor: ExecutionContextAccessor,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    ): DefaultDomainEventSupervisor = DefaultDomainEventSupervisor(
        interceptorManager,
        eventHandlerDispatcher,
        reliableProvider.getIfUnique(),
        integrationEventManager.getIfUnique(),
        executionContextAccessor,
        reliableEventDeliveryContextScopeManager,
    )

    @Bean
    @ConditionalOnMissingBean(Cap4kEventListenerFactory::class)
    fun cap4kEventListenerFactory(
        descriptorResolver: Cap4kEventHandlerDescriptorResolver,
        registry: Cap4kEventHandlerRegistry,
        invocationScopeManager: InvocationScopeManager,
    ): Cap4kEventListenerFactory = Cap4kEventListenerFactory(
        descriptorResolver,
        registry,
        invocationScopeManager,
    )
}
