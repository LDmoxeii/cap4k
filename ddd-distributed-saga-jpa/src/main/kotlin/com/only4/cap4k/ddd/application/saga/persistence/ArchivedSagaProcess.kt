package com.only4.cap4k.ddd.application.saga.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

@Aggregate(
    aggregate = "archived_saga",
    name = "ArchivedSagaProcess",
    root = false,
    type = Aggregate.TYPE_ENTITY,
    relevant = ["ArchivedSaga"],
    description = "SAGA事务-子环节(存档)"
)
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
