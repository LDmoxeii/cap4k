package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.configure.SagaProperties
import com.only4.cap4k.ddd.application.saga.configure.SagaScheduleProperties
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.SagaJpaRepository
import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestInterceptor
import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.saga.*
import com.only4.cap4k.ddd.core.application.saga.impl.DefaultSagaSupervisor
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
 * Saga 自动配置
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@EnableJpaRepositories(
    basePackages = [
        "com.only4.cap4k.ddd.application.saga.persistence"
    ]
)
@EntityScan(
    basePackages = [
        "com.only4.cap4k.ddd.application.saga.persistence"
    ]
)
class SagaAutoConfiguration {

    companion object {
        const val CONFIG_KEY_4_SAGA_COMPENSE_LOCKER_KEY = "saga_compense[$CONFIG_KEY_4_SVC_NAME]"
        const val CONFIG_KEY_4_SAGA_ARCHIVE_LOCKER_KEY = "saga_archive[$CONFIG_KEY_4_SVC_NAME]"
    }

    @Bean
    fun defaultSagaSupervisor(
        requestHandlers: List<RequestHandler<*, *>>,
        requestInterceptors: List<RequestInterceptor<*, *>>,
        @Autowired(required = false) validator: Validator?,
        sagaRecordRepository: SagaRecordRepository,
        @Value(CONFIG_KEY_4_SVC_NAME) svcName: String,
        sagaProperties: SagaProperties,
    ): DefaultSagaSupervisor {
        return DefaultSagaSupervisor(
            requestHandlers,
            requestInterceptors,
            validator,
            sagaRecordRepository,
            svcName,
            sagaProperties.asyncThreadPoolSize,
            sagaProperties.asyncThreadFactoryClassName
        ).apply {
            SagaSupervisorSupport.configure(this as SagaSupervisor)
            SagaSupervisorSupport.configure(this as SagaProcessSupervisor)
            SagaSupervisorSupport.configure(this as SagaManager)
            init()
        }
    }

    @Bean
    @ConditionalOnMissingBean(SagaRecordRepository::class)
    fun jpaSagaRecordRepository(
        sagaJpaRepository: SagaJpaRepository,
        archivedSagaJpaRepository: ArchivedSagaJpaRepository
    ): JpaSagaRecordRepository {
        return JpaSagaRecordRepository(
            sagaJpaRepository,
            archivedSagaJpaRepository
        )
    }

    @Bean
    fun jpaSagaScheduleService(
        sagaManager: SagaManager,
        locker: Locker,
        @Value("\$$CONFIG_KEY_4_SAGA_COMPENSE_LOCKER_KEY") compensationLockerKey: String,
        @Value("\$$CONFIG_KEY_4_SAGA_ARCHIVE_LOCKER_KEY") archiveLockerKey: String,
        sagaScheduleProperties: SagaScheduleProperties,
        jdbcTemplate: JdbcTemplate
    ): JpaSagaScheduleService {
        return JpaSagaScheduleService(
            sagaManager,
            locker,
            compensationLockerKey,
            archiveLockerKey,
            sagaScheduleProperties.addPartitionEnable,
            jdbcTemplate
        ).apply {
            init()
        }
    }

    /**
     * Saga定时补偿任务
     */
    @Service
    @EnableScheduling
    private class SagaScheduleLoader(
        private val sagaScheduleProperties: SagaScheduleProperties,
        private val scheduleService: JpaSagaScheduleService
    ) {
        companion object {
            private const val CONFIG_KEY_4_COMPENSE_CRON =
                "\${cap4k.ddd.application.saga.schedule.compenseCron:\${cap4k.ddd.application.saga.schedule.compense-cron:0 * * * * ?}}"
            private const val CONFIG_KEY_4_ARCHIVE_CRON =
                "\${cap4k.ddd.application.saga.schedule.archiveCron:\${cap4k.ddd.application.saga.schedule.archive-cron:0 0 2 * * ?}}"
            private const val CONFIG_KEY_4_ADD_PARTITION_CRON =
                "\${cap4k.ddd.application.saga.schedule.addPartitionCron:\${cap4k.ddd.application.saga.schedule.add-partition-cron:0 0 0 * * ?}}"
        }

        @Scheduled(cron = CONFIG_KEY_4_COMPENSE_CRON)
        fun compensation() {
            scheduleService.compense(
                sagaScheduleProperties.compenseBatchSize,
                sagaScheduleProperties.compenseMaxConcurrency,
                Duration.ofSeconds(sagaScheduleProperties.compenseIntervalSeconds.toLong()),
                Duration.ofSeconds(sagaScheduleProperties.compenseMaxLockSeconds.toLong())
            )
        }

        @Scheduled(cron = CONFIG_KEY_4_ARCHIVE_CRON)
        fun archive() {
            scheduleService.archive(
                sagaScheduleProperties.archiveExpireDays,
                sagaScheduleProperties.archiveBatchSize,
                Duration.ofSeconds(sagaScheduleProperties.archiveMaxLockSeconds.toLong())
            )
        }

        @Scheduled(cron = CONFIG_KEY_4_ADD_PARTITION_CRON)
        fun addTablePartition() {
            scheduleService.addPartition()
        }
    }
}
