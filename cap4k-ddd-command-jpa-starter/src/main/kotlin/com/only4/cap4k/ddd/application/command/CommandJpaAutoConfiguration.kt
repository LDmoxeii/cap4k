package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.command.JpaCommandRecordRepository
import com.only4.cap4k.ddd.application.command.JpaCommandScheduleService
import com.only4.cap4k.ddd.application.command.JpaReliableCommandTransaction
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.application.command.configure.CommandProperties
import com.only4.cap4k.ddd.application.command.configure.CommandScheduleProperties
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisor
import com.only4.cap4k.ddd.core.application.command.CommandManager
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandTransaction
import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.command.impl.DefaultReliableCommandSupervisor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
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
@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd.application.command.persistence"])
@EntityScan(basePackages = ["com.only4.cap4k.ddd.application.command.persistence"])
@EnableConfigurationProperties(CommandProperties::class, CommandScheduleProperties::class)
class CommandJpaAutoConfiguration {
    companion object {
        const val RETRY_LOCKER_KEY = "command_retry[$CONFIG_KEY_4_SVC_NAME]"
    }

    @Bean
    @ConditionalOnMissingBean(CommandRecordRepository::class)
    fun jpaCommandRecordRepository(
        commandJpaRepository: CommandRecordJpaRepository,
    ): JpaCommandRecordRepository = JpaCommandRecordRepository(commandJpaRepository)

    @Bean
    @ConditionalOnMissingBean(JpaCommandExecutionSubstrate::class)
    fun jpaCommandExecutionSubstrate(
        commandJpaRepository: CommandRecordJpaRepository,
    ): JpaCommandExecutionSubstrate = JpaCommandExecutionSubstrate(commandJpaRepository)

    @Bean
    @ConditionalOnMissingBean(ReliableCommandSupervisor::class)
    fun reliableCommandSupervisor(
        commandSupervisor: CommandSupervisor,
        unitOfWork: CommandUnitOfWorkCoordinator,
        transaction: ReliableCommandTransaction,
        validatorProvider: ObjectProvider<Validator>,
        commandRecordRepository: CommandRecordRepository,
        executionContextAccessor: ExecutionContextAccessor,
        executionContextScopeManager: ExecutionContextScopeManager,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        invocationScopeAccessor: InvocationScopeAccessor,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        properties: CommandProperties,
    ): DefaultReliableCommandSupervisor = DefaultReliableCommandSupervisor(
        commandSupervisor = commandSupervisor,
        validator = validatorProvider.getIfAvailable(),
        commandRecordRepository = commandRecordRepository,
        unitOfWorkProvider = { unitOfWork },
        transaction = transaction,
        serviceName = serviceName,
        threadPoolSize = properties.commandScheduleThreadPoolSize,
        threadFactoryClassName = properties.commandScheduleThreadFactoryClassName,
        executionContextAccessor = executionContextAccessor,
        executionContextScopeManager = executionContextScopeManager,
        executionContextCodecRegistry = executionContextCodecRegistry,
        invocationScopeAccessor = invocationScopeAccessor,
    ).apply(DefaultReliableCommandSupervisor::init)

    @Bean
    @ConditionalOnMissingBean(ReliableCommandTransaction::class)
    fun reliableCommandTransaction(): JpaReliableCommandTransaction = JpaReliableCommandTransaction()

    @Bean
    fun jpaCommandScheduleService(
        commandManager: CommandManager,
        locker: Locker,
        @Value(RETRY_LOCKER_KEY) retryLockerKey: String,
        properties: CommandScheduleProperties,
        jdbcTemplate: JdbcTemplate,
    ): JpaCommandScheduleService = JpaCommandScheduleService(
        commandManager,
        locker,
        retryLockerKey,
        properties.addPartitionEnable,
        jdbcTemplate,
    ).apply { init() }

    @Bean
    fun commandScheduleTasks(
        scheduleService: JpaCommandScheduleService,
        properties: CommandScheduleProperties,
    ): CommandScheduleTasks = CommandScheduleTasks(scheduleService, properties)

    class CommandScheduleTasks(
        private val scheduleService: JpaCommandScheduleService,
        private val properties: CommandScheduleProperties,
    ) {
        @Scheduled(cron = "\${cap4k.ddd.application.command.schedule.retryCron:\${cap4k.ddd.application.command.schedule.retry-cron:0 * * * * ?}}")
        fun retry() = scheduleService.retry(
            properties.retryBatchSize,
            Duration.ofSeconds(properties.retryIntervalSeconds.toLong()),
            Duration.ofSeconds(properties.retryMaxLockSeconds.toLong()),
        )


        @Scheduled(cron = "\${cap4k.ddd.application.command.schedule.addPartitionCron:\${cap4k.ddd.application.command.schedule.add-partition-cron:0 0 0 * * ?}}")
        fun addPartition() = scheduleService.addPartition()
    }
}
