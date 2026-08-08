package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 基于Jpa的命令记录仓储实现
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
open class JpaCommandRecordRepository(
    private val commandJpaRepository: CommandRecordJpaRepository,
) : CommandRecordRepository {

    override fun create(): CommandRecord = CommandRecordImpl()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(commandRecord: CommandRecord) {
        val record = commandRecord as CommandRecordImpl
        val command = commandJpaRepository.save(record.entity)
        record.resume(command)
    }

}
