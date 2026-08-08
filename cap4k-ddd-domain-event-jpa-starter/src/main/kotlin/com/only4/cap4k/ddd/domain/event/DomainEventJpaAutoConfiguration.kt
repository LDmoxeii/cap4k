package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.autoconfigure.RuntimeProviderComposition
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventPublisher
import com.only4.cap4k.ddd.domain.event.configure.EventScheduleProperties
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration

@AutoConfiguration
@EnableScheduling
@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd.domain.event.persistence"])
@EntityScan(basePackages = ["com.only4.cap4k.ddd.domain.event.persistence"])
@EnableConfigurationProperties(EventScheduleProperties::class)
class DomainEventJpaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(EventRecordRepository::class)
    fun eventRecordRepository(
        eventJpaRepository: EventJpaRepository,
    ): JpaEventRecordRepository = JpaEventRecordRepository(eventJpaRepository)

    @Bean
    @ConditionalOnMissingBean(JpaEventExecutionSubstrate::class)
    fun jpaEventExecutionSubstrate(
        eventJpaRepository: EventJpaRepository,
    ): JpaEventExecutionSubstrate = JpaEventExecutionSubstrate(eventJpaRepository)
    @Bean
    fun reliableEventInfrastructure(
        messageInterceptors: List<EventMessageInterceptor>,
        eventInterceptors: List<EventInterceptor>,
    ): ReliableEventInfrastructure = ReliableEventInfrastructure(
        messageInterceptors,
        eventInterceptors,
    )

    @Bean
    @ConditionalOnMissingBean(EventPublisher::class)
    fun eventPublisher(
        eventHandlerDispatcher: EventHandlerDispatcher,
        integrationEventPublishers: List<IntegrationEventPublisher>,
        infrastructure: ReliableEventInfrastructure,
        domainEventInterceptorManager: DomainEventInterceptorManager,
        beanFactory: ListableBeanFactory,
        executionContextScopeManager: ExecutionContextScopeManager,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
    ): DefaultEventPublisher = DefaultEventPublisher(
        eventHandlerDispatcher,
        integrationEventPublishers,
        infrastructure,
        domainEventInterceptorManager,
        infrastructure,
        RuntimeProviderComposition.optional(
            beanFactory,
            IntegrationEventManager::class.java,
            "integration-event-manager",
        ),
        executionContextScopeManager,
        executionContextCodecRegistry,
        reliableEventDeliveryContextScopeManager,
    )

    @Bean
    @ConditionalOnMissingBean(ReliableDomainEventProvider::class)
    fun reliableDomainEventProvider(
        eventRecordRepository: EventRecordRepository,
        domainEventInterceptorManager: DomainEventInterceptorManager,
        reliableEventCoordinator: ReliableEventCoordinator,
        applicationEventPublisher: ApplicationEventPublisher,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
    ): JpaReliableDomainEventProvider = JpaReliableDomainEventProvider(
        eventRecordRepository,
        domainEventInterceptorManager,
        reliableEventCoordinator,
        applicationEventPublisher,
        serviceName,
        executionContextCodecRegistry,
    )

    @Bean(destroyMethod = "shutdown")
    fun eventScheduleService(
        @Lazy eventPublisher: EventPublisher,
        executionSubstrate: JpaEventExecutionSubstrate,
        eventJpaRepository: EventJpaRepository,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        properties: EventScheduleProperties,
        jdbcTemplate: JdbcTemplate,
    ): JpaEventScheduleService = JpaEventScheduleService(
        eventPublisher,
        executionSubstrate,
        eventJpaRepository,
        serviceName,
        properties.retryBatchSize,
        Duration.ofSeconds(properties.deliveryLeaseSeconds.toLong()),
        Duration.ofSeconds(properties.deliveryLeaseRenewSeconds.toLong()),
        properties.workerThreads,
        properties.addPartitionEnable,
        jdbcTemplate,
    ).apply { init() }

    @Bean
    fun eventScheduleTasks(
        service: JpaEventScheduleService,
        properties: EventScheduleProperties,
    ): EventScheduleTasks = EventScheduleTasks(service, properties)

    class EventScheduleTasks(
        private val service: JpaEventScheduleService,
        private val properties: EventScheduleProperties,
    ) {
        @Scheduled(cron = "\${cap4k.ddd.domain.event.schedule.retryCron:\${cap4k.ddd.domain.event.schedule.retry-cron:0 * * * * ?}}")
        fun retry() = service.retry()


        @Scheduled(cron = "\${cap4k.ddd.domain.event.schedule.addPartitionCron:\${cap4k.ddd.domain.event.schedule.add-partition-cron:0 0 0 * * ?}}")
        fun addPartition() = service.addPartition()
    }

}
