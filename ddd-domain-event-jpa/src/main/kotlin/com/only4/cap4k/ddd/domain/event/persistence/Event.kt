package com.only4.cap4k.ddd.domain.event.persistence

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.annotation.JSONField
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature.IgnoreNonFieldGetter
import com.alibaba.fastjson.serializer.SerializerFeature.SkipTransientField
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.DomainException
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
 * 事件
 *
 * 本文件由[cap4j-ddd-codegen-maven-plugin]生成
 * 警告：请勿手工修改该文件的字段声明，重新生成会覆盖字段声明
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
@Aggregate(aggregate = "event", name = "Event", root = true, type = Aggregate.TYPE_ENTITY, description = "事件")
@Entity
@Table(name = "`__event`")
@DynamicInsert
@DynamicUpdate
class Event {
    companion object {
        private val log = LoggerFactory.getLogger(Event::class.java)

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

    // 【行为方法开始】
    fun init(
        payload: Any,
        svcName: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    ) {
        this.eventUuid = UUID.randomUUID().toString()
        this.svcName = svcName
        this.createAt = scheduleAt
        this.expireAt = scheduleAt.plusSeconds(expireAfter.seconds)
        this.eventState = EventState.INIT
        this.tryTimes = retryTimes
        this.triedTimes = 1
        this.lastTryTime = scheduleAt

        loadPayload(payload)

        this.nextTryTime = calculateNextTryTime(scheduleAt)
    }

    @Transient
    @JSONField(serialize = false)
    var payload: Any? = null
        get() {
            if (field != null) {
                return field
            }
            if (dataType.isNotBlank()) {
                val dataClass: Class<*> = try {
                    Class.forName(dataType)
                } catch (e: ClassNotFoundException) {
                    log.error("事件类型解析错误", e)
                    throw DomainException("事件数据类型解析错误: $dataType", e)
                }
                field = JSON.parseObject(data, dataClass, Feature.SupportNonPublicField)
            } else throw DomainException("事件数据类型未指定")
            return field
        }
        private set

    private fun loadPayload(payload: Any) {
        this.payload = payload
        this.data = JSON.toJSONString(payload, IgnoreNonFieldGetter, SkipTransientField)
        this.dataType = payload.javaClass.name

        val integrationEvent = payload.javaClass.getAnnotation(IntegrationEvent::class.java)
        val domainEvent = payload.javaClass.getAnnotation(DomainEvent::class.java)

        this.eventType = when {
            integrationEvent != null -> integrationEvent.value
            domainEvent != null -> domainEvent.value
            else -> throw DomainException("事件类型未指定: ${payload.javaClass.name}")
        }

        val retry = payload.javaClass.getAnnotation(Retry::class.java)
        if (retry != null) {
            this.tryTimes = retry.retryTimes
            this.expireAt = this.createAt.plusMinutes(retry.expireAfter.toLong())
        }
    }

    val isValid: Boolean
        get() = this.eventState in setOf(EventState.INIT, EventState.DELIVERING, EventState.EXCEPTION)

    val isInvalid: Boolean
        get() = this.eventState in setOf(EventState.CANCEL, EventState.EXPIRED, EventState.EXHAUSTED)

    val isDelivering: Boolean
        get() = EventState.DELIVERING == this.eventState

    val isDelivered: Boolean
        get() = EventState.DELIVERED == this.eventState

    fun beginDelivery(now: LocalDateTime): Boolean {
        when {
            // 初始状态或者确认中或者异常
            !isValid -> return false
            // 超过重试次数
            this.triedTimes >= this.tryTimes -> {
                this.eventState = EventState.EXHAUSTED
                return false
            }
            // 事件过期
            now.isAfter(this.expireAt) -> {
                this.eventState = EventState.EXPIRED
                return false
            }
            // 未到下次重试时间
            this.lastTryTime != now && this.nextTryTime.isAfter(now) -> return false
        }

        this.eventState = EventState.DELIVERING
        this.lastTryTime = now
        this.triedTimes += 1
        this.nextTryTime = calculateNextTryTime(now)
        return true
    }

    fun endDelivery(now: LocalDateTime) {
        this.eventState = EventState.DELIVERED
    }

    fun cancelDelivery(now: LocalDateTime): Boolean {
        if (isDelivered || isInvalid) {
            return false
        }
        this.eventState = EventState.CANCEL
        return true
    }

    fun occurredException(now: LocalDateTime, ex: Throwable) {
        if (isDelivered) {
            return
        }
        this.eventState = EventState.EXCEPTION
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw, true))
        this.exception = sw.toString()
    }

    private fun calculateNextTryTime(now: LocalDateTime): LocalDateTime {
        val retry = payload!!.javaClass.getAnnotation(Retry::class.java)
        if (retry == null || retry.retryIntervals.isEmpty()) {
            return when {
                this.triedTimes <= 10 -> now.plusMinutes(1)
                this.triedTimes <= 20 -> now.plusMinutes(5)
                else -> now.plusMinutes(10)
            }
        }
        val index = (this.triedTimes - 1).coerceIn(0, retry.retryIntervals.lastIndex)
        return now.plusMinutes(retry.retryIntervals[index].toLong())
    }

    override fun toString(): String {
        return JSON.toJSONString(this, IgnoreNonFieldGetter, SkipTransientField)
    }

    // 【行为方法结束】

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
            @JvmStatic
            fun valueOf(value: Int): EventState? {
                return entries.find { it.value == value }
            }
        }

        class Converter : AttributeConverter<EventState, Int> {
            override fun convertToDatabaseColumn(attribute: EventState): Int {
                return attribute.value
            }

            override fun convertToEntityAttribute(dbData: Int): EventState? {
                return valueOf(dbData)
            }
        }
    }

    // 【字段映射开始】本段落由[cap4j-ddd-codegen-maven-plugin]维护，请不要手工改动
    /**
     * bigint
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null

    /**
     * 事件uuid
     * varchar(64)  NOT NULL DEFAULT ''
     */
    @Column(name = "`event_uuid`")
    lateinit var eventUuid: String

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`")
    lateinit var svcName: String

    /**
     * 事件类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`event_type`")
    lateinit var eventType: String

    /**
     * 事件数据
     * text (nullable)
     */
    @Column(name = "`data`")
    lateinit var data: String

    /**
     * 事件数据类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`data_type`")
    lateinit var dataType: String

    /**
     * 异常信息
     * text (nullable)
     */
    @Column(name = "`exception`")
    var exception: String? = null

    /**
     * 过期时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`")
    lateinit var expireAt: LocalDateTime

    /**
     * 创建时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`")
    lateinit var createAt: LocalDateTime

    /**
     * 分发状态@E=0:INIT:init|-1:DELIVERING:delivering|-2:CANCEL:cancel|-3:EXPIRED:expired|-4:EXHAUSTED:exhausted|-9:EXCEPTION:exception|1:DELIVERED:delivered;@T=EventState;
     * int          NOT NULL DEFAULT '0'
     */
    @Column(name = "`event_state`")
    @Convert(converter = EventState.Converter::class)
    lateinit var eventState: EventState

    /**
     * 上次尝试时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`")
    lateinit var lastTryTime: LocalDateTime

    /**
     * 下次尝试时间
     * datetime     NOT NULL DEFAULT '0001-01-01 00:00:00'
     */
    @Column(name = "`next_try_time`")
    lateinit var nextTryTime: LocalDateTime

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
