package com.only4.cap4k.ddd.application.command.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.annotation.JSONField
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.annotation.Retry
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
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

    /**
     * 数据版本（支持乐观锁）
     * int          NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`")
    var version: Int = 0,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandRecordEntity::class.java)

        const val F_COMMAND_UUID = "commandUuid"
        const val F_SVC_NAME = "svcName"
        const val F_COMMAND_TYPE = "commandType"
        const val F_PARAM = "param"
        const val F_PARAM_TYPE = "paramType"
        const val F_RESULT = "result"
        const val F_RESULT_TYPE = "resultType"
        const val F_EXCEPTION = "exception"
        const val F_CREATE_AT = "createAt"
        const val F_EXPIRE_AT = "expireAt"
        const val F_COMMAND_STATE = "commandState"
        const val F_TRY_TIMES = "tryTimes"
        const val F_TRIED_TIMES = "triedTimes"
        const val F_LAST_TRY_TIME = "lastTryTime"
        const val F_NEXT_TRY_TIME = "nextTryTime"
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
        this.result = ""
        this.resultType = ""
    }

    @Transient
    @JSONField(serialize = false)
    var commandParam: Command<*>? = null
        get() {
            if (field != null) {
                return field
            }
            if (paramType.isNotBlank()) {
                val dataClass = try {
                    Class.forName(paramType)
                } catch (e: ClassNotFoundException) {
                    log.error("参数类型解析错误", e)
                    throw DomainException("参数类型解析错误: $paramType", e)
                }
                field = JSON.parseObject(param, dataClass, Feature.SupportNonPublicField) as Command<*>
            }
            return field
        }
        private set

    private fun loadCommand(commandParam: Command<*>) {
        this.commandParam = commandParam
        this.param = JSON.toJSONString(
            commandParam,
            SerializerFeature.IgnoreNonFieldGetter,
            SerializerFeature.SkipTransientField
        )
        this.paramType = commandParam.javaClass.name
        val retry = commandParam.javaClass.getAnnotation(Retry::class.java)
        if (retry != null) {
            this.tryTimes = retry.retryTimes
            this.expireAt = this.createAt.plusMinutes(retry.expireAfter.toLong())
        }
    }

    @Transient
    @JSONField(serialize = false)
    var commandResult: Any? = null
        get() {
            if (field != null) {
                return field
            }
            if (resultType.isNotBlank()) {
                val dataClass = try {
                    Class.forName(resultType)
                } catch (e: ClassNotFoundException) {
                    log.error("返回类型解析错误", e)
                    throw DomainException("返回类型解析错误: $resultType", e)
                }
                field = JSON.parseObject(result, dataClass, Feature.SupportNonPublicField)
            }
            return field
        }
        private set

    private fun loadCommandResult(result: Any) {
        this.commandResult = result
        this.result = JSON.toJSONString(
            result,
            SerializerFeature.IgnoreNonFieldGetter,
            SerializerFeature.SkipTransientField
        )
        this.resultType = result.javaClass.name
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
                return false
            }
            // 事件过期
            now.isAfter(this.expireAt) -> {
                this.commandState = CommandState.EXPIRED
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

    fun endCommand(now: LocalDateTime, result: Any) {
        this.commandState = CommandState.EXECUTED
        loadCommandResult(result)
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
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.exception = sw.toString()
    }

    private fun calculateNextTryTime(now: LocalDateTime): LocalDateTime {
        val retry = commandParam!!.javaClass.getAnnotation(Retry::class.java)
        if (retry == null || retry.retryIntervals.isEmpty()) {
            return when {
                this.triedTimes <= 10 -> now.plusMinutes(1)
                this.triedTimes <= 20 -> now.plusMinutes(5)
                else -> now.plusMinutes(10)
            }
        }
        val index = (this.triedTimes - 1).coerceIn(0, retry.retryIntervals.lastIndex)
        return now.plusMinutes(retry.retryIntervals[index].toLong())
    }

    override fun toString(): String {
        return JSON.toJSONString(this, SerializerFeature.IgnoreNonFieldGetter, SerializerFeature.SkipTransientField)
    }

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
