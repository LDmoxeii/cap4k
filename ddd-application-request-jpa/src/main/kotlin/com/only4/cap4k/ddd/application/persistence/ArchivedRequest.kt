package com.only4.cap4k.ddd.application.persistence

import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * 归档请求实体
 *
 * @author binking338
 * @date 2025/5/16
 */
@Aggregate(
    aggregate = "archived_request",
    name = "ArchivedRequest",
    root = true,
    type = Aggregate.TYPE_ENTITY,
    description = "请求记录"
)
@Entity
@Table(name = "`__archived_request`")
@DynamicInsert
@DynamicUpdate
class ArchivedRequest {
    // 【行为方法开始】
    fun archiveFrom(request: Request) {
        this.id = request.id
        this.requestUuid = request.requestUuid
        this.svcName = request.svcName
        this.requestType = request.requestType
        this.param = request.param
        this.paramType = request.paramType
        this.result = request.result
        this.resultType = request.resultType
        this.exception = request.exception
        this.expireAt = request.expireAt
        this.createAt = request.createAt
        this.requestState = request.requestState
        this.nextTryTime = request.nextTryTime
        this.triedTimes = request.triedTimes
        this.tryTimes = request.tryTimes
        this.version = request.version
    }
    // 【行为方法结束】

    // 【字段映射开始】本段落由[cap4j-ddd-codegen-maven-plugin]维护，请不要手工改动
    /**
     * bigint
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null

    /**
     * REQUEST uuid
     * varchar(64)  NOT NULL DEFAULT ''
     */
    @Column(name = "`request_uuid`")
    var requestUuid: String = ""

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`")
    var svcName: String = ""

    /**
     * REQUEST类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`request_type`")
    var requestType: String = ""

    /**
     * 参数
     * text
     */
    @Column(name = "`param`")
    var param: String? = null

    /**
     * 参数类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`param_type`")
    var paramType: String = ""

    /**
     * 结果
     * text
     */
    @Column(name = "`result`")
    var result: String? = null

    /**
     * 结果类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`result_type`")
    var resultType: String = ""

    /**
     * 执行异常
     * text
     */
    @Column(name = "`exception`")
    var exception: String? = null

    /**
     * 过期时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`")
    var expireAt: LocalDateTime? = null

    /**
     * 创建时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`")
    var createAt: LocalDateTime? = null

    /**
     * 执行状态@E=0:INIT:init|-1:EXECUTING:executing|-2:CANCEL:cancel|-3:EXPIRED:expired|-4:EXHAUSTED:exhausted|-9:EXCEPTION:exception|1:EXECUTED:executed;@T=RequestState;
     * int          NOT NULL DEFAULT '0'
     */
    @Column(name = "`request_state`")
    @Convert(converter = Request.RequestState.Converter::class)
    var requestState: Request.RequestState = Request.RequestState.INIT

    /**
     * 上次尝试时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`")
    var lastTryTime: LocalDateTime? = null

    /**
     * 下次尝试时间
     * datetime     NOT NULL DEFAULT '0001-01-01 00:00:00'
     */
    @Column(name = "`next_try_time`")
    var nextTryTime: LocalDateTime? = null

    /**
     * 已尝试次数
     * int(11)      NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`")
    var triedTimes: Int = 0

    /**
     * 尝试次数
     * int(11)      NOT NULL DEFAULT '0'
     */
    @Column(name = "`try_times`")
    var tryTimes: Int = 0

    /**
     * 数据版本（支持乐观锁）
     * int          NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`")
    var version: Int = 0
    // 【字段映射结束】本段落由[cap4j-ddd-codegen-maven-plugin]维护，请不要手工改动
}
