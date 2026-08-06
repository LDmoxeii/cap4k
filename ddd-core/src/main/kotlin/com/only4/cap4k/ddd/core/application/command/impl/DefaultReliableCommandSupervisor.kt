package com.only4.cap4k.ddd.core.application.command.impl

import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandManager
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandTransaction
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.createScheduledThreadPool
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

open class DefaultReliableCommandSupervisor(
    private val commandSupervisor: CommandSupervisor,
    private val validator: Validator?,
    private val commandRecordRepository: CommandRecordRepository,
    private val unitOfWorkProvider: () -> CommandUnitOfWorkCoordinator,
    private val transaction: ReliableCommandTransaction,
    private val serviceName: String,
    private val threadPoolSize: Int,
    private val threadFactoryClassName: String,
    private val executionContextAccessor: ExecutionContextAccessor = ExecutionContextAccessor {
        ExecutionContextSnapshot.EMPTY
    },
    private val executionContextScopeManager: ExecutionContextScopeManager = ExecutionContextScopeManager {
        AutoCloseable { }
    },
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val invocationScopeAccessor: InvocationScopeAccessor,
) : ReliableCommandSupervisor, CommandManager {
    companion object {
        private const val DEFAULT_COMMAND_EXPIRE_MINUTES = 1440
        private const val DEFAULT_COMMAND_RETRY_TIMES = 200
        private const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES = 2
    }

    private val executorService by lazy {
        createScheduledThreadPool(threadPoolSize, threadFactoryClassName, javaClass.classLoader)
    }

    fun init() {
        executorService
    }

    override fun <COMMAND : Command<RESULT>, RESULT : Any> schedule(
        command: COMMAND,
        schedule: LocalDateTime,
    ): String {
        requireRegistrationScope("Reliable Command")
        check(unitOfWorkProvider().active) {
            "Reliable Command registration requires an active Command Unit of Work"
        }
        transaction.requireActive()
        validate(command)
        val record = createCommandRecord(command::class.java.name, command, schedule)
        if (record.isExecuting) {
            transaction.afterCommit { scheduleExecution(record.command, record) }
        }
        return record.id
    }

    private fun requireRegistrationScope(kind: String) {
        val current = invocationScopeAccessor.current()
        check(current == InvocationKind.COMMAND || current == InvocationKind.DOMAIN_EVENT_HANDLER) {
            "$kind registration requires COMMAND or DOMAIN_EVENT_HANDLER invocation scope; " +
                "current=${current ?: "NONE"}"
        }
    }

    override fun resume(command: CommandRecord, minNextTryTime: LocalDateTime) {
        val now = LocalDateTime.now()
        val commandTime = if (Duration.between(command.nextTryTime, now).isNegative) now else command.nextTryTime
        command.beginCommand(commandTime)

        var maxTry = 65535
        while (command.nextTryTime.isBefore(minNextTryTime) && command.isValid) {
            command.beginCommand(command.nextTryTime)
            if (maxTry-- <= 0) throw DomainException("疑似死循环")
        }

        commandRecordRepository.save(command)
        validate(command.command)
        if (command.isExecuting) scheduleExecution(command.command, command)
    }

    override fun retry(id: String) {
        val record = commandRecordRepository.getById(id)
        validate(record.command)
        internalSend(record.command, record)
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<CommandRecord> =
        commandRecordRepository.getByNextTryTime(serviceName, maxNextTryTime, limit)

    protected open fun createCommandRecord(
        commandType: String,
        command: Command<*>,
        scheduleAt: LocalDateTime,
    ): CommandRecord {
        val record = commandRecordRepository.create()
        record.init(
            command = command,
            serviceName = serviceName,
            commandType = commandType,
            scheduleAt = scheduleAt,
            expireAfter = Duration.ofMinutes(DEFAULT_COMMAND_EXPIRE_MINUTES.toLong()),
            retryTimes = DEFAULT_COMMAND_RETRY_TIMES,
            executionContext = executionContextCodecRegistry.encode(
                executionContextAccessor.current(),
                ExecutionContextBoundary.RELIABLE_COMMAND,
            ),
        )

        val duration = Duration.between(LocalDateTime.now(), scheduleAt)
        if (duration.isNegative || duration.toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES) {
            record.beginCommand(scheduleAt)
        }
        commandRecordRepository.save(record)
        return record
    }

    protected open fun scheduleExecution(command: Command<*>, record: CommandRecord) {
        val duration = Duration.between(LocalDateTime.now(), record.scheduleTime)
            .let { if (it.isNegative) Duration.ZERO else it }
        executorService.schedule({ internalSend(command, record) }, duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun internalSend(command: Command<*>, record: CommandRecord): Any = try {
        val snapshot = executionContextCodecRegistry.decodeReliable(
            record.executionContext,
            ExecutionContextBoundary.RELIABLE_COMMAND,
        )
        val result = executionContextScopeManager.install(snapshot).use {
            commandSupervisor.send(command as Command<Any>)
        }
        record.endCommand(LocalDateTime.now())
        commandRecordRepository.save(record)
        result
    } catch (throwable: Throwable) {
        record.occurredException(LocalDateTime.now(), throwable)
        commandRecordRepository.save(record)
        throw throwable
    }

    private fun validate(command: Any) {
        validator?.validate(command)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }
    }
}
