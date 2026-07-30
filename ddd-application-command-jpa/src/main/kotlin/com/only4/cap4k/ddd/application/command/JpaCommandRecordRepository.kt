package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.application.command.persistence.ArchivedCommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.ArchivedCommandRecordJpaRepository
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 基于Jpa的命令记录仓储实现
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
open class JpaCommandRecordRepository(
    private val commandJpaRepository: CommandRecordJpaRepository,
    private val archivedCommandRecordJpaRepository: ArchivedCommandRecordJpaRepository
) : CommandRecordRepository {

    override fun create(): CommandRecord = CommandRecordImpl()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(commandRecord: CommandRecord) {
        val record = commandRecord as CommandRecordImpl
        val command = commandJpaRepository.save(record.entity)
        record.resume(command)
    }

    override fun getById(id: String): CommandRecord {
        val command = commandJpaRepository.findOne { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<String>(CommandRecordEntity.F_COMMAND_UUID), id)
        }.orElseThrow { DomainException("CommandRecord not found") }

        return CommandRecordImpl().apply {
            resume(command)
        }
    }

    override fun getByNextTryTime(serviceName: String, maxNextTryTime: LocalDateTime, limit: Int): List<CommandRecord> {
        val commands = commandJpaRepository.findAll({ root, cq, cb ->
            cq.where(
                cb.or(
                    cb.and(
                        // 【初始状态】
                        cb.equal(root.get<CommandRecordEntity.CommandState>(CommandRecordEntity.F_COMMAND_STATE), CommandRecordEntity.CommandState.INIT),
                        cb.lessThan(root.get(CommandRecordEntity.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(CommandRecordEntity.F_SVC_NAME), serviceName)
                    ),
                    cb.and(
                        // 【执行中状态】
                        cb.equal(
                            root.get<CommandRecordEntity.CommandState>(CommandRecordEntity.F_COMMAND_STATE),
                            CommandRecordEntity.CommandState.EXECUTING
                        ),
                        cb.lessThan(root.get(CommandRecordEntity.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(CommandRecordEntity.F_SVC_NAME), serviceName)
                    ),
                    cb.and(
                        // 【异常状态】
                        cb.equal(
                            root.get<CommandRecordEntity.CommandState>(CommandRecordEntity.F_COMMAND_STATE),
                            CommandRecordEntity.CommandState.EXCEPTION
                        ),
                        cb.lessThan(root.get(CommandRecordEntity.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(CommandRecordEntity.F_SVC_NAME), serviceName)
                    )
                )
            )
            null
        }, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, CommandRecordEntity.F_NEXT_TRY_TIME)))

        return commands.map { command ->
            CommandRecordImpl().apply {
                resume(command)
            }
        }.toList()
    }

    override fun archiveByExpireAt(serviceName: String, maxExpireAt: LocalDateTime, limit: Int): Int {
        val commands = commandJpaRepository.findAll({ root, cq, cb ->
            cq.where(
                cb.and(
                    // 【状态】
                    cb.or(
                        cb.equal(root.get<CommandRecordEntity.CommandState>(CommandRecordEntity.F_COMMAND_STATE), CommandRecordEntity.CommandState.CANCEL),
                        cb.equal(root.get<CommandRecordEntity.CommandState>(CommandRecordEntity.F_COMMAND_STATE), CommandRecordEntity.CommandState.EXPIRED),
                        cb.equal(
                            root.get<CommandRecordEntity.CommandState>(CommandRecordEntity.F_COMMAND_STATE),
                            CommandRecordEntity.CommandState.EXHAUSTED
                        ),
                        cb.equal(root.get<CommandRecordEntity.CommandState>(CommandRecordEntity.F_COMMAND_STATE), CommandRecordEntity.CommandState.EXECUTED)
                    ),
                    cb.lessThan(root.get(CommandRecordEntity.F_EXPIRE_AT), maxExpireAt),
                    cb.equal(root.get<String>(CommandRecordEntity.F_SVC_NAME), serviceName)
                )
            )
            null
        }, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, CommandRecordEntity.F_NEXT_TRY_TIME)))

        if (!commands.hasContent()) {
            return 0
        }

        val archivedCommands = commands.map { command ->
            ArchivedCommandRecordEntity().apply {
                archiveFrom(command)
            }
        }.toList()

        migrate(commands.content, archivedCommands)
        return commands.numberOfElements
    }

    @Transactional
    open fun migrate(commands: List<CommandRecordEntity>, archivedCommands: List<ArchivedCommandRecordEntity>) {
        archivedCommandRecordJpaRepository.saveAll(archivedCommands)
        commandJpaRepository.deleteAllInBatch(commands)
    }
}
