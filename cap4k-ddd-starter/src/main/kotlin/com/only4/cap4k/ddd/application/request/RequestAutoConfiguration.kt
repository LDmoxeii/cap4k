package com.only4.cap4k.ddd.application.request

import com.only4.cap4k.ddd.application.JpaRequestRecordRepository
import com.only4.cap4k.ddd.application.JpaRequestScheduleService
import com.only4.cap4k.ddd.application.persistence.ArchivedRequestJpaRepository
import com.only4.cap4k.ddd.application.persistence.RequestJpaRepository
import com.only4.cap4k.ddd.application.request.configure.RequestProperties
import com.only4.cap4k.ddd.application.request.configure.RequestScheduleProperties
import com.only4.cap4k.ddd.core.application.*
import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.impl.DefaultRequestSupervisor
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import jakarta.validation.Validator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 请求自动配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@EnableJpaRepositories(
    basePackages = [
        "com.only4.cap4k.ddd.application.persistence"
    ]
)
@EntityScan(
    basePackages = [
        "com.only4.cap4k.ddd.application.persistence"
    ]
)
class RequestAutoConfiguration {

    companion object {
        const val CONFIG_KEY_4_REQUEST_COMPENSE_LOCKER_KEY = "request_compense[$CONFIG_KEY_4_SVC_NAME]"
        const val CONFIG_KEY_4_REQUEST_ARCHIVE_LOCKER_KEY = "request_archive[$CONFIG_KEY_4_SVC_NAME]"
    }

    @Bean
    @ConditionalOnMissingBean(RequestRecordRepository::class)
    fun jpaRequestRecordRepository(
        requestJpaRepository: RequestJpaRepository,
        archivedRequestJpaRepository: ArchivedRequestJpaRepository
    ): JpaRequestRecordRepository {
        return JpaRequestRecordRepository(requestJpaRepository, archivedRequestJpaRepository)
    }

    @Bean
    fun defaultRequestSupervisor(
        requestHandlers: List<RequestHandler<*, *>>,
        requestInterceptors: List<RequestInterceptor<*, *>>,
        @Autowired(required = false) validator: Validator?,
        requestProperties: RequestProperties,
        requestRecordRepository: RequestRecordRepository,
        @Value(CONFIG_KEY_4_SVC_NAME) svcName: String
    ): DefaultRequestSupervisor {
        return DefaultRequestSupervisor(
            requestHandlers,
            requestInterceptors,
            validator,
            requestRecordRepository,
            svcName,
            requestProperties.requestScheduleThreadPoolSize,
            requestProperties.requestScheduleThreadFactoryClassName
        ).apply {
            init()
            RequestSupervisorSupport.configure(this as RequestSupervisor)
            RequestSupervisorSupport.configure(this as RequestManager)
        }
    }

    @Bean
    fun jpaRequestScheduleService(
        requestManager: RequestManager,
        locker: Locker,
        @Value(CONFIG_KEY_4_REQUEST_COMPENSE_LOCKER_KEY) compensationLockerKey: String,
        @Value(CONFIG_KEY_4_REQUEST_ARCHIVE_LOCKER_KEY) archiveLockerKey: String,
        requestScheduleProperties: RequestScheduleProperties,
        jdbcTemplate: JdbcTemplate
    ): JpaRequestScheduleService {
        return JpaRequestScheduleService(
            requestManager,
            locker,
            compensationLockerKey,
            archiveLockerKey,
            requestScheduleProperties.addPartitionEnable,
            jdbcTemplate
        ).apply {
            init()
        }
    }

    /**
     * Request定时补偿任务
     */
    @Service
    @EnableScheduling
    private class RequestScheduleLoader(
        private val requestScheduleProperties: RequestScheduleProperties,
        private val scheduleService: JpaRequestScheduleService
    ) {
        companion object {
            private const val CONFIG_KEY_4_COMPENSE_CRON =
                "\${cap4k.ddd.application.request.schedule.compenseCron:\${cap4k.ddd.application.request.schedule.compense-cron:0 * * * * ?}}"
            private const val CONFIG_KEY_4_ARCHIVE_CRON =
                "\${cap4k.ddd.application.request.schedule.archiveCron:\${cap4k.ddd.application.request.schedule.archive-cron:0 0 2 * * ?}}"
            private const val CONFIG_KEY_4_ADD_PARTITION_CRON =
                "\${cap4k.ddd.application.request.schedule.addPartitionCron:\${cap4k.ddd.application.request.schedule.add-partition-cron:0 0 0 * * ?}}"
        }

        @Scheduled(cron = CONFIG_KEY_4_COMPENSE_CRON)
        fun compensation() {
            scheduleService.compense(
                requestScheduleProperties.compenseBatchSize,
                requestScheduleProperties.compenseMaxConcurrency,
                Duration.ofSeconds(requestScheduleProperties.compenseIntervalSeconds.toLong()),
                Duration.ofSeconds(requestScheduleProperties.compenseMaxLockSeconds.toLong())
            )
        }

        @Scheduled(cron = CONFIG_KEY_4_ARCHIVE_CRON)
        fun archive() {
            scheduleService.archive(
                requestScheduleProperties.archiveExpireDays,
                requestScheduleProperties.archiveBatchSize,
                Duration.ofSeconds(requestScheduleProperties.archiveMaxLockSeconds.toLong())
            )
        }

        @Scheduled(cron = CONFIG_KEY_4_ADD_PARTITION_CRON)
        fun addTablePartition() {
            scheduleService.addPartition()
        }
    }
}
