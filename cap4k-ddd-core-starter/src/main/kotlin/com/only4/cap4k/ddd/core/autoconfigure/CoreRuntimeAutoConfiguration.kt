package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.application.ReliableRequestSupervisor
import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestInterceptor
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.RequestSupervisorSupport
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkSupport
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisorSupport
import com.only4.cap4k.ddd.core.application.impl.DefaultRequestSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisorSupport
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisorSupport
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisorSupport
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisorSupport
import com.only4.cap4k.ddd.core.domain.service.impl.DefaultDomainServiceSupervisor
import jakarta.validation.Validator
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [CoreIdAutoConfiguration::class])
class CoreRuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RequestSupervisor::class)
    fun defaultRequestSupervisor(
        requestHandlers: List<RequestHandler<*, *>>,
        requestInterceptors: List<RequestInterceptor<*, *>>,
        validatorProvider: ObjectProvider<Validator>,
    ): DefaultRequestSupervisor = DefaultRequestSupervisor(
        requestHandlers,
        requestInterceptors,
        validatorProvider.ifAvailable,
    ).apply(DefaultRequestSupervisor::init)

    @Bean
    @ConditionalOnMissingBean(DomainServiceSupervisor::class)
    fun defaultDomainServiceSupervisor(applicationContext: ApplicationContext): DefaultDomainServiceSupervisor =
        DefaultDomainServiceSupervisor(applicationContext)

    @Bean
    @ConditionalOnMissingBean(EventTypeCatalog::class)
    fun eventTypeCatalog(beanFactory: ListableBeanFactory): EventTypeCatalog =
        SpringEventTypeCatalog(beanFactory)

    @Bean
    fun mediatorCapabilityBinder(
        applicationContext: ApplicationContext,
        beanFactory: ListableBeanFactory,
        identifierGenerator: IdentifierGenerator,
    ): SmartInitializingSingleton = SmartInitializingSingleton {
        MediatorSupport.configure(applicationContext)
        MediatorSupport.configure(identifierGenerator)
        RequestSupervisorSupport.configure(uniqueBean(beanFactory, RequestSupervisor::class.java, "requests"))
        DomainServiceSupervisorSupport.configure(
            uniqueBean(beanFactory, DomainServiceSupervisor::class.java, "services")
        )
        DomainEventSupervisorSupport.configure(
            uniqueBean(beanFactory, DomainEventSupervisor::class.java, "domain-events")
        )
        DomainEventSupervisorSupport.configure(
            uniqueBean(beanFactory, DomainEventManager::class.java, "domain-event-manager")
        )

        optionalUniqueBean(beanFactory, ReliableRequestSupervisor::class.java, "reliable-requests")
            ?.let(RequestSupervisorSupport::configure)
        optionalUniqueBean(beanFactory, AggregateFactorySupervisor::class.java, "factories")
            ?.let(AggregateFactorySupervisorSupport::configure)
        optionalUniqueBean(beanFactory, RepositorySupervisor::class.java, "repositories")
            ?.let(RepositorySupervisorSupport::configure)
        optionalUniqueBean(beanFactory, UnitOfWork::class.java, "unit-of-work")
            ?.let(UnitOfWorkSupport::configure)
        optionalUniqueBean(beanFactory, IntegrationEventSupervisor::class.java, "integration-events")
            ?.let(IntegrationEventSupervisorSupport::configure)
        optionalUniqueBean(beanFactory, IntegrationEventManager::class.java, "integration-event-manager")
            ?.let(IntegrationEventSupervisorSupport::configure)
        optionalUniqueBean(beanFactory, ReliableDomainEventProvider::class.java, "reliable-domain-events")
    }

    private fun <T : Any> uniqueBean(beanFactory: ListableBeanFactory, type: Class<T>, capability: String): T {
        val beans = beanFactory.getBeansOfType(type)
        require(beans.size == 1) {
            "cap4k capability '$capability' requires exactly one provider, found ${beans.keys.sorted()}"
        }
        return beans.values.single()
    }

    private fun <T : Any> optionalUniqueBean(
        beanFactory: ListableBeanFactory,
        type: Class<T>,
        capability: String,
    ): T? {
        val beans = beanFactory.getBeansOfType(type)
        require(beans.size <= 1) {
            "cap4k capability '$capability' has conflicting providers ${beans.keys.sorted()}"
        }
        return beans.values.singleOrNull()
    }
}
