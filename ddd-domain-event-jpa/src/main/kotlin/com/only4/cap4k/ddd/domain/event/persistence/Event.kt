package com.only4.cap4k.ddd.domain.event.persistence

import com.fasterxml.jackson.annotation.JsonIgnore
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPayloadValidator
import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.domain.event.impl.DomainEventPayloadValidator
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.ReliableFailureFacts
import com.only4.cap4k.ddd.core.share.annotation.Retry
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

@Entity
@Table(name = "`__event`")
@DynamicInsert
@DynamicUpdate
class Event(
    /**
     * bigint
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    var id: Long? = null,

    /**
     * 事件uuid
     * varchar(64)  NOT NULL DEFAULT ''
     */
    @Column(name = "`event_uuid`")
    var eventUuid: String = "",

    /**
     * 服务
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`svc_name`")
    var svcName: String = "",

    /**
     * 事件类型
     * varchar(255) NOT NULL DEFAULT ''
     */
    @Column(name = "`event_type`")
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
    @Column(name = "`data_type`")
    var dataType: String = "",

    /** Versioned ExecutionContext envelope captured at event attachment/registration time. */
    @Column(name = "`execution_context`")
    var executionContext: String? = null,

    /** Safe structured facts for the latest failed delivery attempt. */
    @Column(name = "`failure_facts`", columnDefinition = "text")
    var failureFactsJson: String? = null,

    /**
     * 过期时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`expire_at`", columnDefinition = "datetime(3)")
    var expireAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 创建时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`create_at`", columnDefinition = "datetime(3)")
    var createAt: LocalDateTime = LocalDateTime.now(),

    /** Immutable time at which the reliable event was first registered for publication. */
    @Column(name = "`published_at`", nullable = false, columnDefinition = "datetime(3)")
    var publishedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),

    /**
     * 分发状态@E=0:INIT:init|-1:DELIVERING:delivering|-2:CANCEL:cancel|-3:EXPIRED:expired|-4:EXHAUSTED:exhausted|-9:EXCEPTION:exception|1:DELIVERED:delivered;@T=EventState;
     * int          NOT NULL DEFAULT '0'
     */
    @Column(name = "`event_state`")
    @Convert(converter = EventState.Converter::class)
    var eventState: EventState = EventState.INIT,

    /**
     * 上次尝试时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`last_try_time`", columnDefinition = "datetime(3)")
    var lastTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 下次尝试时间
     * datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
     */
    @Column(name = "`next_try_time`", columnDefinition = "datetime(3)")
    var nextTryTime: LocalDateTime = LocalDateTime.now(),

    /**
     * 已尝试次数
     * int(11)      NOT NULL DEFAULT '0'
     */
    @Column(name = "`tried_times`")
    var triedTimes: Int = 0,

    /**
     * 尝试次数
     * int(11)      NOT NULL DEFAULT '0'
     */
    @Column(name = "`try_times`")
    var tryTimes: Int = 0,

    /** Immutable retry-policy snapshot captured when the reliable event is registered. */
    @Column(name = "`retry_policy`", nullable = false)
    var retryPolicy: String = "",

    /**
     * 数据版本（支持乐观锁）
     * int          NOT NULL DEFAULT '0'
     */
    @Version
    @Column(name = "`version`")
    var version: Int = 0,

    /** Private runtime ownership token assigned by the atomic JPA substrate. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "`delivery_token`", length = 32, columnDefinition = "varbinary(32)")
    var deliveryToken: ByteArray? = null,

    /** Private runtime lease boundary for the current delivery token. */
    @Column(name = "`lease_until`", columnDefinition = "datetime(3)")
    var leaseUntil: LocalDateTime? = null,

    /** Durable idempotency marker for the last accepted operator redrive request. */
    @Column(name = "`redrive_request_token`", length = 128)
    var redriveRequestToken: String? = null,

    /** Runtime-owned timestamp at which the record entered a terminal state. */
    @Column(name = "`terminalized_at`", columnDefinition = "datetime(3)")
    var terminalizedAt: LocalDateTime? = null,

    /** Database audit columns retained for schema/projection parity. */
    @Column(name = "`db_created_at`", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    var dbCreatedAt: LocalDateTime? = null,

    @Column(name = "`db_updated_at`", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    var dbUpdatedAt: LocalDateTime? = null,
) {
    companion object {
        private val log = LoggerFactory.getLogger(Event::class.java)

        const val F_EVENT_UUID = "eventUuid"
        const val F_SVC_NAME = "svcName"
        const val F_EVENT_TYPE = "eventType"
        const val F_DATA = "data"
        const val F_DATA_TYPE = "dataType"
        const val F_EXECUTION_CONTEXT = "executionContext"
        const val F_FAILURE_FACTS_JSON = "failureFactsJson"
        const val F_CREATE_AT = "createAt"
        const val F_PUBLISHED_AT = "publishedAt"
        const val F_EXPIRE_AT = "expireAt"
        const val F_EVENT_STATE = "eventState"
        const val F_TRY_TIMES = "tryTimes"
        const val F_RETRY_POLICY = "retryPolicy"
        const val F_TRIED_TIMES = "triedTimes"
        const val F_LAST_TRY_TIME = "lastTryTime"
        const val F_NEXT_TRY_TIME = "nextTryTime"
        const val F_DELIVERY_TOKEN = "deliveryToken"
        const val F_LEASE_UNTIL = "leaseUntil"
        const val F_REDRIVE_REQUEST_TOKEN = "redriveRequestToken"
        const val F_TERMINALIZED_AT = "terminalizedAt"
    }

    fun init(
        payload: Any,
        svcName: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int,
    ): Event = apply {
        this.eventUuid = UUID.randomUUID().toString()
        this.svcName = svcName
        this.publishedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS)
        this.createAt = scheduleAt
        this.expireAt = scheduleAt.plusSeconds(expireAfter.seconds)
        this.eventState = EventState.INIT
        this.tryTimes = retryTimes
        this.triedTimes = 0
        this.lastTryTime = scheduleAt

        loadPayload(payload)

        this.nextTryTime = scheduleAt
    }

    @Transient
    @get:JsonIgnore
    @field:JsonIgnore
    var payload: Any? = null
        get() {
            if (field != null) {
                return field
            }
            if (dataType.isNotBlank()) {
                val dataClass: Class<*> = try {
                    Class.forName(dataType)
                } catch (e: ClassNotFoundException) {
                    log.error("事件类型解析错误 failureType={}", e.javaClass.name)
                    throw DomainException("事件数据类型解析错误")
                }
                field = RuntimeJson.read(data, dataClass)
            } else throw DomainException("事件数据类型未指定")
            return field
        }
        private set

    @Transient
    @get:JsonIgnore
    @field:JsonIgnore
    var failureFacts: ReliableFailureFacts? = null
        get() {
            if (field == null && !failureFactsJson.isNullOrBlank()) {
                field = RuntimeJson.read(failureFactsJson!!, ReliableFailureFacts::class.java)
            }
            return field
        }
        private set

    private fun loadPayload(payload: Any) {
        val integrationEvent = payload.javaClass.getAnnotation(IntegrationEvent::class.java)
        val domainEvent = payload.javaClass.getAnnotation(DomainEvent::class.java)
        val integrationEventName = integrationEvent?.let { IntegrationEventPayloadValidator.eventName(payload) }

        if (integrationEvent == null && domainEvent == null) {
            throw DomainException("事件类型未指定: ${payload.javaClass.name}")
        }
        DomainEventPayloadValidator.validate(payload)

        this.payload = payload
        this.data = RuntimeJson.write(payload)
        this.dataType = payload.javaClass.name

        this.eventType = when {
            integrationEventName != null -> integrationEventName
            domainEvent != null -> domainEvent.value
            else -> error("unreachable")
        }

        val retry = payload.javaClass.getAnnotation(Retry::class.java)
        val policySnapshot = ReliableRetryPolicySnapshot.capture(retry, this.tryTimes)
        this.retryPolicy = RuntimeJson.write(policySnapshot)
        this.tryTimes = policySnapshot.retryLimit
        if (retry != null) {
            this.expireAt = this.createAt.plusMinutes(retry.expireAfter.toLong())
        }
    }

    @PrePersist
    @PreUpdate
    fun validatePersistenceInvariants() {
        if (eventType.isBlank()) {
            throw DomainException("Reliable Event eventType must not be blank")
        }
    }

    override fun toString(): String =
        "EventRecord(eventUuid=$eventUuid, service=$svcName, type=$eventType, " +
            "state=${eventState.stateName}, attempt=$triedTimes/$tryTimes, " +
            "lastTryTime=$lastTryTime, nextTryTime=$nextTryTime, failure=$failureFacts)"

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
}
