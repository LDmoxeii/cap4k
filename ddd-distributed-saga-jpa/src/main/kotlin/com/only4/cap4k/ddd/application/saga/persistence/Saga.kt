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
class Saga {

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

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "`saga_id`", nullable = false)
    var sagaProcesses: MutableList<SagaProcess>? = null

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null

    /**
     * SAGA uuid
     * varchar(64) NOT NULL DEFAULT ''
     */
    @Column(name = "`saga_uuid`", nullable = false)
    var sagaUuid: String = ""

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`", nullable = false)
    var svcName: String = ""

    /**
     * SAGA类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`saga_type`", nullable = false)
    var sagaType: String = ""

    /**
     * 参数
     * text (nullable)
     */
    @Column(name = "`param`")
    var param: String? = null

    /**
     * 参数类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`param_type`", nullable = false)
    var paramType: String = ""

    /**
     * 结果
     * text (nullable)
     */
    @Column(name = "`result`")
    var result: String? = null

    /**
     * 结果类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`result_type`", nullable = false)
    var resultType: String = ""

    /**
     * 异常信息
     * text (nullable)
     */
    @Column(name = "`exception`")
    var exception: String? = null

    /**
     * 过期时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`")
    var expireAt: LocalDateTime? = null

    /**
     * 创建时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`")
    var createAt: LocalDateTime? = null

    /**
     * 执行状态
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`saga_state`", nullable = false)
    @Convert(converter = SagaState.Converter::class)
    var sagaState: SagaState = SagaState.INIT

    /**
     * 上次尝试时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`")
    var lastTryTime: LocalDateTime? = null

    /**
     * 下次尝试时间
     * datetime NOT NULL DEFAULT '0001-01-01 00:00:00'
     */
    @Column(name = "`next_try_time`")
    var nextTryTime: LocalDateTime? = null

    /**
     * 已尝试次数
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`", nullable = false)
    var triedTimes: Int = 0

    /**
     * 尝试次数
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`try_times`", nullable = false)
    var tryTimes: Int = 0

    /**
     * 乐观锁
     * int NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`", nullable = false)
    var version: Int = 0

    @Transient
    @JSONField(serialize = false)
    private var sagaParam: SagaParam<*>? = null

    @Transient
    @JSONField(serialize = false)
    private var sagaResult: Any? = null

    fun init(
        sagaParam: SagaParam<*>,
        svcName: String,
        sagaType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    ) {
        this.sagaUuid = UUID.randomUUID().toString()
        this.svcName = svcName
        this.sagaType = sagaType
        this.createAt = scheduleAt
        this.expireAt = scheduleAt.plusSeconds(expireAfter.seconds)
        this.sagaState = SagaState.INIT
        this.tryTimes = retryTimes
        this.triedTimes = 0
        this.lastTryTime = scheduleAt
        this.nextTryTime = calculateNextTryTime(scheduleAt)
        this.loadSagaParam(sagaParam)
        this.result = ""
        this.resultType = ""
        this.sagaProcesses = ArrayList()
    }

    private fun loadSagaParam(sagaParam: SagaParam<*>?) {
        if (sagaParam == null) {
            throw DomainException("Saga参数不能为null")
        }
        this.sagaParam = sagaParam
        this.param = JSON.toJSONString(sagaParam, IgnoreNonFieldGetter, SkipTransientField)
        this.paramType = sagaParam.javaClass.name

        val retry = sagaParam.javaClass.getAnnotation(Retry::class.java)
        if (retry != null) {
            this.tryTimes = retry.retryTimes
            this.expireAt = this.createAt?.plusMinutes(retry.expireAfter.toLong())
        }
    }

    private fun loadSagaResult(result: Any?) {
        if (result == null) {
            throw DomainException("Saga返回不能为null")
        }
        this.sagaResult = result
        this.result = JSON.toJSONString(result, IgnoreNonFieldGetter, SkipTransientField)
        this.resultType = result.javaClass.name
    }

    fun getSagaParam(): SagaParam<*>? {
        if (this.sagaParam != null) {
            return this.sagaParam
        }
        if (paramType.isNotBlank()) {
            var dataClass: Class<*>? = null
            try {
                dataClass = Class.forName(paramType)
            } catch (e: ClassNotFoundException) {
                log.error("参数类型解析错误", e)
            }
            this.sagaParam = JSON.parseObject(param, dataClass, Feature.SupportNonPublicField) as SagaParam<*>?
        }
        return this.sagaParam
    }

    fun getSagaResult(): Any? {
        if (this.sagaResult != null) {
            return this.sagaResult
        }

        if (resultType.isNotBlank()) {
            var dataClass: Class<*>? = null
            try {
                dataClass = Class.forName(resultType)
            } catch (e: ClassNotFoundException) {
                log.error("返回类型解析错误", e)
            }
            this.sagaResult = JSON.parseObject(result, dataClass, Feature.SupportNonPublicField)
        }
        return this.sagaResult
    }

    fun getSagaProcess(processCode: String): SagaProcess? {
        if (this.sagaProcesses == null) {
            return null
        }
        return this.sagaProcesses!!.find { it.processCode == processCode }
    }

    fun beginSagaProcess(now: LocalDateTime, processCode: String, param: RequestParam<*>) {
        var sagaProcess = getSagaProcess(processCode)
        if (sagaProcess == null) {
            sagaProcess = SagaProcess().apply {
                this.processCode = processCode
                this.processState = SagaProcess.SagaProcessState.INIT
                this.createAt = now
                this.triedTimes = 0
            }
            if (this.sagaProcesses == null) {
                this.sagaProcesses = ArrayList()
            }
            this.sagaProcesses!!.add(sagaProcess)
        }
        sagaProcess.beginProcess(now, param)
    }

    fun endSagaProcess(now: LocalDateTime, processCode: String, result: Any) {
        val sagaProcess = getSagaProcess(processCode) ?: return
        sagaProcess.endProcess(now, result)
    }

    fun isValid(): Boolean {
        return SagaState.INIT == this.sagaState ||
                SagaState.EXECUTING == this.sagaState ||
                SagaState.EXCEPTION == this.sagaState
    }

    fun isInvalid(): Boolean {
        return SagaState.CANCEL == this.sagaState ||
                SagaState.EXPIRED == this.sagaState ||
                SagaState.EXHAUSTED == this.sagaState
    }

    fun isExecuting(): Boolean {
        return SagaState.EXECUTING == this.sagaState
    }

    fun isExecuted(): Boolean {
        return SagaState.EXECUTED == this.sagaState
    }

    fun beginSaga(now: LocalDateTime): Boolean {
        // 初始状态或者确认中或者异常
        if (!isValid()) {
            return false
        }
        // 超过重试次数
        if (this.triedTimes >= this.tryTimes) {
            this.sagaState = SagaState.EXHAUSTED
            return false
        }
        // 事件过期
        if (now.isAfter(this.expireAt)) {
            this.sagaState = SagaState.EXPIRED
            return false
        }
        // 未到下次重试时间
        if ((this.lastTryTime == null || !this.lastTryTime!!.isEqual(now)) &&
            this.nextTryTime != null && this.nextTryTime!!.isAfter(now)
        ) {
            return false
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
        if (isExecuted() || isInvalid()) {
            return false
        }
        this.sagaState = SagaState.CANCEL
        return true
    }

    fun occuredException(now: LocalDateTime, ex: Throwable) {
        if (isExecuted()) {
            return
        }
        this.sagaState = SagaState.EXCEPTION
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.exception = sw.toString()
    }

    private fun calculateNextTryTime(now: LocalDateTime): LocalDateTime {
        val retry = getSagaParam()?.javaClass?.getAnnotation(Retry::class.java)
        if (retry == null || retry.retryIntervals.isEmpty()) {
            return when {
                this.triedTimes <= 10 -> now.plusMinutes(1)
                this.triedTimes <= 20 -> now.plusMinutes(5)
                else -> now.plusMinutes(10)
            }
        }
        var index = this.triedTimes - 1
        if (index >= retry.retryIntervals.size) {
            index = retry.retryIntervals.size - 1
        } else if (index < 0) {
            index = 0
        }
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
            fun valueOf(value: Int): SagaState? {
                for (state in values()) {
                    if (state.value == value) {
                        return state
                    }
                }
                return null
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
