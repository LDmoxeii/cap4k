package com.only4.cap4k.ddd.core.application.command.impl

import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandTransaction
import com.only4.cap4k.ddd.core.application.command.ReliableCommandWakeUp
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime

open class DefaultReliableCommandSupervisor(
    private val validator: Validator?,
    private val commandRecordRepository: CommandRecordRepository,
    private val unitOfWorkProvider: () -> CommandUnitOfWorkCoordinator,
    private val transaction: ReliableCommandTransaction,
    private val wakeUp: ReliableCommandWakeUp,
    private val serviceName: String,
    private val executionContextAccessor: ExecutionContextAccessor = ExecutionContextAccessor {
        ExecutionContextSnapshot.EMPTY
    },
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val invocationScopeAccessor: InvocationScopeAccessor,
) : ReliableCommandSupervisor {
    companion object {
        private const val DEFAULT_COMMAND_EXPIRE_MINUTES = 1440
        private const val DEFAULT_COMMAND_RETRY_TIMES = 200
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
        transaction.afterCommit { wakeUp.wakeUp(schedule) }
        return record.id
    }

    private fun requireRegistrationScope(kind: String) {
        val current = invocationScopeAccessor.current()
        check(current == InvocationKind.COMMAND || current == InvocationKind.DOMAIN_EVENT_HANDLER) {
            "$kind registration requires COMMAND or DOMAIN_EVENT_HANDLER invocation scope; " +
                "current=${current ?: "NONE"}"
        }
    }

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
        commandRecordRepository.save(record)
        return record
    }

    private fun validate(command: Any) {
        validator?.validate(command)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }
    }
}
