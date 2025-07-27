package com.only4.cap4k.ddd.domain.event.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

/**
 * 归档事件
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
@Entity
@Table(name = "`__archived_event`")
@DynamicInsert
@DynamicUpdate
class ArchivedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null

    /**
     * 事件uuid
     * varchar(64) NOT NULL DEFAULT ''
     */
    @Column(name = "`event_uuid`", nullable = false)
    var eventUuid: String = ""

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`", nullable = false)
    var svcName: String = ""

    /**
     * 事件类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`event_type`", nullable = false)
    var eventType: String = ""

    /**
     * 事件数据
     * text (nullable)
     */
    @Column(name = "`data`")
    var data: String? = null

    /**
     * 事件数据类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`data_type`", nullable = false)
    var dataType: String = ""

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
     * 分发状态
     * int(11) NOT NULL DEFAULT '0'
     */
    @Column(name = "`event_state`", nullable = false)
    @Convert(converter = Event.EventState.Converter::class)
    var eventState: Event.EventState = Event.EventState.INIT

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
     * int(11) NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`", nullable = false)
    var triedTimes: Int = 0

    /**
     * 尝试次数
     * int(11) NOT NULL DEFAULT '0'
     */
    @Column(name = "`try_times`", nullable = false)
    var tryTimes: Int = 0

    /**
     * 乐观锁
     * int(11) NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`", nullable = false)
    var version: Int = 0

    /**
     * 创建时间（数据库自动维护）
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`db_created_at`", insertable = false, updatable = false)
    var dbCreatedAt: LocalDateTime? = null

    /**
     * 更新时间（数据库自动维护）
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     */
    @Column(name = "`db_updated_at`", insertable = false, updatable = false)
    var dbUpdatedAt: LocalDateTime? = null

    fun archiveFrom(event: Event) {
        this.id = event.id
        this.eventUuid = event.eventUuid
        this.svcName = event.svcName
        this.eventType = event.eventType
        this.data = event.data
        this.dataType = event.dataType
        this.exception = event.exception
        this.createAt = event.createAt
        this.expireAt = event.expireAt
        this.eventState = event.eventState
        this.tryTimes = event.tryTimes
        this.triedTimes = event.triedTimes
        this.lastTryTime = event.lastTryTime
        this.nextTryTime = event.nextTryTime
        this.version = event.version
    }

    override fun toString(): String {
        return JSON.toJSONString(this, IgnoreNonFieldGetter, SkipTransientField)
    }
}
