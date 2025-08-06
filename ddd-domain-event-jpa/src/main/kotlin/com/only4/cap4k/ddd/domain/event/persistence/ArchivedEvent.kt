package com.only4.cap4k.ddd.domain.event.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.domain.event.persistence.Event.EventState
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
class ArchivedEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null,

    /**
     * 事件uuid
     * varchar(64) NOT NULL DEFAULT ''
     */
    @Column(name = "`event_uuid`", nullable = false)
    var eventUuid: String = "",

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`", nullable = false)
    var svcName: String = "",

    /**
     * 事件类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`event_type`", nullable = false)
    var eventType: String = "",

    /**
     * 事件数据
     * text (nullable)
     */
    @Column(name = "`data`")
    var data: String = "",

    /**
     * 事件数据类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`data_type`", nullable = false)
    var dataType: String = "",

    /**
     * 异常信息
     * text (nullable)
     */
    @Column(name = "`exception`")
    var exception: String? = null,

    /**
     * 过期时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`")
    var expireAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 创建时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`")
    var createAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 分发状态
     * int(11) NOT NULL DEFAULT '0'
     */
    @Column(name = "`event_state`", nullable = false)
    @Convert(converter = EventState.Converter::class)
    var eventState: EventState = EventState.INIT,

    /**
     * 上次尝试时间
     * datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`")
    var lastTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 下次尝试时间
     * datetime NOT NULL DEFAULT '0001-01-01 00:00:00'
     */
    @Column(name = "`next_try_time`")
    var nextTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 已尝试次数
     * int(11) NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`", nullable = false)
    var triedTimes: Int = 0,

    /**
     * 尝试次数
     * int(11) NOT NULL DEFAULT '0'
     */
    @Column(name = "`try_times`", nullable = false)
    var tryTimes: Int = 0,

    /**
     * 乐观锁
     * int(11) NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`", nullable = false)
    var version: Int = 0,

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

    companion object {
        /**
         * 从Event创建存档实体
         */
        fun fromEvent(event: Event): ArchivedEvent = ArchivedEvent().apply {
            archiveFrom(event)
        }

        /**
         * 创建新的存档Event实例
         */
        fun create(
            eventUuid: String,
            svcName: String,
            eventType: String,
            data: String = "",
            dataType: String = "",
        ): ArchivedEvent = ArchivedEvent(
            eventUuid = eventUuid,
            svcName = svcName,
            eventType = eventType,
            data = data,
            dataType = dataType
        )
    }

    fun archiveFrom(event: Event): ArchivedEvent = apply {
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

    fun updateEventState(newState: EventState): ArchivedEvent = apply {
        this.eventState = newState
    }

    fun updateException(exception: String?): ArchivedEvent = apply {
        this.exception = exception
    }

    fun incrementTriedTimes(): ArchivedEvent = apply {
        this.triedTimes++
        this.lastTryTime = LocalDateTime.now()
    }

    override fun toString(): String {
        return JSON.toJSONString(this, IgnoreNonFieldGetter, SkipTransientField)
    }
}
