package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.application.UnitOfWorkSupport
import com.only4.cap4k.ddd.core.domain.aggregate.*
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultSpecificationManager
import com.only4.cap4k.ddd.core.domain.aggregate.impl.SpecificationUnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.repo.PersistListener
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisorSupport
import com.only4.cap4k.ddd.core.domain.repo.impl.DefaultEntityInlinePersistListener
import com.only4.cap4k.ddd.core.domain.repo.impl.DefaultPersistListenerManager
import com.only4.cap4k.ddd.domain.aggregate.impl.DefaultAggregateSupervisor
import com.only4.cap4k.ddd.domain.event.configure.EventProperties
import com.only4.cap4k.ddd.domain.repo.configure.JpaUnitOfWorkProperties
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 基于Jpa的仓储实现自动配置类
 *
 * DefaultPersistListenerManager
 * DefaultSpecificationManager
 * JpaUnitOfWork
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
open class JpaRepositoryAutoConfiguration {

    @Bean
    open fun defaultRepositorySupervisor(
        repositories: List<Repository<*>>,
        unitOfWork: JpaUnitOfWork
    ): DefaultRepositorySupervisor {
        return DefaultRepositorySupervisor(repositories, unitOfWork).apply {
            init()
            RepositorySupervisorSupport.configure(this)
        }
    }

    @Bean
    open fun defaultAggregateSupervisor(
        repositorySupervisor: DefaultRepositorySupervisor,
        jpaUnitOfWork: JpaUnitOfWork
    ): DefaultAggregateSupervisor {
        return DefaultAggregateSupervisor(
            repositorySupervisor,
            jpaUnitOfWork
        ).apply {
            AggregateSupervisorSupport.configure(this)
        }
    }

    @Bean
    open fun defaultAggregateFactorySupervisor(
        factories: List<AggregateFactory<*, *>>,
        jpaUnitOfWork: JpaUnitOfWork
    ): DefaultAggregateFactorySupervisor {
        return DefaultAggregateFactorySupervisor(
            factories,
            jpaUnitOfWork
        ).apply {
            init()
            AggregateFactorySupervisorSupport.configure(this)
        }
    }

    @Bean
    open fun jpaUnitOfWork(
        unitOfWorkInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        jpaUnitOfWorkProperties: JpaUnitOfWorkProperties
    ): JpaUnitOfWork {
        return JpaUnitOfWork(
            unitOfWorkInterceptors,
            persistListenerManager,
            jpaUnitOfWorkProperties.supportEntityInlinePersistListener,
            jpaUnitOfWorkProperties.supportValueObjectExistsCheckOnSave
        ).apply {
            UnitOfWorkSupport.configure(this)
            JpaQueryUtils.configure(this, jpaUnitOfWorkProperties.retrieveCountWarnThreshold)
            Md5HashIdentifierGenerator.configure(jpaUnitOfWorkProperties.generalIdFieldName)
        }
    }

    @Configuration
    open class JpaUnitOfWorkLoader(
        @Autowired(required = false) jpaUnitOfWork: JpaUnitOfWork?
    ) {
        init {
            if (jpaUnitOfWork != null) {
                JpaUnitOfWork.fixAopWrapper(jpaUnitOfWork)
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(PersistListenerManager::class)
    open fun defaultPersistListenerManager(
        persistListeners: List<PersistListener<*>>,
        eventProperties: EventProperties
    ): DefaultPersistListenerManager {
        return DefaultPersistListenerManager(
            persistListeners,
            eventProperties.eventScanPackage
        ).apply {
            init()
        }
    }

    @Bean
    @ConditionalOnMissingBean(SpecificationManager::class)
    open fun defaultSpecificationManager(specifications: List<Specification<*>>): DefaultSpecificationManager {
        return DefaultSpecificationManager(specifications).apply {
            init()
        }
    }

    @Bean
    open fun specificationUnitOfWorkInterceptor(specificationManager: SpecificationManager): SpecificationUnitOfWorkInterceptor {
        return SpecificationUnitOfWorkInterceptor(specificationManager)
    }

    @Bean
    @ConditionalOnProperty(
        name = ["cap4k.ddd.application.jpa-uow.supportEntityInlinePersistListener"],
        havingValue = "true",
        matchIfMissing = true
    )
    open fun defaultEntityInlinePersistListener(): DefaultEntityInlinePersistListener {
        return DefaultEntityInlinePersistListener()
    }
}
