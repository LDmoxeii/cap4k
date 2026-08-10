package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.ProviderUnavailableException
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.capability.CapabilityInterceptor
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.capability.impl.DefaultCapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandInterceptor
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.impl.DefaultCommandSupervisor
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.query.QueryInterceptor
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import com.only4.cap4k.ddd.core.application.query.impl.DefaultQuerySupervisor
import com.only4.cap4k.ddd.core.application.provider.InMemoryRuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.async.BoundedApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeManager
import com.only4.cap4k.ddd.core.domain.managed.DefaultManagedEntityAdmissionCoordinator
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionCoordinator
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldRegistry
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerDescriptorResolver
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerRegistry
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import com.only4.cap4k.ddd.core.domain.service.impl.DefaultDomainServiceSupervisor
import jakarta.validation.Validator
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Qualifier

@AutoConfiguration(after = [CoreIdAutoConfiguration::class])
@EnableConfigurationProperties(ApplicationExecutionProperties::class)
class CoreRuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RuntimeProviderStateRegistry::class)
    fun runtimeProviderStateRegistry(): RuntimeProviderStateRegistry = InMemoryRuntimeProviderStateRegistry()

    @Bean
    @ConditionalOnMissingBean(ExecutionContextAccessor::class)
    fun executionContextManager(): DefaultExecutionContextManager = DefaultExecutionContextManager()

    @Bean
    fun reliableEventDeliveryContextManager(
        executionContextAccessor: ExecutionContextAccessor,
        executionContextScopeManager: ExecutionContextScopeManager,
    ): DefaultReliableEventDeliveryContextManager = DefaultReliableEventDeliveryContextManager(
        executionContextAccessor,
        executionContextScopeManager,
    )

    @Bean
    @ConditionalOnMissingBean(ExecutionContextCodecRegistry::class)
    fun executionContextCodecRegistry(
        codecs: List<ExecutionContextElementCodec<*>>,
    ): ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(codecs)

    @Bean
    @ConditionalOnMissingBean(ExecutionContextPropagation::class)
    fun executionContextPropagation(
        accessor: ExecutionContextAccessor,
        scopeManager: ExecutionContextScopeManager,
    ): ExecutionContextPropagation = ExecutionContextPropagation(accessor, scopeManager)

    @Bean
    @ConditionalOnMissingBean(InvocationScopeAccessor::class)
    fun invocationScopeManager(): DefaultInvocationScopeManager = DefaultInvocationScopeManager()

    @Bean
    @ConditionalOnMissingBean(InvocationPolicy::class)
    fun invocationPolicy(scopeAccessor: InvocationScopeAccessor): InvocationPolicy = InvocationPolicy(scopeAccessor)

    @Bean
    @ConditionalOnMissingBean(ManagedEntityAdmissionCoordinator::class)
    fun managedEntityAdmissionCoordinator(
        registry: ManagedFieldRegistry,
        executionContextAccessor: ExecutionContextAccessor,
    ): ManagedEntityAdmissionCoordinator =
        DefaultManagedEntityAdmissionCoordinator(registry, executionContextAccessor)

    @Bean(name = [QUERY_ASYNC_EXECUTOR_BEAN])
    @ConditionalOnMissingBean(name = [QUERY_ASYNC_EXECUTOR_BEAN])
    fun queryAsyncExecutor(properties: ApplicationExecutionProperties): ApplicationAsyncExecutor =
        properties.query.toExecutor()

    @Bean(name = [CAPABILITY_ASYNC_EXECUTOR_BEAN])
    @ConditionalOnMissingBean(name = [CAPABILITY_ASYNC_EXECUTOR_BEAN])
    fun capabilityAsyncExecutor(properties: ApplicationExecutionProperties): ApplicationAsyncExecutor =
        properties.capability.toExecutor()

    @Bean
    @ConditionalOnMissingBean(CommandSupervisor::class)
    fun defaultCommandSupervisor(
        handlers: List<CommandHandler<*, *>>,
        interceptors: List<CommandInterceptor<*, *>>,
        validatorProvider: ObjectProvider<Validator>,
        unitOfWorkProvider: ObjectProvider<CommandUnitOfWorkCoordinator>,
        invocationPolicy: InvocationPolicy,
        invocationScopeManager: InvocationScopeManager,
    ): DefaultCommandSupervisor = DefaultCommandSupervisor(
        handlers,
        interceptors,
        validatorProvider.ifAvailable,
        unitOfWorkProvider = {
            unitOfWorkProvider.ifAvailable
                ?: throw ProviderUnavailableException("unit-of-work", "a cap4k Persistence Provider starter")
        },
        invocationPolicy = invocationPolicy,
        invocationScopeManager = invocationScopeManager,
    ).apply(DefaultCommandSupervisor::init)

    @Bean
    @ConditionalOnMissingBean(QuerySupervisor::class)
    fun defaultQuerySupervisor(
        handlers: List<QueryHandler<*, *>>,
        interceptors: List<QueryInterceptor<*, *>>,
        validatorProvider: ObjectProvider<Validator>,
        invocationPolicy: InvocationPolicy,
        invocationScopeManager: InvocationScopeManager,
        executionContextAccessor: ExecutionContextAccessor,
        executionContextPropagation: ExecutionContextPropagation,
        @Qualifier(QUERY_ASYNC_EXECUTOR_BEAN) asyncExecutor: ApplicationAsyncExecutor,
        queryExecutionProvider: ObjectProvider<QueryExecution>,
    ): DefaultQuerySupervisor = DefaultQuerySupervisor(
        handlers,
        interceptors,
        validatorProvider.ifAvailable,
        invocationPolicy,
        invocationScopeManager,
        executionContextAccessor,
        executionContextPropagation,
        asyncExecutor,
        queryExecutionProvider = {
            queryExecutionProvider.ifAvailable
                ?: throw ProviderUnavailableException("query-execution", "the cap4k JPA starter")
        },
    ).apply(DefaultQuerySupervisor::init)

    @Bean
    @ConditionalOnMissingBean(CapabilitySupervisor::class)
    fun defaultCapabilitySupervisor(
        handlers: List<CapabilityHandler<*, *>>,
        interceptors: List<CapabilityInterceptor<*, *>>,
        validatorProvider: ObjectProvider<Validator>,
        invocationPolicy: InvocationPolicy,
        invocationScopeManager: InvocationScopeManager,
        executionContextAccessor: ExecutionContextAccessor,
        executionContextPropagation: ExecutionContextPropagation,
        @Qualifier(CAPABILITY_ASYNC_EXECUTOR_BEAN) asyncExecutor: ApplicationAsyncExecutor,
    ): DefaultCapabilitySupervisor = DefaultCapabilitySupervisor(
        handlers,
        interceptors,
        validatorProvider.ifAvailable,
        invocationPolicy,
        invocationScopeManager,
        executionContextAccessor,
        executionContextPropagation,
        asyncExecutor,
    ).apply(DefaultCapabilitySupervisor::init)

    @Bean
    @ConditionalOnMissingBean(DomainServiceSupervisor::class)
    fun defaultDomainServiceSupervisor(applicationContext: ApplicationContext): DefaultDomainServiceSupervisor =
        DefaultDomainServiceSupervisor(applicationContext)

    @Bean
    fun cap4kEventHandlerDescriptorResolver(): Cap4kEventHandlerDescriptorResolver =
        Cap4kEventHandlerDescriptorResolver()

    @Bean
    fun cap4kEventHandlerRegistry(): Cap4kEventHandlerRegistry = Cap4kEventHandlerRegistry()

    @Bean
    @ConditionalOnMissingBean(EventTypeCatalog::class)
    fun eventTypeCatalog(
        beanFactory: ListableBeanFactory,
        descriptorResolver: Cap4kEventHandlerDescriptorResolver,
    ): EventTypeCatalog = SpringEventTypeCatalog(beanFactory, descriptorResolver)

    @Bean
    fun inboundIntegrationEventRegistrationView(
        eventTypeCatalog: EventTypeCatalog,
        beanFactory: ListableBeanFactory,
        descriptorResolver: Cap4kEventHandlerDescriptorResolver,
    ): InboundIntegrationEventRegistrationView = CatalogDerivedInboundIntegrationEventRegistrationView(
        eventTypeCatalog, beanFactory, descriptorResolver,
    )

    @Bean
    fun mediatorCapabilityBinder(
        applicationContext: ApplicationContext,
        beanFactory: ListableBeanFactory,
    ): RuntimeProviderBinder = RuntimeProviderBinder(applicationContext, beanFactory)

    private fun ApplicationExecutionProperties.AsyncExecutor.toExecutor(): BoundedApplicationAsyncExecutor =
        BoundedApplicationAsyncExecutor(
            workerCount = workers,
            queueCapacity = queueCapacity,
            overloadStrategy = overloadStrategy,
            threadNamePrefix = threadNamePrefix,
        )

    companion object {
        const val QUERY_ASYNC_EXECUTOR_BEAN = "cap4kQueryAsyncExecutor"
        const val CAPABILITY_ASYNC_EXECUTOR_BEAN = "cap4kCapabilityAsyncExecutor"
    }
}
