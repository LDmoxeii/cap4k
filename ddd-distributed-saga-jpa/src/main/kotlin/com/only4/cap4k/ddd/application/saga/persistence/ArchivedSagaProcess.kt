package com.only4.cap4k.ddd.application.saga.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

@Entity
@Table(name = "`__archived_saga_process`")
@DynamicInsert
@DynamicUpdate
class ArchivedSagaProcess(
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
    @Convert(converter = SagaProcess.SagaCompensationState.Converter::class)
    var compensationState: SagaProcess.SagaCompensationState = SagaProcess.SagaCompensationState.NONE,

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
    @Convert(converter = SagaProcess.SagaProcessState.Converter::class)
    var processState: SagaProcess.SagaProcessState = SagaProcess.SagaProcessState.INIT,

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

    /**
     * 创建时间（数据库自动维护）
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`db_created_at`", insertable = false, updatable = false)
    var dbCreatedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 更新时间（数据库自动维护）
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     */
    @Column(name = "`db_updated_at`", insertable = false, updatable = false)
    var dbUpdatedAt: LocalDateTime = LocalDateTime.now(),
) {

    fun archiveFrom(sagaProcess: SagaProcess): ArchivedSagaProcess = apply {
        this.processCode = sagaProcess.processCode
        this.param = sagaProcess.param
        this.paramType = sagaProcess.paramType
        this.result = sagaProcess.result
        this.resultType = sagaProcess.resultType
        this.exception = sagaProcess.exception
        this.executedAt = sagaProcess.executedAt
        this.compensationCode = sagaProcess.compensationCode
        this.compensationParam = sagaProcess.compensationParam
        this.compensationParamType = sagaProcess.compensationParamType
        this.compensationResult = sagaProcess.compensationResult
        this.compensationResultType = sagaProcess.compensationResultType
        this.compensationException = sagaProcess.compensationException
        this.compensationState = sagaProcess.compensationState
        this.compensationLastTryTime = sagaProcess.compensationLastTryTime
        this.compensationTriedTimes = sagaProcess.compensationTriedTimes
        this.compensatedAt = sagaProcess.compensatedAt
        this.processState = sagaProcess.processState
        this.createAt = sagaProcess.createAt
        this.triedTimes = sagaProcess.triedTimes
        this.lastTryTime = sagaProcess.lastTryTime
    }

    fun updateProcessState(newState: SagaProcess.SagaProcessState): ArchivedSagaProcess = apply {
        this.processState = newState
    }

    fun updateResult(result: String, resultType: String): ArchivedSagaProcess = apply {
        this.result = result
        this.resultType = resultType
    }

    fun updateException(exception: String?): ArchivedSagaProcess = apply {
        this.exception = exception
    }

    fun incrementTriedTimes(): ArchivedSagaProcess = apply {
        this.triedTimes++
        this.lastTryTime = LocalDateTime.now()
    }

    override fun toString(): String {
        return JSON.toJSONString(this, IgnoreNonFieldGetter, SkipTransientField)
    }
}
