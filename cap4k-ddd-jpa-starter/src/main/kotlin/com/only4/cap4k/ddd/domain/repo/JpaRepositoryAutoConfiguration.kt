package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.repo.PersistListener
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.repo.impl.DefaultEntityInlinePersistListener
import com.only4.cap4k.ddd.core.domain.repo.impl.DefaultPersistListenerManager
import com.only4.cap4k.ddd.domain.repo.configure.JpaUnitOfWorkProperties
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
    ): DefaultAggregateFactorySupervisor = DefaultAggregateFactorySupervisor(
        factories,
        unitOfWork,
    ).apply {
        init()
    }

    @Bean
    @ConditionalOnMissingBean(UnitOfWork::class)
    fun jpaUnitOfWork(
        unitOfWorkInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        jpaUnitOfWorkProperties: JpaUnitOfWorkProperties,
        generatedOwnIdRegistry: GeneratedOwnIdRegistry,
    ): JpaUnitOfWork = JpaUnitOfWork(
        unitOfWorkInterceptors,
        persistListenerManager,
        jpaUnitOfWorkProperties.supportEntityInlinePersistListener,
        generatedOwnIdRegistry,
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
    @ConditionalOnMissingBean(PersistListenerManager::class)
    fun defaultPersistListenerManager(
        persistListeners: List<PersistListener<*>>,
    ): DefaultPersistListenerManager = DefaultPersistListenerManager(persistListeners).apply {
        init()
    }

    @Bean
    @ConditionalOnMissingBean(DefaultEntityInlinePersistListener::class)
    @ConditionalOnProperty(
        name = ["cap4k.ddd.application.jpa-uow.supportEntityInlinePersistListener"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun defaultEntityInlinePersistListener(): DefaultEntityInlinePersistListener =
        DefaultEntityInlinePersistListener()
}
