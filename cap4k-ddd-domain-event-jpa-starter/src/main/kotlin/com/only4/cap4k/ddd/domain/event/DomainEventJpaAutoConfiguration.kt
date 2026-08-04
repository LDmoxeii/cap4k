package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventPublisher
import com.only4.cap4k.ddd.domain.event.configure.EventProperties
import com.only4.cap4k.ddd.domain.event.configure.EventScheduleProperties
import com.only4.cap4k.ddd.domain.event.persistence.ArchivedEventJpaRepository
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration

@AutoConfiguration
@EnableScheduling
@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd.domain.event.persistence"])
@EntityScan(basePackages = ["com.only4.cap4k.ddd.domain.event.persistence"])
@EnableConfigurationProperties(EventProperties::class, EventScheduleProperties::class)
class DomainEventJpaAutoConfiguration {
    companion object {
        const val RETRY_LOCKER_KEY = "event_retry[$CONFIG_KEY_4_SVC_NAME]"
        const val ARCHIVE_LOCKER_KEY = "event_archive[$CONFIG_KEY_4_SVC_NAME]"
    }

    @Bean
    @ConditionalOnMissingBean(EventRecordRepository::class)
    fun eventRecordRepository(
        eventJpaRepository: EventJpaRepository,
        archivedEventJpaRepository: ArchivedEventJpaRepository,
    ): JpaEventRecordRepository = JpaEventRecordRepository(eventJpaRepository, archivedEventJpaRepository)

    @Bean
    fun reliableEventInfrastructure(
        messageInterceptors: List<EventMessageInterceptor>,
        eventInterceptors: List<EventInterceptor>,
        eventRecordRepository: EventRecordRepository,
    ): ReliableEventInfrastructure = ReliableEventInfrastructure(
        messageInterceptors,
        eventInterceptors,
        eventRecordRepository,
    )

    @Bean
    @ConditionalOnMissingBean(EventPublisher::class)
    fun eventPublisher(
        eventHandlerDispatcher: EventHandlerDispatcher,
        integrationEventPublishers: List<IntegrationEventPublisher>,
        eventRecordRepository: EventRecordRepository,
        infrastructure: ReliableEventInfrastructure,
        domainEventInterceptorManager: DomainEventInterceptorManager,
        integrationEventManagerProvider: ObjectProvider<IntegrationEventManager>,
        executionContextScopeManager: ExecutionContextScopeManager,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        reliableEventDeliveryContextScopeManager: ReliableEventDeliveryContextScopeManager,
        properties: EventProperties,
    ): DefaultEventPublisher = DefaultEventPublisher(
        eventHandlerDispatcher,
        integrationEventPublishers,
        eventRecordRepository,
        infrastructure,
        domainEventInterceptorManager,
        infrastructure,
        integrationEventManagerProvider.getIfUnique(),
        infrastructure,
        properties.publisherThreadPoolSize,
        executionContextScopeManager,
        executionContextCodecRegistry,
        reliableEventDeliveryContextScopeManager,
    ).apply { init() }

    @Bean
    @ConditionalOnMissingBean(ReliableDomainEventProvider::class)
    fun reliableDomainEventProvider(
        eventRecordRepository: EventRecordRepository,
        domainEventInterceptorManager: DomainEventInterceptorManager,
        eventPublisher: EventPublisher,
        applicationEventPublisher: ApplicationEventPublisher,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
    ): JpaReliableDomainEventProvider = JpaReliableDomainEventProvider(
        eventRecordRepository,
        domainEventInterceptorManager,
        eventPublisher,
        applicationEventPublisher,
        serviceName,
        executionContextCodecRegistry,
    )

    @Bean
    fun eventScheduleService(
        eventPublisher: EventPublisher,
        eventRecordRepository: EventRecordRepository,
        locker: Locker,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        @Value(RETRY_LOCKER_KEY) retryLockerKey: String,
        @Value(ARCHIVE_LOCKER_KEY) archiveLockerKey: String,
        properties: EventScheduleProperties,
        jdbcTemplate: JdbcTemplate,
    ): JpaEventScheduleService = JpaEventScheduleService(
        eventPublisher,
        eventRecordRepository,
        locker,
        serviceName,
        retryLockerKey,
        archiveLockerKey,
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
        fun retry() = service.retry(
            properties.retryBatchSize,
            Duration.ofSeconds(properties.retryIntervalSeconds.toLong()),
            Duration.ofSeconds(properties.retryMaxLockSeconds.toLong()),
        )

        @Scheduled(cron = "\${cap4k.ddd.domain.event.schedule.archiveCron:\${cap4k.ddd.domain.event.schedule.archive-cron:0 0 2 * * ?}}")
        fun archive() = service.archive(
            properties.archiveExpireDays,
            properties.archiveBatchSize,
            Duration.ofSeconds(properties.archiveMaxLockSeconds.toLong()),
        )

        @Scheduled(cron = "\${cap4k.ddd.domain.event.schedule.addPartitionCron:\${cap4k.ddd.domain.event.schedule.add-partition-cron:0 0 0 * * ?}}")
        fun addPartition() = service.addPartition()
    }

}
