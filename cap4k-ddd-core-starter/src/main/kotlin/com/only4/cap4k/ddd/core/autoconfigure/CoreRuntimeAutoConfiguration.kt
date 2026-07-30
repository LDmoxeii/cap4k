package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.ProviderUnavailableException
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkSupport
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.capability.CapabilityInterceptor
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisorSupport
import com.only4.cap4k.ddd.core.application.capability.impl.DefaultCapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandInterceptor
import com.only4.cap4k.ddd.core.application.command.CommandManager
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.CommandSupervisorSupport
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisorSupport
import com.only4.cap4k.ddd.core.application.command.impl.DefaultCommandSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisorSupport
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.QueryInterceptor
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import com.only4.cap4k.ddd.core.application.query.QuerySupervisorSupport
import com.only4.cap4k.ddd.core.application.query.impl.DefaultQuerySupervisor
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
    @ConditionalOnMissingBean(CommandSupervisor::class)
    fun defaultCommandSupervisor(
        handlers: List<CommandHandler<*, *>>,
        interceptors: List<CommandInterceptor<*, *>>,
        validatorProvider: ObjectProvider<Validator>,
        unitOfWorkProvider: ObjectProvider<UnitOfWork>,
    ): DefaultCommandSupervisor = DefaultCommandSupervisor(
        handlers,
        interceptors,
        validatorProvider.ifAvailable,
        unitOfWorkProvider = {
            unitOfWorkProvider.ifAvailable
                ?: throw ProviderUnavailableException("unit-of-work", "a cap4k Persistence Provider starter")
        },
    ).apply(DefaultCommandSupervisor::init)

    @Bean
    @ConditionalOnMissingBean(QuerySupervisor::class)
    fun defaultQuerySupervisor(
        handlers: List<QueryHandler<*, *>>,
        interceptors: List<QueryInterceptor<*, *>>,
        validatorProvider: ObjectProvider<Validator>,
    ): DefaultQuerySupervisor = DefaultQuerySupervisor(
        handlers,
        interceptors,
        validatorProvider.ifAvailable,
    ).apply(DefaultQuerySupervisor::init)

    @Bean
    @ConditionalOnMissingBean(CapabilitySupervisor::class)
    fun defaultCapabilitySupervisor(
        handlers: List<CapabilityHandler<*, *>>,
        interceptors: List<CapabilityInterceptor<*, *>>,
        validatorProvider: ObjectProvider<Validator>,
    ): DefaultCapabilitySupervisor = DefaultCapabilitySupervisor(
        handlers,
        interceptors,
        validatorProvider.ifAvailable,
    ).apply(DefaultCapabilitySupervisor::init)

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
        CommandSupervisorSupport.configure(uniqueBean(beanFactory, CommandSupervisor::class.java, "commands"))
        QuerySupervisorSupport.configure(uniqueBean(beanFactory, QuerySupervisor::class.java, "queries"))
        CapabilitySupervisorSupport.configure(
            uniqueBean(beanFactory, CapabilitySupervisor::class.java, "capabilities")
        )
        DomainServiceSupervisorSupport.configure(
            uniqueBean(beanFactory, DomainServiceSupervisor::class.java, "services")
        )
        DomainEventSupervisorSupport.configure(
            uniqueBean(beanFactory, DomainEventSupervisor::class.java, "domain-events")
        )
        DomainEventSupervisorSupport.configure(
            uniqueBean(beanFactory, DomainEventManager::class.java, "domain-event-manager")
        )

        optionalUniqueBean(beanFactory, ReliableCommandSupervisor::class.java, "reliable-commands")
            ?.let(ReliableCommandSupervisorSupport::configure)
        optionalUniqueBean(beanFactory, CommandManager::class.java, "command-manager")
            ?.let(ReliableCommandSupervisorSupport::configure)
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

    private fun <T : Any> uniqueBean(beanFactory: ListableBeanFactory, type: Class<T>, provider: String): T {
        val beans = beanFactory.getBeansOfType(type)
        require(beans.size == 1) {
            "cap4k provider '$provider' requires exactly one implementation, found ${beans.keys.sorted()}"
        }
        return beans.values.single()
    }

    private fun <T : Any> optionalUniqueBean(
        beanFactory: ListableBeanFactory,
        type: Class<T>,
        provider: String,
    ): T? {
        val beans = beanFactory.getBeansOfType(type)
        require(beans.size <= 1) {
            "cap4k provider '$provider' has conflicting implementations ${beans.keys.sorted()}"
        }
        return beans.values.singleOrNull()
    }
}
