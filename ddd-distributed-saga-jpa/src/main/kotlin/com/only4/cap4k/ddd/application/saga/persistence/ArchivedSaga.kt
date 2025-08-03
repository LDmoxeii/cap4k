package com.only4.cap4k.ddd.application.saga.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.application.saga.persistence.Saga.SagaState
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

/**
 * SAGA事务(存档)
 *
 * @author LD_moxeii
 * @date 2025/08/01
 */
@Aggregate(
    aggregate = "archived_saga",
    name = "ArchivedSaga",
    root = true,
    type = Aggregate.TYPE_ENTITY,
    description = "SAGA事务(存档)"
)
@Entity
@Table(name = "`__archived_saga`")
@DynamicInsert
@DynamicUpdate
class ArchivedSaga {

    fun archiveFrom(saga: Saga) {
        this.id = saga.id
        this.sagaUuid = saga.sagaUuid
        this.svcName = saga.svcName
        this.sagaType = saga.sagaType
        this.param = saga.param
        this.paramType = saga.paramType
        this.result = saga.result
        this.resultType = saga.resultType
        this.exception = saga.exception
        this.expireAt = saga.expireAt
        this.createAt = saga.createAt
        this.sagaState = saga.sagaState
        this.nextTryTime = saga.nextTryTime
        this.triedTimes = saga.triedTimes
        this.tryTimes = saga.tryTimes
        this.version = saga.version
        this.sagaProcesses = saga.sagaProcesses.map { p ->
            ArchivedSagaProcess().apply {
                processCode = p.processCode
                param = p.param
                paramType = p.paramType
                result = p.result
                resultType = p.resultType
                exception = p.exception
                processState = p.processState
                createAt = p.createAt
                triedTimes = p.triedTimes
                lastTryTime = p.lastTryTime
            }
        }.toMutableList()
    }

    override fun toString(): String {
        return JSON.toJSONString(this, IgnoreNonFieldGetter, SkipTransientField)
    }

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "`saga_id`", nullable = false)
    var sagaProcesses: MutableList<ArchivedSagaProcess> = mutableListOf()
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null

    /**
     * SAGA uuid
     * varchar(64) NOT NULL DEFAULT ''
     */
    @Column(name = "`saga_uuid`", nullable = false)
    lateinit var sagaUuid: String

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`", nullable = false)
    lateinit var svcName: String

    /**
     * SAGA类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`saga_type`", nullable = false)
    lateinit var sagaType: String

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
     * 过期时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`")
    lateinit var expireAt: LocalDateTime

    /**
     * 创建时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`")
    lateinit var createAt: LocalDateTime

    /**
     * 执行状态
     * int NOT NULL DEFAULT '0'
     */
    @Column(name = "`saga_state`", nullable = false)
    @Convert(converter = SagaState.Converter::class)
    lateinit var sagaState: SagaState

    /**
     * 上次尝试时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`")
    lateinit var lastTryTime: LocalDateTime

    /**
     * 下次尝试时间
     * datetime NOT NULL DEFAULT '0001-01-01 00:00:00'
     */
    @Column(name = "`next_try_time`")
    lateinit var nextTryTime: LocalDateTime

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

    /**
     * 创建时间（数据库自动维护）
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`db_created_at`", insertable = false, updatable = false)
    lateinit var dbCreatedAt: LocalDateTime

    /**
     * 更新时间（数据库自动维护）
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     */
    @Column(name = "`db_updated_at`", insertable = false, updatable = false)
    lateinit var dbUpdatedAt: LocalDateTime
}
