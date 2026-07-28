package com.only4.cap4k.ddd.application.request

import com.only4.cap4k.ddd.application.JpaRequestRecordRepository
import com.only4.cap4k.ddd.application.JpaRequestScheduleService
import com.only4.cap4k.ddd.application.persistence.ArchivedRequestJpaRepository
import com.only4.cap4k.ddd.application.persistence.RequestJpaRepository
import com.only4.cap4k.ddd.application.request.configure.RequestProperties
import com.only4.cap4k.ddd.application.request.configure.RequestScheduleProperties
import com.only4.cap4k.ddd.core.application.ReliableRequestSupervisor
import com.only4.cap4k.ddd.core.application.RequestManager
import com.only4.cap4k.ddd.core.application.RequestRecordRepository
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.RequestSupervisorSupport
import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.impl.DefaultReliableRequestSupervisor
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import jakarta.validation.Validator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration

@AutoConfiguration
@EnableScheduling
@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd.application.persistence"])
@EntityScan(basePackages = ["com.only4.cap4k.ddd.application.persistence"])
@EnableConfigurationProperties(RequestProperties::class, RequestScheduleProperties::class)
class RequestJpaAutoConfiguration {
    companion object {
        const val COMPENSATION_LOCKER_KEY = "request_compense[$CONFIG_KEY_4_SVC_NAME]"
        const val ARCHIVE_LOCKER_KEY = "request_archive[$CONFIG_KEY_4_SVC_NAME]"
    }

    @Bean
    @ConditionalOnMissingBean(RequestRecordRepository::class)
    fun jpaRequestRecordRepository(
        requestJpaRepository: RequestJpaRepository,
        archivedRequestJpaRepository: ArchivedRequestJpaRepository,
    ): JpaRequestRecordRepository = JpaRequestRecordRepository(requestJpaRepository, archivedRequestJpaRepository)

    @Bean
    @ConditionalOnMissingBean(ReliableRequestSupervisor::class)
    fun reliableRequestSupervisor(
        requestSupervisor: RequestSupervisor,
        validatorProvider: ObjectProvider<Validator>,
        requestRecordRepository: RequestRecordRepository,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        properties: RequestProperties,
    ): DefaultReliableRequestSupervisor = DefaultReliableRequestSupervisor(
        requestSupervisor = requestSupervisor,
        validator = validatorProvider.getIfAvailable(),
        requestRecordRepository = requestRecordRepository,
        svcName = serviceName,
        threadPoolSize = properties.requestScheduleThreadPoolSize,
        threadFactoryClassName = properties.requestScheduleThreadFactoryClassName,
    ).apply {
        init()
        RequestSupervisorSupport.configure(this as ReliableRequestSupervisor)
        RequestSupervisorSupport.configure(this as RequestManager)
    }

    @Bean
    fun jpaRequestScheduleService(
        requestManager: RequestManager,
        locker: Locker,
        @Value(COMPENSATION_LOCKER_KEY) compensationLockerKey: String,
        @Value(ARCHIVE_LOCKER_KEY) archiveLockerKey: String,
        properties: RequestScheduleProperties,
        jdbcTemplate: JdbcTemplate,
    ): JpaRequestScheduleService = JpaRequestScheduleService(
        requestManager,
        locker,
        compensationLockerKey,
        archiveLockerKey,
        properties.addPartitionEnable,
        jdbcTemplate,
    ).apply { init() }

    @Bean
    fun requestScheduleTasks(
        scheduleService: JpaRequestScheduleService,
        properties: RequestScheduleProperties,
    ): RequestScheduleTasks = RequestScheduleTasks(scheduleService, properties)

    class RequestScheduleTasks(
        private val scheduleService: JpaRequestScheduleService,
        private val properties: RequestScheduleProperties,
    ) {
        @Scheduled(cron = "\${cap4k.ddd.application.request.schedule.compenseCron:\${cap4k.ddd.application.request.schedule.compense-cron:0 * * * * ?}}")
        fun compensation() = scheduleService.compense(
            properties.compenseBatchSize,
            properties.compenseMaxConcurrency,
            Duration.ofSeconds(properties.compenseIntervalSeconds.toLong()),
            Duration.ofSeconds(properties.compenseMaxLockSeconds.toLong()),
        )

        @Scheduled(cron = "\${cap4k.ddd.application.request.schedule.archiveCron:\${cap4k.ddd.application.request.schedule.archive-cron:0 0 2 * * ?}}")
        fun archive() = scheduleService.archive(
            properties.archiveExpireDays,
            properties.archiveBatchSize,
            Duration.ofSeconds(properties.archiveMaxLockSeconds.toLong()),
        )

        @Scheduled(cron = "\${cap4k.ddd.application.request.schedule.addPartitionCron:\${cap4k.ddd.application.request.schedule.add-partition-cron:0 0 0 * * ?}}")
        fun addPartition() = scheduleService.addPartition()
    }
}
