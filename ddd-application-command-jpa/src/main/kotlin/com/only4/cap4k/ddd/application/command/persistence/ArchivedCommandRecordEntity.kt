package com.only4.cap4k.ddd.application.command.persistence

import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity.CommandState
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

@Entity
@Table(name = "`__archived_command`")
@DynamicInsert
@DynamicUpdate
class ArchivedCommandRecordEntity(
    /**
     * bigint
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null,

    /**
     * COMMAND uuid
     * varchar(64)  NOT NULL DEFAULT ''
     */
    @Column(name = "`command_uuid`")
    var commandUuid: String = "",

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`")
    var svcName: String = "",

    /**
     * COMMAND类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`command_type`")
    var commandType: String = "",

    /**
     * 参数
     * text
     */
    @Column(name = "`param`")
    var param: String = "",

    /**
     * 参数类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`param_type`")
    var paramType: String = "",

    /** Original versioned ExecutionContext envelope. Null represents legacy EMPTY context. */
    @Column(name = "`execution_context`")
    var executionContext: String? = null,

    /**
     * 结果
     * text
     */
    @Column(name = "`result`")
    var result: String = "",

    /**
     * 结果类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`result_type`")
    var resultType: String = "",

    /**
     * 执行异常
     * text
     */
    @Column(name = "`exception`")
    var exception: String? = null,

    /**
     * 过期时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`")
    var expireAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 创建时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`")
    var createAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 执行状态@E=0:INIT:init|-1:EXECUTING:executing|-2:CANCEL:cancel|-3:EXPIRED:expired|-4:EXHAUSTED:exhausted|-9:EXCEPTION:exception|1:EXECUTED:executed;@T=CommandState;
     * int          NOT NULL DEFAULT '0'
     */
    @Column(name = "`command_state`")
    @Convert(converter = CommandState.Converter::class)
    var commandState: CommandState = CommandState.INIT,

    /**
     * 上次尝试时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`")
    var lastTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 下次尝试时间
     * datetime     NOT NULL DEFAULT '0001-01-01 00:00:00'
     */
    @Column(name = "`next_try_time`")
    var nextTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 已尝试次数
     * int(11)      NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`")
    var triedTimes: Int = 0,

    /**
     * 尝试次数
     * int(11)      NOT NULL DEFAULT '0'
     */
    @Column(name = "`try_times`")
    var tryTimes: Int = 0,

    /** Immutable retry-policy snapshot captured by the active command record. */
    @Column(name = "`retry_policy`", nullable = false)
    var retryPolicy: String = "",

    /**
     * 数据版本（支持乐观锁）
     * int          NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`")
    var version: Int = 0,
) {
    /**
     * 从命令复制数据到归档记录
     */
    fun archiveFrom(command: CommandRecordEntity): ArchivedCommandRecordEntity = apply {
        this.id = command.id
        this.commandUuid = command.commandUuid
        this.svcName = command.svcName
        this.commandType = command.commandType
        this.param = command.param
        this.paramType = command.paramType
        this.executionContext = command.executionContext
        this.result = command.result
        this.resultType = command.resultType
        this.exception = command.exception
        this.expireAt = command.expireAt
        this.createAt = command.createAt
        this.commandState = command.commandState
        this.lastTryTime = command.lastTryTime
        this.nextTryTime = command.nextTryTime
        this.triedTimes = command.triedTimes
        this.tryTimes = command.tryTimes
        this.retryPolicy = command.retryPolicy
        this.version = command.version
    }
}
