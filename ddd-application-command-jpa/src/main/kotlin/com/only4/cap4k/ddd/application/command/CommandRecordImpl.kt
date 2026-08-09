package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandRecord
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

}
