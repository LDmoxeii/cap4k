package com.only4.cap4k.ddd.domain.event.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime
import java.util.*

/**
 * 归档事件
 */
@Entity
@Table(name = "`__archived_event`")
@DynamicInsert
@DynamicUpdate
data class ArchivedEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    val id: Long? = null,

    /**
     * 事件uuid
     */
    @Column(name = "`event_uuid`")
    var eventUuid: String = "",

    /**
     * 服务
     */
    @Column(name = "`svc_name`")
    var svcName: String = "",

    /**
     * 事件类型
     */
    @Column(name = "`event_type`")
    var eventType: String = "",

    /**
     * 事件数据
     */
    @Column(name = "`data`")
    var data: String = "",

    /**
     * 事件数据类型
     */
    @Column(name = "`data_type`")
    var dataType: String = "",

    /**
     * 异常信息
     */
    @Column(name = "`exception`")
    var exception: String = "",

    /**
     * 创建时间
     */
    @Column(name = "`create_at`")
    var createAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 过期时间
     */
    @Column(name = "`expire_at`")
    var expireAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 分发状态
     */
    @Column(name = "`event_state`")
    @Convert(converter = Event.EventState.Converter::class)
    var eventState: Event.EventState = Event.EventState.INIT,

    /**
     * 尝试次数
     */
    @Column(name = "`try_times`")
    var tryTimes: Int = 0,

    /**
     * 已尝试次数
     */
    @Column(name = "`tried_times`")
    var triedTimes: Int = 0,

    /**
     * 上次尝试时间
     */
    @Column(name = "`last_try_time`")
    var lastTryTime: LocalDateTime? = null,

    /**
     * 下次尝试时间
     */
    @Column(name = "`next_try_time`")
    var nextTryTime: LocalDateTime? = null,

    /**
     * 乐观锁
     */
    @Version
    @Column(name = "`version`")
    var version: Int = 0,

    /**
     * 创建时间（数据库自动维护）
     */
    @Column(name = "`db_created_at`", insertable = false, updatable = false)
    var dbCreatedAt: LocalDateTime? = null,

    /**
     * 更新时间（数据库自动维护）
     */
    @Column(name = "`db_updated_at`", insertable = false, updatable = false)
    var dbUpdatedAt: Date? = null
) {
    /**
     * 从Event复制数据
     */
    fun archiveFrom(event: Event) {
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

    /**
     * 转为字符串
     */
    override fun toString(): String {
        return JSON.toJSONString(
            this,
            SerializerFeature.IgnoreNonFieldGetter,
            SerializerFeature.SkipTransientField
        )
    }
}
