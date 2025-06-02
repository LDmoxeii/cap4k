package com.only4.cap4k.ddd.domain.event.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.annotation.JSONField
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.annotation.Retry
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 事件实体
 */
@Aggregate(aggregate = "event", name = "Event", root = true, type = Aggregate.TYPE_ENTITY, description = "事件")
@Entity
@Table(name = "`__event`")
@DynamicInsert
@DynamicUpdate
data class Event(
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    val id: Long = 0L,

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
    @Convert(converter = EventState.Converter::class)
    var eventState: EventState = EventState.INIT,

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
    var lastTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 下次尝试时间
     */
    @Column(name = "`next_try_time`")
    var nextTryTime: LocalDateTime = LocalDateTime.MIN,

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
    val dbCreatedAt: LocalDateTime? = null,

    /**
     * 更新时间（数据库自动维护）
     */
    @Column(name = "`db_updated_at`", insertable = false, updatable = false)
    val dbUpdatedAt: Date? = null
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Event::class.java)

        const val F_EVENT_UUID = "eventUuid"
        const val F_SVC_NAME = "svcName"
        const val F_EVENT_TYPE = "eventType"
        const val F_DATA = "data"
        const val F_DATA_TYPE = "dataType"
        const val F_EXCEPTION = "exception"
        const val F_CREATE_AT = "createAt"
        const val F_EXPIRE_AT = "expireAt"
        const val F_EVENT_STATE = "eventState"
        const val F_TRY_TIMES = "tryTimes"
        const val F_TRIED_TIMES = "triedTimes"
        const val F_LAST_TRY_TIME = "lastTryTime"
        const val F_NEXT_TRY_TIME = "nextTryTime"

    }

    @field:Transient
    @field:JSONField(serialize = false)
    var payload: Any = Any()
        get() {
            if (dataType.isNotBlank()) {
                try {
                    val dataClass = Class.forName(dataType)
                    field = JSON.parseObject(data, dataClass, Feature.SupportNonPublicField)
                } catch (e: ClassNotFoundException) {
                    logger.error("事件类型解析错误: $dataType", e)
                }
            }
            return field
        }

    /**
     * 初始化事件
     */
    fun init(payload: Any, svcName: String, scheduleAt: LocalDateTime, expireAfter: Duration, retryTimes: Int) {
        this.eventUuid = UUID.randomUUID().toString()
        this.svcName = svcName
        this.createAt = scheduleAt
        this.expireAt = scheduleAt.plusSeconds(expireAfter.seconds)
        this.eventState = EventState.INIT
        this.tryTimes = retryTimes
        this.triedTimes = 1
        this.lastTryTime = scheduleAt
        this.nextTryTime = calculateNextTryTime(scheduleAt)
        loadPayload(payload)
    }

    /**
     * 加载事件负载
     */
    private fun loadPayload(payload: Any) {
        this.payload = payload
        this.data =
            JSON.toJSONString(payload, SerializerFeature.IgnoreNonFieldGetter, SerializerFeature.SkipTransientField)
        this.dataType = payload.javaClass.name

        val integrationEvent = payload.javaClass.getAnnotation(IntegrationEvent::class.java)
        val domainEvent = payload.javaClass.getAnnotation(DomainEvent::class.java)

        this.eventType = when {
            integrationEvent != null -> integrationEvent.value
            domainEvent != null -> domainEvent.value
            else -> ""
        }

        val retry = payload.javaClass.getAnnotation(Retry::class.java)
        if (retry != null) {
            this.tryTimes = retry.retryTimes
            this.expireAt = this.createAt.plusMinutes(retry.expireAfter.toLong())
        }
    }

    /**
     * 事件是否有效
     */
    val isValid: Boolean
        get() = eventState == EventState.INIT ||
                eventState == EventState.DELIVERING ||
                eventState == EventState.EXCEPTION

    /**
     * 事件是否无效
     */
    val isInvalid: Boolean
        get() = eventState == EventState.CANCEL ||
                eventState == EventState.EXPIRED ||
                eventState == EventState.EXHAUSTED

    /**
     * 事件是否已发送
     */
    val isDelivered: Boolean
        get() = eventState == EventState.DELIVERED

    /**
     * 为发送保持状态
     */
    fun holdState4Delivery(now: LocalDateTime): Boolean {
        // 超过重试次数
        if (triedTimes >= tryTimes) {
            eventState = EventState.EXHAUSTED
            return false
        }

        // 事件过期
        if (now.isAfter(expireAt)) {
            eventState = EventState.EXPIRED
            return false
        }

        // 初始状态或者确认中或者异常
        if (!isValid) {
            return false
        }

        // 未到下次重试时间
        if (nextTryTime.isAfter(now)) {
            return false
        }

        eventState = EventState.DELIVERING
        lastTryTime = now
        nextTryTime = calculateNextTryTime(now)
        triedTimes++

        return true
    }

    /**
     * 确认发送
     */
    fun confirmedDelivery(now: LocalDateTime) {
        eventState = EventState.DELIVERED
    }

    /**
     * 取消发送
     */
    fun cancelDelivery(now: LocalDateTime): Boolean {
        if (isDelivered || isInvalid) {
            return false
        }
        eventState = EventState.CANCEL
        return true
    }

    /**
     * 发生异常
     */
    fun occurredException(now: LocalDateTime, ex: Throwable) {
        if (isDelivered) {
            return
        }
        eventState = EventState.EXCEPTION
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        exception = sw.toString()
    }

    /**
     * 计算下次重试时间
     */
    private fun calculateNextTryTime(now: LocalDateTime): LocalDateTime {
        val retry = payload?.javaClass?.getAnnotation(Retry::class.java)

        if (retry == null || retry.retryIntervals.isEmpty()) {
            return when {
                triedTimes <= 10 -> now.plusMinutes(1)
                triedTimes <= 20 -> now.plusMinutes(5)
                else -> now.plusMinutes(10)
            }
        }

        var index = triedTimes - 1
        when {
            index >= retry.retryIntervals.size -> index = retry.retryIntervals.size - 1
            index < 0 -> index = 0
        }

        return now.plusMinutes(retry.retryIntervals[index].toLong())
    }

    /**
     * 转为字符串
     */
    override fun toString(): String {
        return JSON.toJSONString(this, SerializerFeature.IgnoreNonFieldGetter, SerializerFeature.SkipTransientField)
    }

    /**
     * 事件状态枚举
     */
    enum class EventState(val value: Int, val stateName: String) {
        /**
         * 初始状态
         */
        INIT(0, "init"),

        /**
         * 待确认发送结果
         */
        DELIVERING(-1, "delivering"),

        /**
         * 业务主动取消
         */
        CANCEL(-2, "cancel"),

        /**
         * 过期
         */
        EXPIRED(-3, "expired"),

        /**
         * 用完重试次数
         */
        EXHAUSTED(-4, "exhausted"),

        /**
         * 发送异常
         */
        EXCEPTION(-9, "exception"),

        /**
         * 已发送
         */
        DELIVERED(1, "delivered");

        companion object {
            fun valueOf(value: Int): EventState? {
                return entries.find { it.value == value }
            }
        }

        /**
         * JPA枚举转换器
         */
        class Converter : AttributeConverter<EventState, Int> {
            override fun convertToDatabaseColumn(attribute: EventState): Int {
                return attribute.value
            }

            override fun convertToEntityAttribute(dbData: Int): EventState? {
                return valueOf(dbData)
            }
        }
    }
}
