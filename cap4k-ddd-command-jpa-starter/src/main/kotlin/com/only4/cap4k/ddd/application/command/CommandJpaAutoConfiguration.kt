package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.application.command.configure.ReliableCommandWorkerProperties
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisor
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandTransaction
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
import org.springframework.context.SmartLifecycle
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.util.concurrent.atomic.AtomicBoolean

@AutoConfiguration
@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd.application.command.persistence"])
@EntityScan(basePackages = ["com.only4.cap4k.ddd.application.command.persistence"])
@EnableConfigurationProperties(ReliableCommandWorkerProperties::class)
class CommandJpaAutoConfiguration {
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

    @Bean(destroyMethod = "close")
    fun jpaReliableCommandWorker(
        substrate: JpaCommandExecutionSubstrate,
        commandSupervisor: CommandSupervisor,
        executionContextScopeManager: ExecutionContextScopeManager,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
        properties: ReliableCommandWorkerProperties,
    ): JpaReliableCommandWorker = JpaReliableCommandWorker(
        substrate = substrate,
        commandSupervisor = commandSupervisor,
        executionContextScopeManager = executionContextScopeManager,
        executionContextCodecRegistry = executionContextCodecRegistry,
        serviceName = serviceName,
        workerCount = properties.workerCount,
        batchSize = properties.batchSize,
        pollInterval = properties.pollInterval,
        leaseDuration = properties.leaseDuration,
        renewInterval = properties.renewInterval,
        threadFactoryClassName = properties.threadFactoryClassName,
    )

    @Bean
    fun jpaReliableCommandWorkerLifecycle(worker: JpaReliableCommandWorker): SmartLifecycle =
        ReliableCommandWorkerLifecycle(worker)

    @Bean
    @ConditionalOnMissingBean(ReliableCommandSupervisor::class)
    fun reliableCommandSupervisor(
        unitOfWork: CommandUnitOfWorkCoordinator,
        transaction: ReliableCommandTransaction,
        validatorProvider: ObjectProvider<Validator>,
        commandRecordRepository: CommandRecordRepository,
        executionContextAccessor: ExecutionContextAccessor,
        executionContextCodecRegistry: ExecutionContextCodecRegistry,
        invocationScopeAccessor: InvocationScopeAccessor,
        wakeUp: JpaReliableCommandWorker,
        @Value(CONFIG_KEY_4_SVC_NAME) serviceName: String,
    ): DefaultReliableCommandSupervisor = DefaultReliableCommandSupervisor(
        validator = validatorProvider.getIfAvailable(),
        commandRecordRepository = commandRecordRepository,
        unitOfWorkProvider = { unitOfWork },
        transaction = transaction,
        wakeUp = wakeUp,
        serviceName = serviceName,
        executionContextAccessor = executionContextAccessor,
        executionContextCodecRegistry = executionContextCodecRegistry,
        invocationScopeAccessor = invocationScopeAccessor,
    )

    @Bean
    @ConditionalOnMissingBean(ReliableCommandTransaction::class)
    fun reliableCommandTransaction(): JpaReliableCommandTransaction = JpaReliableCommandTransaction()
}

internal class ReliableCommandWorkerLifecycle(
    private val worker: JpaReliableCommandWorker,
) : SmartLifecycle {
    private val running = AtomicBoolean(false)

    override fun start() {
        if (running.compareAndSet(false, true)) worker.init()
    }

    override fun stop() {
        if (running.compareAndSet(true, false)) worker.close()
    }

    override fun isRunning(): Boolean = running.get()

    override fun getPhase(): Int = Int.MAX_VALUE
}
