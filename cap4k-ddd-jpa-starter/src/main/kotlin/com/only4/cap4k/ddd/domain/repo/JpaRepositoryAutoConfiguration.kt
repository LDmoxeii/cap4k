package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.application.JpaQueryExecution
import com.only4.cap4k.ddd.application.JpaPersistenceAuditEnricher
import com.only4.cap4k.ddd.application.JpaUnitOfWorkLimits
import com.only4.cap4k.ddd.core.application.AggregatePersistenceIntentRecorder
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateLifecycleInvoker
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateRootCatalog
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.impl.FactoryDerivedAggregateRootCatalog
import com.only4.cap4k.ddd.core.domain.aggregate.impl.ReflectiveAggregateLifecycleInvoker
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.domain.repo.configure.JpaUnitOfWorkProperties
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.time.Clock

@AutoConfiguration
@EnableConfigurationProperties(JpaUnitOfWorkProperties::class)
class JpaRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RepositorySupervisor::class)
    fun defaultRepositorySupervisor(
        repositories: List<Repository<*>>,
        persistenceIntents: AggregatePersistenceIntentRecorder,
        invocationScopeAccessor: InvocationScopeAccessor,
        aggregateRootCatalog: AggregateRootCatalog,
    ): DefaultRepositorySupervisor = DefaultRepositorySupervisor(
        repositories,
        persistenceIntents,
        invocationScopeAccessor,
        aggregateRootCatalog,
    ).apply {
        init()
    }

    @Bean
    @ConditionalOnMissingBean(AggregateRootCatalog::class)
    fun aggregateRootCatalog(
        factories: List<AggregateFactory<*, *>>,
    ): AggregateRootCatalog = FactoryDerivedAggregateRootCatalog(factories)

    @Bean
    @ConditionalOnMissingBean(AggregateFactorySupervisor::class)
    fun defaultAggregateFactorySupervisor(
        factories: List<AggregateFactory<*, *>>,
        persistenceIntents: AggregatePersistenceIntentRecorder,
        invocationScopeAccessor: InvocationScopeAccessor,
        lifecycleInvoker: AggregateLifecycleInvoker,
    ): DefaultAggregateFactorySupervisor = DefaultAggregateFactorySupervisor(
        factories,
        persistenceIntents,
        invocationScopeAccessor,
        lifecycleInvoker,
    ).apply {
        init()
    }

    @Bean
    @ConditionalOnMissingBean(CommandUnitOfWorkCoordinator::class)
    fun jpaUnitOfWork(
        domainEventManager: DomainEventManager,
        integrationEventManager: ObjectProvider<IntegrationEventManager>,
        lifecycleInvoker: AggregateLifecycleInvoker,
        jpaUnitOfWorkProperties: JpaUnitOfWorkProperties,
        generatedOwnIdRegistry: GeneratedOwnIdRegistry,
        auditEnrichers: List<JpaPersistenceAuditEnricher>,
        clock: ObjectProvider<Clock>,
        executionContextAccessor: ExecutionContextAccessor,
    ): JpaUnitOfWork = JpaUnitOfWork(
        domainEventManager = domainEventManager,
        integrationEventManager = integrationEventManager.getIfUnique(),
        lifecycleInvoker = lifecycleInvoker,
        generatedOwnIdRegistry = generatedOwnIdRegistry,
        auditEnrichers = auditEnrichers,
        clock = clock.getIfAvailable { Clock.systemUTC() },
        executionContextAccessor = executionContextAccessor,
        limits = JpaUnitOfWorkLimits(
            maxFrontierRounds = jpaUnitOfWorkProperties.maxFrontierRounds,
            maxSynchronousEvents = jpaUnitOfWorkProperties.maxSynchronousEvents,
            maxNestedCommands = jpaUnitOfWorkProperties.maxNestedCommands,
            maxProviderFlushes = jpaUnitOfWorkProperties.maxProviderFlushes,
        ),
    ).also { JpaQueryUtils.configure(it, jpaUnitOfWorkProperties.retrieveCountWarnThreshold) }

    @Bean
    @ConditionalOnMissingBean(QueryExecution::class)
    fun jpaQueryExecution(): JpaQueryExecution = JpaQueryExecution()

    @Bean
    @ConditionalOnMissingBean(AggregateLifecycleInvoker::class)
    fun aggregateLifecycleInvoker(): AggregateLifecycleInvoker = ReflectiveAggregateLifecycleInvoker()
}
