package com.only4.cap4k.ddd.application.saga.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.annotation.JSONField
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.saga.SagaParam
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

/**
 * SAGA事务
 *
 * @author LD_moxeii
 * @date 2025/08/01
 */
@Aggregate(aggregate = "saga", name = "Saga", root = true, type = Aggregate.TYPE_ENTITY, description = "SAGA事务")
@Entity
@Table(name = "`__saga`")
@DynamicInsert
@DynamicUpdate
class Saga(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null,

    /**
     * SAGA uuid
     * varchar(64) NOT NULL DEFAULT ''
     */
    @Column(name = "`saga_uuid`", nullable = false)
    var sagaUuid: String = "",

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`", nullable = false)
    var svcName: String = "",

    /**
     * SAGA类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`saga_type`", nullable = false)
    var sagaType: String = "",

    /**
     * 参数
     * text (nullable)
     */
    @Column(name = "`param`")
    var param: String = "",

    /**
     * 参数类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`param_type`", nullable = false)
    var paramType: String = "",

    /**
     * 结果
     * text (nullable)
     */
    @Column(name = "`result`")
    var result: String = "",

    /**
     * 结果类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`result_type`", nullable = false)
    var resultType: String = "",

    /**
     * 异常信息
     * text (nullable)
     */
    @Column(name = "`exception`")
    var exception: String? = null,

    /**
     * 过期时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`")
    var expireAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 创建时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`")
    var createAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 执行状态
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`saga_state`", nullable = false)
    @Convert(converter = SagaState.Converter::class)
    var sagaState: SagaState = SagaState.INIT,

    /**
     * 上次尝试时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`")
    var lastTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 下次尝试时间
     * datetime NOT NULL DEFAULT '0001-01-01 00:00:00'
     */
    @Column(name = "`next_try_time`")
    var nextTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 已尝试次数
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`", nullable = false)
    var triedTimes: Int = 0,

    /**
     * 尝试次数
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`try_times`", nullable = false)
    var tryTimes: Int = 0,

    /**
     * 乐观锁
     * int NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`", nullable = false)
    var version: Int = 0,

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "`saga_id`", nullable = false)
    var sagaProcesses: MutableList<SagaProcess> = mutableListOf(),
) {
    companion object {
        private val log = LoggerFactory.getLogger(Saga::class.java)

        const val F_SAGA_UUID = "sagaUuid"
        const val F_SVC_NAME = "svcName"
        const val F_SAGA_TYPE = "sagaType"
        const val F_PARAM = "param"
        const val F_PARAM_TYPE = "paramType"
        const val F_RESULT = "result"
        const val F_RESULT_TYPE = "resultType"
        const val F_EXCEPTION = "exception"
        const val F_CREATE_AT = "createAt"
        const val F_EXPIRE_AT = "expireAt"
        const val F_SAGA_STATE = "sagaState"
        const val F_TRY_TIMES = "tryTimes"
        const val F_TRIED_TIMES = "triedTimes"
        const val F_LAST_TRY_TIME = "lastTryTime"
        const val F_NEXT_TRY_TIME = "nextTryTime"
    }

    fun init(
        sagaParam: SagaParam<*>,
        svcName: String,
        sagaType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int,
    ): Saga = apply {
        this.sagaUuid = UUID.randomUUID().toString()
        this.svcName = svcName
        this.sagaType = sagaType
        this.createAt = scheduleAt
        this.expireAt = scheduleAt.plusSeconds(expireAfter.seconds)
        this.sagaState = SagaState.INIT
        this.tryTimes = retryTimes
        this.triedTimes = 0
        this.lastTryTime = scheduleAt

        this.loadSagaParam(sagaParam)

        this.nextTryTime = calculateNextTryTime(scheduleAt)
        this.result = ""
        this.resultType = ""
        this.sagaProcesses = ArrayList()
    }

    @Transient
    @JSONField(serialize = false)
    var sagaParam: SagaParam<*>? = null
        get() {
            if (field != null) {
                return field
            }
            if (paramType.isNotBlank()) {
                var dataClass: Class<*>? = null
                try {
                    dataClass = Class.forName(paramType)
                } catch (e: ClassNotFoundException) {
                    log.error("参数类型解析错误", e)
                }
                field = JSON.parseObject(param, dataClass, Feature.SupportNonPublicField) as SagaParam<*>?
            }
            return field
        }
        private set

    private fun loadSagaParam(sagaParam: SagaParam<*>) {
        this.sagaParam = sagaParam
        this.param = JSON.toJSONString(sagaParam, IgnoreNonFieldGetter, SkipTransientField)
        this.paramType = sagaParam.javaClass.name

        val retry = sagaParam.javaClass.getAnnotation(Retry::class.java)
        if (retry != null) {
            this.tryTimes = retry.retryTimes
            this.expireAt = this.createAt.plusMinutes(retry.expireAfter.toLong())
        }
    }

    @Transient
    @JSONField(serialize = false)
    var sagaResult: Any? = null
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

    private fun loadSagaResult(result: Any) {
        this.sagaResult = result
        this.result = JSON.toJSONString(result, IgnoreNonFieldGetter, SkipTransientField)
        this.resultType = result.javaClass.name
    }

    fun getSagaProcess(processCode: String): SagaProcess? = sagaProcesses.find { it.processCode == processCode }

    fun beginSagaProcess(now: LocalDateTime, processCode: String, param: RequestParam<*>) {
        var sagaProcess = getSagaProcess(processCode)
        if (sagaProcess == null) {
            sagaProcess = SagaProcess().apply {
                this.processCode = processCode
                this.processState = SagaProcess.SagaProcessState.INIT
                this.createAt = now
                this.triedTimes = 0
            }
            this.sagaProcesses.add(sagaProcess)
        }
        sagaProcess.beginProcess(now, param)
    }

    fun endSagaProcess(now: LocalDateTime, processCode: String, result: Any) {
        val sagaProcess = getSagaProcess(processCode) ?: return
        sagaProcess.endProcess(now, result)
    }

    val isValid: Boolean
        get() = this.sagaState in setOf(SagaState.INIT, SagaState.EXECUTING, SagaState.EXCEPTION)

    val isInvalid: Boolean
        get() = this.sagaState in setOf(SagaState.CANCEL, SagaState.EXPIRED, SagaState.EXHAUSTED)

    val isExecuting: Boolean
        get() = SagaState.EXECUTING == this.sagaState

    val isExecuted: Boolean
        get() = SagaState.EXECUTED == this.sagaState

    fun beginSaga(now: LocalDateTime): Boolean {
        when {
            // 初始状态或者确认中或者异常
            !isValid -> return false
            // 超过重试次数
            this.triedTimes >= this.tryTimes -> {
                this.sagaState = SagaState.EXHAUSTED
                return false
            }
            // 事件过期
            now.isAfter(this.expireAt) -> {
                this.sagaState = SagaState.EXPIRED
                return false
            }
            // 未到下次重试时间
            !this.lastTryTime.isEqual(now) && this.nextTryTime.isAfter(now) -> return false
        }

        this.sagaState = SagaState.EXECUTING
        this.lastTryTime = now
        this.triedTimes++
        this.nextTryTime = calculateNextTryTime(now)
        return true
    }

    fun endSaga(now: LocalDateTime, result: Any) {
        this.sagaState = SagaState.EXECUTED
        loadSagaResult(result)
    }

    fun cancelSaga(now: LocalDateTime): Boolean {
        if (isExecuted || isInvalid) {
            return false
        }
        this.sagaState = SagaState.CANCEL
        return true
    }

    fun occurredException(now: LocalDateTime, ex: Throwable) {
        if (isExecuted) {
            return
        }
        this.sagaState = SagaState.EXCEPTION
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.exception = sw.toString()
    }

    private fun calculateNextTryTime(now: LocalDateTime): LocalDateTime {
        val retry = sagaParam!!.javaClass.getAnnotation(Retry::class.java)
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
        return JSON.toJSONString(this, IgnoreNonFieldGetter, SkipTransientField)
    }

    enum class SagaState(val value: Int, val stateName: String) {
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
            fun valueOf(value: Int): SagaState? {
                return entries.find { it.value == value }
            }
        }

        class Converter : AttributeConverter<SagaState, Int> {
            override fun convertToDatabaseColumn(attribute: SagaState): Int {
                return attribute.value
            }

            override fun convertToEntityAttribute(dbData: Int): SagaState? {
                return valueOf(dbData)
            }
        }
    }
}
