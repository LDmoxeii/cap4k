package com.only4.cap4k.ddd.application.saga.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

/**
 * SAGA事务-子环节(存档)
 *
 * @author LD_moxeii
 * @date 2025/08/01
 */
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
class ArchivedSagaProcess {

    override fun toString(): String {
        return JSON.toJSONString(this, IgnoreNonFieldGetter, SkipTransientField)
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
    @Convert(converter = SagaProcess.SagaProcessState.Converter::class)
    lateinit var processState: SagaProcess.SagaProcessState

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
