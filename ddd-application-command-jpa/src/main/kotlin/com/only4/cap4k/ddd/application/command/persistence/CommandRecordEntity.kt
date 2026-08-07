package com.only4.cap4k.ddd.application.command.persistence

import com.fasterxml.jackson.annotation.JsonIgnore
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.ReliableFailureFacts
import com.only4.cap4k.ddd.core.share.ReliableFailureOperation
import com.only4.cap4k.ddd.core.share.annotation.Retry
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "`__command`")
@DynamicInsert
@DynamicUpdate
class CommandRecordEntity(
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

    /** Versioned ExecutionContext envelope captured when the reliable command is registered. */
    @Column(name = "`execution_context`")
    var executionContext: String? = null,

    /** Safe structured facts for the latest failed execution attempt. */
    @Column(name = "`failure_facts`", columnDefinition = "text")
    var failureFactsJson: String? = null,

    /**
     * 过期时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`", columnDefinition = "datetime(3)")
    var expireAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 创建时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`", columnDefinition = "datetime(3)")
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
    @Column(name = "`last_try_time`", columnDefinition = "datetime(3)")
    var lastTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 下次尝试时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`next_try_time`", columnDefinition = "datetime(3)")
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

    /** Immutable retry-policy snapshot captured when the reliable command is registered. */
    @Column(name = "`retry_policy`", nullable = false)
    var retryPolicy: String = "",

    /**
     * 数据版本（支持乐观锁）
     * int          NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`")
    var version: Int = 0,

    /** Private runtime ownership token assigned by the atomic JPA substrate. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "`delivery_token`", length = 32, columnDefinition = "varbinary(32)")
    var deliveryToken: ByteArray? = null,

    /** Private runtime lease boundary for the current delivery token. */
    @Column(name = "`lease_until`", columnDefinition = "datetime(3)")
    var leaseUntil: LocalDateTime? = null,

    /** Database audit columns retained for schema/projection parity. */
    @Column(name = "`db_created_at`", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    var dbCreatedAt: LocalDateTime? = null,

    @Column(name = "`db_updated_at`", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    var dbUpdatedAt: LocalDateTime? = null,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandRecordEntity::class.java)

        const val F_COMMAND_UUID = "commandUuid"
        const val F_SVC_NAME = "svcName"
        const val F_COMMAND_TYPE = "commandType"
        const val F_PARAM = "param"
        const val F_PARAM_TYPE = "paramType"
        const val F_EXECUTION_CONTEXT = "executionContext"
        const val F_FAILURE_FACTS_JSON = "failureFactsJson"
        const val F_CREATE_AT = "createAt"
        const val F_EXPIRE_AT = "expireAt"
        const val F_COMMAND_STATE = "commandState"
        const val F_TRY_TIMES = "tryTimes"
        const val F_RETRY_POLICY = "retryPolicy"
        const val F_TRIED_TIMES = "triedTimes"
        const val F_LAST_TRY_TIME = "lastTryTime"
        const val F_NEXT_TRY_TIME = "nextTryTime"
        const val F_DELIVERY_TOKEN = "deliveryToken"
        const val F_LEASE_UNTIL = "leaseUntil"
    }

    fun init(
        commandParam: Command<*>,
        svcName: String,
        commandType: String,
        scheduleAt: LocalDateTime = LocalDateTime.now(),
        expireAfter: Duration,
        retryTimes: Int,
    ): CommandRecordEntity = apply {
        this.commandUuid = UUID.randomUUID().toString()
        this.svcName = svcName
        this.commandType = commandType
        this.createAt = scheduleAt
        this.expireAt = scheduleAt.plusSeconds(expireAfter.seconds)
        this.commandState = CommandState.INIT
        this.tryTimes = retryTimes
        this.triedTimes = 0
        this.lastTryTime = scheduleAt

        loadCommand(commandParam)

        this.nextTryTime = calculateNextTryTime(scheduleAt)
    }

    @Transient
    @get:JsonIgnore
    @field:JsonIgnore
    var commandParam: Command<*>? = null
        get() {
            if (field != null) {
                return field
            }
            if (paramType.isNotBlank()) {
                val dataClass = try {
                    Class.forName(paramType)
                } catch (e: ClassNotFoundException) {
                    log.error("参数类型解析错误 failureType={}", e.javaClass.name)
                    throw DomainException("参数类型解析错误")
                }
                field = RuntimeJson.read(param, dataClass) as Command<*>
            }
            return field
        }
        private set

    private fun loadCommand(commandParam: Command<*>) {
        this.commandParam = commandParam
        this.param = RuntimeJson.write(commandParam)
        this.paramType = commandParam.javaClass.name
        val retry = commandParam.javaClass.getAnnotation(Retry::class.java)
        val policySnapshot = ReliableRetryPolicySnapshot.capture(retry, this.tryTimes)
        this.retryPolicy = RuntimeJson.write(policySnapshot)
        this.tryTimes = policySnapshot.retryLimit
        if (retry != null) {
            this.expireAt = this.createAt.plusMinutes(retry.expireAfter.toLong())
        }
    }

    @Transient
    @get:JsonIgnore
    @field:JsonIgnore
    var failureFacts: ReliableFailureFacts? = null
        get() {
            if (field == null && !failureFactsJson.isNullOrBlank()) {
                field = RuntimeJson.read(failureFactsJson!!, ReliableFailureFacts::class.java)
            }
            return field
        }
        private set

    private fun recordFailure(facts: ReliableFailureFacts) {
        failureFacts = facts
        failureFactsJson = RuntimeJson.write(facts)
    }

    private fun markFailureTerminal() {
        val current = failureFacts ?: return
        if (current.terminal) return
        recordFailure(current.copy(retryable = false, terminal = true))
    }

    val isValid: Boolean
        get() = this.commandState in setOf(CommandState.INIT, CommandState.EXECUTING, CommandState.EXCEPTION)

    val isInvalid: Boolean
        get() = this.commandState in setOf(CommandState.CANCEL, CommandState.EXPIRED, CommandState.EXHAUSTED)

    val isExecuting: Boolean
        get() = CommandState.EXECUTING == this.commandState

    val isExecuted: Boolean
        get() = CommandState.EXECUTED == this.commandState

    fun beginCommand(now: LocalDateTime): Boolean {
        when {
            // 初始状态或者确认中或者异常
            !isValid -> return false
            // 超过重试次数
            this.triedTimes >= this.tryTimes -> {
                this.commandState = CommandState.EXHAUSTED
                markFailureTerminal()
                return false
            }
            // 事件过期
            now.isAfter(this.expireAt) -> {
                this.commandState = CommandState.EXPIRED
                markFailureTerminal()
                return false
            }
            // 未到下次重试时间
            this.lastTryTime != now && this.nextTryTime.isAfter(now) -> return false
        }

        this.commandState = CommandState.EXECUTING
        this.lastTryTime = now
        this.triedTimes += 1
        this.nextTryTime = calculateNextTryTime(now)
        return true
    }

    fun endCommand(now: LocalDateTime) {
        this.commandState = CommandState.EXECUTED
    }

    fun cancelCommand(now: LocalDateTime): Boolean {
        if (isExecuted || isInvalid) {
            return false
        }
        this.commandState = CommandState.CANCEL
        return true
    }

    fun occurredException(now: LocalDateTime, ex: Throwable) {
        if (isExecuted) {
            return
        }
        this.commandState = CommandState.EXCEPTION
        val retryable = this.triedTimes < this.tryTimes && !now.isAfter(this.expireAt)
        recordFailure(
            ReliableFailureFacts.capture(
                operation = ReliableFailureOperation.COMMAND_EXECUTION,
                throwable = ex,
                occurredAt = now,
                attempt = this.triedTimes.coerceAtLeast(1),
                correlationId = this.commandUuid,
                retryable = retryable,
            )
        )
    }

    private fun calculateNextTryTime(now: LocalDateTime): LocalDateTime {
        val policySnapshot = RuntimeJson.read(retryPolicy, ReliableRetryPolicySnapshot::class.java)
        return now.plusMinutes(policySnapshot.delayMinutesFor(this.triedTimes))
    }

    override fun toString(): String =
        "CommandRecord(commandUuid=$commandUuid, service=$svcName, type=$commandType, " +
            "state=${commandState.stateName}, attempt=$triedTimes/$tryTimes, " +
            "lastTryTime=$lastTryTime, nextTryTime=$nextTryTime, failure=$failureFacts)"

    enum class CommandState(val value: Int, val stateName: String) {
        /**
         * 初始状态
         */
        INIT(0, "init"),

        /**
         * 待确认结果
         */
        EXECUTING(-1, "executing"),

        /**
         * 业务主动取消
         */
        CANCEL(-2, "cancel"),

        /**
         * 过期
         */
        EXPIRED(-3, "expired"),

        /**
         * 用完重试次数
         */
        EXHAUSTED(-4, "exhausted"),

        /**
         * 发生异常
         */
        EXCEPTION(-9, "exception"),

        /**
         * 已发送
         */
        EXECUTED(1, "executed");

        companion object {
            @JvmStatic
            fun valueOf(value: Int): CommandState? {
                return entries.find { it.value == value }
            }
        }

        class Converter : AttributeConverter<CommandState, Int> {
            override fun convertToDatabaseColumn(attribute: CommandState): Int {
                return attribute.value
            }

            override fun convertToEntityAttribute(dbData: Int): CommandState? {
                return valueOf(dbData)
            }
        }
    }
}
