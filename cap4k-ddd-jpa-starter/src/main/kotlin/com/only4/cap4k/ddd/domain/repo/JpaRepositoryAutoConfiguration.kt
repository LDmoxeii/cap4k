package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.application.JpaPersistenceAuditEnricher
import com.only4.cap4k.ddd.application.JpaUnitOfWorkLimits
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateLifecycleInvoker
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.impl.ReflectiveAggregateLifecycleInvoker
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.domain.repo.configure.JpaUnitOfWorkProperties
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AutoConfiguration
@EnableConfigurationProperties(JpaUnitOfWorkProperties::class)
class JpaRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RepositorySupervisor::class)
    fun defaultRepositorySupervisor(
        repositories: List<Repository<*>>,
        unitOfWork: UnitOfWork,
    ): DefaultRepositorySupervisor = DefaultRepositorySupervisor(repositories, unitOfWork).apply {
        init()
    }

    @Bean
    @ConditionalOnMissingBean(AggregateFactorySupervisor::class)
    fun defaultAggregateFactorySupervisor(
        factories: List<AggregateFactory<*, *>>,
        unitOfWork: UnitOfWork,
        lifecycleInvoker: AggregateLifecycleInvoker,
    ): DefaultAggregateFactorySupervisor = DefaultAggregateFactorySupervisor(
        factories,
        unitOfWork,
        lifecycleInvoker,
    ).apply {
        init()
    }

    @Bean
    @ConditionalOnMissingBean(UnitOfWork::class)
    fun jpaUnitOfWork(
        domainEventManager: DomainEventManager,
        integrationEventManager: ObjectProvider<IntegrationEventManager>,
        lifecycleInvoker: AggregateLifecycleInvoker,
        jpaUnitOfWorkProperties: JpaUnitOfWorkProperties,
        generatedOwnIdRegistry: GeneratedOwnIdRegistry,
        auditEnrichers: List<JpaPersistenceAuditEnricher>,
    ): JpaUnitOfWork = JpaUnitOfWork(
        domainEventManager = domainEventManager,
        integrationEventManager = integrationEventManager.getIfUnique(),
        lifecycleInvoker = lifecycleInvoker,
        generatedOwnIdRegistry = generatedOwnIdRegistry,
        auditEnrichers = auditEnrichers,
        limits = JpaUnitOfWorkLimits(
            maxFrontierRounds = jpaUnitOfWorkProperties.maxFrontierRounds,
            maxSynchronousEvents = jpaUnitOfWorkProperties.maxSynchronousEvents,
            maxNestedCommands = jpaUnitOfWorkProperties.maxNestedCommands,
            maxProviderFlushes = jpaUnitOfWorkProperties.maxProviderFlushes,
        ),
    ).also { JpaQueryUtils.configure(it, jpaUnitOfWorkProperties.retrieveCountWarnThreshold) }

    @Configuration(proxyBeanMethods = false)
    class JpaUnitOfWorkLoader(
        @Autowired(required = false) jpaUnitOfWork: JpaUnitOfWork?,
    ) {
        init {
            jpaUnitOfWork?.let { JpaUnitOfWork.fixAopWrapper(it) }
        }
    }

    @Bean
    @ConditionalOnMissingBean(AggregateLifecycleInvoker::class)
    fun aggregateLifecycleInvoker(): AggregateLifecycleInvoker = ReflectiveAggregateLifecycleInvoker()
}
