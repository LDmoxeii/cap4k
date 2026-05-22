package com.only4.cap4k.ddd.application.saga.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.annotation.JSONField
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

@Aggregate(
    aggregate = "saga",
    name = "SagaProcess",
    root = false,
    type = Aggregate.TYPE_ENTITY,
    relevant = ["Saga"],
    description = "SAGA事务-子环节"
)
@Entity
@Table(name = "`__saga_process`")
@DynamicInsert
@DynamicUpdate
class SagaProcess(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null,

    /**
     * SAGA处理环节代码
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`process_code`", nullable = false)
    var processCode: String = "",

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
     * 正向流程完成时间
     * datetime (nullable)
     */
    @Column(name = "`executed_at`")
    var executedAt: LocalDateTime? = null,

    /**
     * 补偿流程代码
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`compensation_code`", nullable = false)
    var compensationCode: String = "",

    /**
     * 补偿参数
     * text (nullable)
     */
    @Column(name = "`compensation_param`")
    var compensationParam: String = "",

    /**
     * 补偿参数类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`compensation_param_type`", nullable = false)
    var compensationParamType: String = "",

    /**
     * 补偿结果
     * text (nullable)
     */
    @Column(name = "`compensation_result`")
    var compensationResult: String = "",

    /**
     * 补偿结果类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`compensation_result_type`", nullable = false)
    var compensationResultType: String = "",

    /**
     * 补偿异常
     * text (nullable)
     */
    @Column(name = "`compensation_exception`")
    var compensationException: String? = null,

    /**
     * 补偿状态
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`compensation_state`", nullable = false)
    @Convert(converter = SagaCompensationState.Converter::class)
    var compensationState: SagaCompensationState = SagaCompensationState.NONE,

    /**
     * 补偿上次尝试时间
     * datetime (nullable)
     */
    @Column(name = "`compensation_last_try_time`")
    var compensationLastTryTime: LocalDateTime? = null,

    /**
     * 补偿尝试次数
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`compensation_tried_times`", nullable = false)
    var compensationTriedTimes: Int = 0,

    /**
     * 补偿完成时间
     * datetime (nullable)
     */
    @Column(name = "`compensated_at`")
    var compensatedAt: LocalDateTime? = null,

    /**
     * 执行状态
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`process_state`", nullable = false)
    @Convert(converter = SagaProcessState.Converter::class)
    var processState: SagaProcessState = SagaProcessState.INIT,

    /**
     * 创建时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`", insertable = false, updatable = true)
    var createAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 上次尝试时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`", insertable = false, updatable = true)
    var lastTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 尝试次数
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`", nullable = false)
    var triedTimes: Int = 0,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SagaProcess::class.java)
    }

    @Transient
    @JSONField(serialize = false)
    var sagaProcessResult: Any? = null
        get() {
            if (field != null) {
                return field
            }
            if (resultType.isNotBlank()) {
                val dataClass = try {
                    Class.forName(resultType)
                } catch (e: ClassNotFoundException) {
                    log.error("返回类型解析错误", e)
                    throw ClassNotFoundException("无法找到结果类型: $resultType", e)
                }
                field = JSON.parseObject(result, dataClass, Feature.SupportNonPublicField)
            }
            return field
        }
        private set

    @Transient
    @JSONField(serialize = false)
    var compensationRequestParam: RequestParam<*>? = null
        get() {
            if (field != null) {
                return field
            }
            if (compensationParamType.isNotBlank()) {
                val dataClass = try {
                    Class.forName(compensationParamType)
                } catch (e: ClassNotFoundException) {
                    log.error("补偿参数类型解析错误", e)
                    throw ClassNotFoundException("无法找到补偿参数类型: $compensationParamType", e)
                }
                field = JSON.parseObject(compensationParam, dataClass, Feature.SupportNonPublicField) as RequestParam<*>?
            }
            return field
        }
        private set

    @Transient
    @JSONField(serialize = false)
    var compensationProcessResult: Any? = null
        get() {
            if (field != null) {
                return field
            }
            if (compensationResultType.isNotBlank()) {
                val dataClass = try {
                    Class.forName(compensationResultType)
                } catch (e: ClassNotFoundException) {
                    log.error("补偿返回类型解析错误", e)
                    throw ClassNotFoundException("无法找到补偿结果类型: $compensationResultType", e)
                }
                field = JSON.parseObject(compensationResult, dataClass, Feature.SupportNonPublicField)
            }
            return field
        }
        private set

    fun beginProcess(now: LocalDateTime, param: RequestParam<*>): SagaProcess = apply {
        this.param = JSON.toJSONString(param, IgnoreNonFieldGetter, SkipTransientField)
        this.paramType = param.javaClass.name
        this.processState = SagaProcessState.EXECUTING
        this.lastTryTime = now
    }

    fun endProcess(now: LocalDateTime, result: Any): SagaProcess = apply {
        this.sagaProcessResult = result
        this.result = JSON.toJSONString(result, IgnoreNonFieldGetter, SkipTransientField)
        this.resultType = result.javaClass.name
        this.processState = SagaProcessState.EXECUTED
        this.executedAt = now
        this.lastTryTime = now
    }

    fun occurredException(now: LocalDateTime, ex: Throwable): SagaProcess = apply {
        this.result = JSON.toJSONString(null)
        this.resultType = Any::class.java.name
        this.processState = SagaProcessState.EXCEPTION
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.exception = sw.toString()
    }

    fun registerCompensation(compensationCode: String, param: RequestParam<*>): SagaProcess = apply {
        this.compensationCode = compensationCode
        this.compensationRequestParam = param
        this.compensationParam = JSON.toJSONString(param, IgnoreNonFieldGetter, SkipTransientField)
        this.compensationParamType = param.javaClass.name
        this.compensationState = SagaCompensationState.READY
    }

    fun beginCompensation(now: LocalDateTime): SagaProcess = apply {
        this.compensationState = SagaCompensationState.COMPENSATING
        this.compensationLastTryTime = now
        this.compensationTriedTimes++
    }

    fun endCompensation(now: LocalDateTime, result: Any = Unit): SagaProcess = apply {
        this.compensationProcessResult = result
        this.compensationResult = JSON.toJSONString(result, IgnoreNonFieldGetter, SkipTransientField)
        this.compensationResultType = result.javaClass.name
        this.compensationException = null
        this.compensationState = SagaCompensationState.COMPENSATED
        this.compensationLastTryTime = now
        this.compensatedAt = now
    }

    fun occurredCompensationException(now: LocalDateTime, ex: Throwable): SagaProcess = apply {
        this.compensationState = SagaCompensationState.FAILED
        this.compensationLastTryTime = now
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.compensationException = sw.toString()
    }

    enum class SagaProcessState(val value: Int, val stateName: String) {
        /**
         * 初始状态
         */
        INIT(0, "init"),

        /**
         * 待确认结果
         */
        EXECUTING(-1, "executing"),

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
            fun valueOf(value: Int): SagaProcessState? {
                return entries.find { it.value == value }
            }
        }

        class Converter : AttributeConverter<SagaProcessState, Int> {
            override fun convertToDatabaseColumn(attribute: SagaProcessState): Int {
                return attribute.value
            }

            override fun convertToEntityAttribute(dbData: Int): SagaProcessState? {
                return valueOf(dbData)
            }
        }
    }

    enum class SagaCompensationState(val value: Int, val stateName: String) {
        NONE(0, "none"),
        READY(1, "ready"),
        COMPENSATING(-1, "compensating"),
        MANUAL_REPAIR_REQUIRED(-7, "manual-repair-required"),
        FAILED(-9, "failed"),
        COMPENSATED(2, "compensated");

        companion object {
            @JvmStatic
            fun valueOf(value: Int): SagaCompensationState? {
                return entries.find { it.value == value }
            }
        }

        class Converter : AttributeConverter<SagaCompensationState, Int> {
            override fun convertToDatabaseColumn(attribute: SagaCompensationState): Int {
                return attribute.value
            }

            override fun convertToEntityAttribute(dbData: Int): SagaCompensationState? {
                return valueOf(dbData)
            }
        }
    }
}
