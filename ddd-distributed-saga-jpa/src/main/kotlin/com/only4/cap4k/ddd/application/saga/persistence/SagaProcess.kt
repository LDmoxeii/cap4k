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

/**
 * SAGA事务-子环节
 *
 * @author LD_moxeii
 * @date 2025/08/01
 */
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
class SagaProcess {

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

    fun beginProcess(now: LocalDateTime, param: RequestParam<out Any>) {
        this.param = JSON.toJSONString(param, IgnoreNonFieldGetter, SkipTransientField)
        this.paramType = param.javaClass.name
        this.processState = SagaProcessState.EXECUTING
        this.lastTryTime = now
    }

    fun endProcess(now: LocalDateTime, result: Any) {
        this.sagaProcessResult = result
        this.result = JSON.toJSONString(result, IgnoreNonFieldGetter, SkipTransientField)
        this.resultType = result.javaClass.name
        this.processState = SagaProcessState.EXECUTED
        this.lastTryTime = now
    }

    fun occurredException(now: LocalDateTime, ex: Throwable) {
        this.result = JSON.toJSONString(null)
        this.resultType = Any::class.java.name
        this.processState = SagaProcessState.EXCEPTION
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.exception = sw.toString()
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

    companion object {
        private val log = LoggerFactory.getLogger(SagaProcess::class.java)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null

    /**
     * SAGA处理环节代码
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`process_code`", nullable = false)
    lateinit var processCode: String

    /**
     * 参数
     * text (nullable)
     */
    @Column(name = "`param`")
    lateinit var param: String

    /**
     * 参数类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`param_type`", nullable = false)
    lateinit var paramType: String

    /**
     * 结果
     * text (nullable)
     */
    @Column(name = "`result`")
    lateinit var result: String

    /**
     * 结果类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`result_type`", nullable = false)
    lateinit var resultType: String

    /**
     * 异常信息
     * text (nullable)
     */
    @Column(name = "`exception`")
    var exception: String? = null

    /**
     * 执行状态
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`process_state`", nullable = false)
    @Convert(converter = SagaProcessState.Converter::class)
    lateinit var processState: SagaProcessState

    /**
     * 创建时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`", insertable = false, updatable = true)
    lateinit var createAt: LocalDateTime

    /**
     * 上次尝试时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`", insertable = false, updatable = true)
    lateinit var lastTryTime: LocalDateTime

    /**
     * 尝试次数
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`", nullable = false)
    var triedTimes: Int = 0
}
