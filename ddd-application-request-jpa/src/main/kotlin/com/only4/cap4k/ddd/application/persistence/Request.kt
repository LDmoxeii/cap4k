package com.only4.cap4k.ddd.application.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.annotation.JSONField
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
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

@Aggregate(
    aggregate = "request",
    name = "Request",
    root = true,
    type = Aggregate.TYPE_ENTITY,
    description = "请求记录"
)
@Entity
@Table(name = "`__request`")
@DynamicInsert
@DynamicUpdate
class Request(
    /**
     * bigint
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null,

    /**
     * REQUEST uuid
     * varchar(64)  NOT NULL DEFAULT ''
     */
    @Column(name = "`request_uuid`")
    var requestUuid: String = "",

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`")
    var svcName: String = "",

    /**
     * REQUEST类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`request_type`")
    var requestType: String = "",

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
     * 执行状态@E=0:INIT:init|-1:EXECUTING:executing|-2:CANCEL:cancel|-3:EXPIRED:expired|-4:EXHAUSTED:exhausted|-9:EXCEPTION:exception|1:EXECUTED:executed;@T=RequestState;
     * int          NOT NULL DEFAULT '0'
     */
    @Column(name = "`request_state`")
    @Convert(converter = RequestState.Converter::class)
    var requestState: RequestState = RequestState.INIT,

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
        private val log = LoggerFactory.getLogger(Request::class.java)

        const val F_REQUEST_UUID = "requestUuid"
        const val F_SVC_NAME = "svcName"
        const val F_REQUEST_TYPE = "requestType"
        const val F_PARAM = "param"
        const val F_PARAM_TYPE = "paramType"
        const val F_RESULT = "result"
        const val F_RESULT_TYPE = "resultType"
        const val F_EXCEPTION = "exception"
        const val F_CREATE_AT = "createAt"
        const val F_EXPIRE_AT = "expireAt"
        const val F_REQUEST_STATE = "requestState"
        const val F_TRY_TIMES = "tryTimes"
        const val F_TRIED_TIMES = "triedTimes"
        const val F_LAST_TRY_TIME = "lastTryTime"
        const val F_NEXT_TRY_TIME = "nextTryTime"
    }

    fun init(
        requestParam: RequestParam<*>,
        svcName: String,
        requestType: String,
        scheduleAt: LocalDateTime = LocalDateTime.now(),
        expireAfter: Duration,
        retryTimes: Int,
    ): Request = apply {
        this.requestUuid = UUID.randomUUID().toString()
        this.svcName = svcName
        this.requestType = requestType
        this.createAt = scheduleAt
        this.expireAt = scheduleAt.plusSeconds(expireAfter.seconds)
        this.requestState = RequestState.INIT
        this.tryTimes = retryTimes
        this.triedTimes = 0
        this.lastTryTime = scheduleAt

        loadRequestParam(requestParam)

        this.nextTryTime = calculateNextTryTime(scheduleAt)
        this.result = ""
        this.resultType = ""
    }

    @Transient
    @JSONField(serialize = false)
    var requestParam: RequestParam<*>? = null
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
                field = JSON.parseObject(param, dataClass, Feature.SupportNonPublicField) as RequestParam<*>
            }
            return field
        }
        private set

    private fun loadRequestParam(requestParam: RequestParam<*>) {
        this.requestParam = requestParam
        this.param = JSON.toJSONString(
            requestParam,
            SerializerFeature.IgnoreNonFieldGetter,
            SerializerFeature.SkipTransientField
        )
        this.paramType = requestParam.javaClass.name
        val retry = requestParam.javaClass.getAnnotation(Retry::class.java)
        if (retry != null) {
            this.tryTimes = retry.retryTimes
            this.expireAt = this.createAt.plusMinutes(retry.expireAfter.toLong())
        }
    }

    @Transient
    @JSONField(serialize = false)
    var requestResult: Any? = null
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

    private fun loadRequestResult(result: Any) {
        this.requestResult = result
        this.result = JSON.toJSONString(
            result,
            SerializerFeature.IgnoreNonFieldGetter,
            SerializerFeature.SkipTransientField
        )
        this.resultType = result.javaClass.name
    }

    val isValid: Boolean
        get() = this.requestState in setOf(RequestState.INIT, RequestState.EXECUTING, RequestState.EXCEPTION)

    val isInvalid: Boolean
        get() = this.requestState in setOf(RequestState.CANCEL, RequestState.EXPIRED, RequestState.EXHAUSTED)

    val isExecuting: Boolean
        get() = RequestState.EXECUTING == this.requestState

    val isExecuted: Boolean
        get() = RequestState.EXECUTED == this.requestState

    fun beginRequest(now: LocalDateTime): Boolean {
        when {
            // 初始状态或者确认中或者异常
            !isValid -> return false
            // 超过重试次数
            this.triedTimes >= this.tryTimes -> {
                this.requestState = RequestState.EXHAUSTED
                return false
            }
            // 事件过期
            now.isAfter(this.expireAt) -> {
                this.requestState = RequestState.EXPIRED
                return false
            }
            // 未到下次重试时间
            this.lastTryTime != now && this.nextTryTime.isAfter(now) -> return false
        }

        this.requestState = RequestState.EXECUTING
        this.lastTryTime = now
        this.triedTimes += 1
        this.nextTryTime = calculateNextTryTime(now)
        return true
    }

    fun endRequest(now: LocalDateTime, result: Any) {
        this.requestState = RequestState.EXECUTED
        loadRequestResult(result)
    }

    fun cancelRequest(now: LocalDateTime): Boolean {
        if (isExecuted || isInvalid) {
            return false
        }
        this.requestState = RequestState.CANCEL
        return true
    }

    fun occurredException(now: LocalDateTime, ex: Throwable) {
        if (isExecuted) {
            return
        }
        this.requestState = RequestState.EXCEPTION
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.exception = sw.toString()
    }

    private fun calculateNextTryTime(now: LocalDateTime): LocalDateTime {
        val retry = requestParam!!.javaClass.getAnnotation(Retry::class.java)
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

    enum class RequestState(val value: Int, val stateName: String) {
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
            fun valueOf(value: Int): RequestState? {
                return entries.find { it.value == value }
            }
        }

        class Converter : AttributeConverter<RequestState, Int> {
            override fun convertToDatabaseColumn(attribute: RequestState): Int {
                return attribute.value
            }

            override fun convertToEntityAttribute(dbData: Int): RequestState? {
                return valueOf(dbData)
            }
        }
    }
}
