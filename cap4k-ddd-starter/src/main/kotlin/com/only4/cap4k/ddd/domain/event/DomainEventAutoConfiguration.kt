package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.*
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventPublisher
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventSubscriberManager
import com.only4.cap4k.ddd.core.domain.event.impl.DomainEventUnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.impl.DefaultEventInterceptorManager
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import com.only4.cap4k.ddd.domain.event.configure.EventProperties
import com.only4.cap4k.ddd.domain.event.configure.EventScheduleProperties
import com.only4.cap4k.ddd.domain.event.persistence.ArchivedEventJpaRepository
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 基于JPA的领域事件（集成事件）实现自动配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@EnableJpaRepositories(
    basePackages = [
        "com.only4.cap4k.ddd.domain.event.persistence"
    ]
)
@EntityScan(
    basePackages = [
        "com.only4.cap4k.ddd.domain.event.persistence"
    ]
)
class DomainEventAutoConfiguration {

    companion object {
        const val CONFIG_KEY_4_EVENT_COMPENSE_LOCKER_KEY = "event_compense[$CONFIG_KEY_4_SVC_NAME]"
        const val CONFIG_KEY_4_EVENT_ARCHIVE_LOCKER_KEY = "event_archive[$CONFIG_KEY_4_SVC_NAME]"
    }

    @Bean
    fun defaultDomainEventSupervisor(
        eventRecordRepository: EventRecordRepository,
        domainEventInterceptorManager: DomainEventInterceptorManager,
        eventPublisher: EventPublisher,
        applicationEventPublisher: ApplicationEventPublisher,
        @Value(CONFIG_KEY_4_SVC_NAME) svcName: String,
    ): DefaultDomainEventSupervisor {
        return DefaultDomainEventSupervisor(
            eventRecordRepository,
            domainEventInterceptorManager,
            eventPublisher,
            applicationEventPublisher,
            svcName
        ).apply {
            DomainEventSupervisorSupport.configure(this as DomainEventSupervisor)
            DomainEventSupervisorSupport.configure(this as DomainEventManager)
        }
    }

    @Bean
    fun domainEventUnitOfWorkInterceptor(domainEventManager: DomainEventManager): DomainEventUnitOfWorkInterceptor {
        return DomainEventUnitOfWorkInterceptor(domainEventManager)
    }

    @Bean
    @ConditionalOnMissingBean(EventRecordRepository::class)
    fun jpaEventRecordRepository(
        eventJpaRepository: EventJpaRepository,
        archivedEventJpaRepository: ArchivedEventJpaRepository
    ): JpaEventRecordRepository {
        return JpaEventRecordRepository(
            eventJpaRepository,
            archivedEventJpaRepository
        )
    }

    @Bean
    @ConditionalOnMissingBean(JpaEventScheduleService::class)
    fun jpaEventScheduleService(
        eventPublisher: EventPublisher,
        eventRecordRepository: EventRecordRepository,
        locker: Locker,
        @Value(CONFIG_KEY_4_SVC_NAME) svcName: String,
        @Value(CONFIG_KEY_4_EVENT_COMPENSE_LOCKER_KEY) compensationLockerKey: String,
        @Value(CONFIG_KEY_4_EVENT_ARCHIVE_LOCKER_KEY) archiveLockerKey: String,
        eventScheduleProperties: EventScheduleProperties,
        jdbcTemplate: JdbcTemplate,
    ): JpaEventScheduleService {
        return JpaEventScheduleService(
            eventPublisher,
            eventRecordRepository,
            locker,
            svcName,
            compensationLockerKey,
            archiveLockerKey,
            eventScheduleProperties.addPartitionEnable,
            jdbcTemplate
        ).apply {
            init()
        }
    }

    /**
     * 领域事件定时补偿任务
     */
    @Service
    @EnableScheduling
    private class DomainEventScheduleLoader(
        private val eventScheduleProperties: EventScheduleProperties,
        private val scheduleService: JpaEventScheduleService?
    ) {
        companion object {
            private const val CONFIG_KEY_4_COMPENSE_CRON =
                "\${cap4k.ddd.domain.event.schedule.compenseCron:\${cap4k.ddd.domain.event.schedule.compense-cron:0 * * * * ?}}"
            private const val CONFIG_KEY_4_ARCHIVE_CRON =
                "\${cap4k.ddd.domain.event.schedule.archiveCron:\${cap4k.ddd.domain.event.schedule.archive-cron:0 0 2 * * ?}}"
            private const val CONFIG_KEY_4_ADD_PARTITION_CRON =
                "\${cap4k.ddd.domain.event.schedule.addPartitionCron:\${cap4k.ddd.domain.event.schedule.add-partition-cron:0 0 0 * * ?}}"
        }

        @Scheduled(cron = CONFIG_KEY_4_COMPENSE_CRON)
        fun compensation() {
            scheduleService?.compense(
                eventScheduleProperties.compenseBatchSize,
                eventScheduleProperties.compenseMaxConcurrency,
                Duration.ofSeconds(eventScheduleProperties.compenseIntervalSeconds.toLong()),
                Duration.ofSeconds(eventScheduleProperties.compenseMaxLockSeconds.toLong())
            )
        }

        @Scheduled(cron = CONFIG_KEY_4_ARCHIVE_CRON)
        fun archive() {
            scheduleService?.archive(
                eventScheduleProperties.archiveExpireDays,
                eventScheduleProperties.archiveBatchSize,
                Duration.ofSeconds(eventScheduleProperties.archiveMaxLockSeconds.toLong())
            )
        }

        @Scheduled(cron = CONFIG_KEY_4_ADD_PARTITION_CRON)
        fun addTablePartition() {
            scheduleService?.addPartition()
        }
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher::class)
    fun defaultEventPublisher(
        eventSubscriberManager: EventSubscriberManager,
        integrationEventPublishers: List<IntegrationEventPublisher>,
        eventRecordRepository: EventRecordRepository,
        eventMessageInterceptorManager: EventMessageInterceptorManager,
        domainEventInterceptorManager: DomainEventInterceptorManager,
        integrationEventInterceptorManager: IntegrationEventInterceptorManager,
        integrationEventPublishCallback: IntegrationEventPublisher.PublishCallback,
        eventProperties: EventProperties
    ): DefaultEventPublisher {
        return DefaultEventPublisher(
            eventSubscriberManager,
            integrationEventPublishers,
            eventRecordRepository,
            eventMessageInterceptorManager,
            domainEventInterceptorManager,
            integrationEventInterceptorManager,
            integrationEventPublishCallback,
            eventProperties.publisherThreadPoolSize
        ).apply {
            init()
        }
    }

    @Bean
    @ConditionalOnMissingBean(EventSubscriberManager::class)
    fun defaultEventSubscriberManager(
        subscribers: List<EventSubscriber<*>>,
        applicationEventPublisher: ApplicationEventPublisher,
        eventProperties: EventProperties
    ): DefaultEventSubscriberManager {
        return DefaultEventSubscriberManager(
            subscribers,
            applicationEventPublisher,
            eventProperties.eventScanPackage
        ).apply {
            init()
        }
    }

    @Bean
    @ConditionalOnMissingBean(DefaultEventInterceptorManager::class)
    fun defaultEventInterceptorManager(
        eventMessageInterceptors: List<EventMessageInterceptor>,
        interceptors: List<EventInterceptor>,
        eventRecordRepository: EventRecordRepository
    ): DefaultEventInterceptorManager {
        return DefaultEventInterceptorManager(
            eventMessageInterceptors,
            interceptors,
            eventRecordRepository
        )
    }
}
