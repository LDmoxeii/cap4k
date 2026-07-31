package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.share.DomainException
import java.time.Duration
import java.time.LocalDateTime

/**
 * 命令记录实现
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
class CommandRecordImpl : CommandRecord {
    lateinit var entity: CommandRecordEntity

    /**
     * 恢复命令
     */
    fun resume(command: CommandRecordEntity) {
        this.entity = command
    }

    override fun toString(): String = entity.toString()

    override fun init(
        command: Command<*>,
        serviceName: String,
        commandType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int,
        executionContext: Collection<EncodedExecutionContextElement>,
    ) {
        entity = CommandRecordEntity()
        entity.init(command, serviceName, commandType, scheduleAt, expireAfter, retryTimes)
        entity.executionContext = JpaExecutionContextEnvelope.encode(executionContext)
    }

    override val id: String
        get() = entity.commandUuid

    override val type: String
        get() = entity.commandType

    override val command: Command<*>
        get() = entity.commandParam!!

    override val executionContext: List<EncodedExecutionContextElement>
        get() = JpaExecutionContextEnvelope.decode(entity.executionContext)

    override fun <R : Any> getResult(): R? {
        @Suppress("UNCHECKED_CAST")
        val result = entity.commandResult as? R
        if (result == null && !entity.exception.isNullOrEmpty()) {
            throw DomainException(entity.exception!!)
        }
        return result
    }

    override val scheduleTime: LocalDateTime
        get() = entity.lastTryTime

    override val nextTryTime: LocalDateTime
        get() = entity.nextTryTime

    override val isValid: Boolean
        get() = entity.isValid

    override val isInvalid: Boolean
        get() = entity.isInvalid

    override val isExecuting: Boolean
        get() = entity.isExecuting

    override val isExecuted: Boolean
        get() = entity.isExecuted

    override fun beginCommand(now: LocalDateTime): Boolean = entity.beginCommand(now)

    override fun cancelCommand(now: LocalDateTime): Boolean = entity.cancelCommand(now)

    override fun endCommand(now: LocalDateTime, result: Any) {
        entity.endCommand(now, result)
    }

    override fun occurredException(now: LocalDateTime, throwable: Throwable) {
        entity.occurredException(now, throwable)
    }
}
