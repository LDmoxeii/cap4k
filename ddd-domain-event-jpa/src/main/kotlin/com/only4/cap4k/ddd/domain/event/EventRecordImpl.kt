package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_EVENT_ID
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_EVENT_TYPE
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_PERSIST
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_SCHEDULE
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_TIMESTAMP
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION
import com.only4.cap4k.ddd.domain.event.persistence.Event
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * 事件记录实现
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
class EventRecordImpl : EventRecord {
    private val logger = LoggerFactory.getLogger(EventRecordImpl::class.java)
    lateinit var event: Event
    private var persist = false
    private var _message: Message<Any>? = null

    /**
     * 恢复事件
     */
    fun resume(event: Event) {
        this.event = event
    }

    override fun toString(): String = event.toString()


    override fun init(
        payload: Any,
        svcName: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    ) {
        event = Event()
        event.init(payload, svcName, scheduleAt, expireAfter, retryTimes)
    }

    override val id: String
        get() = event.eventUuid

    override val type: String
        get() = event.eventType

    override val payload: Any
        get() = event.payload!!

    override val scheduleTime: LocalDateTime
        get() = event.createAt!!

    override val nextTryTime: LocalDateTime
        get() = event.nextTryTime!!

    override fun markPersist(persist: Boolean) {
        this.persist = persist
    }

    override val isPersist: Boolean
        get() = persist

    override val message: Message<Any>
        get() {
            if (this._message != null) {
                return this._message!!
            }

            synchronized(this) {
                if (this._message != null) {
                    return this._message!!
                }

                val isIntegrationEvent = this.payload.javaClass.getAnnotation(IntegrationEvent::class.java) != null
                this._message = GenericMessage(
                    this.payload,
                    EventMessageInterceptor.ModifiableMessageHeaders(
                        null,
                        UUID.fromString(this.id),
                        null
                    )
                ).apply {
                    headers.apply {
                        put(HEADER_KEY_CAP4J_EVENT_ID, event.eventUuid)
                        put(
                            HEADER_KEY_CAP4J_EVENT_TYPE, if (isIntegrationEvent)
                                HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION
                            else
                                HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN
                        )
                        put(HEADER_KEY_CAP4J_PERSIST, this@EventRecordImpl.persist)

                        val now = LocalDateTime.now()
                        put(HEADER_KEY_CAP4J_TIMESTAMP, now.toEpochSecond(ZoneOffset.UTC))

                        if (this@EventRecordImpl.scheduleTime.isAfter(now)) {
                            put(
                                HEADER_KEY_CAP4J_SCHEDULE,
                                this@EventRecordImpl.scheduleTime.toEpochSecond(ZoneOffset.UTC)
                            )
                        }
                    }
                }
                return this._message!!
            }
        }

    override val isValid: Boolean
        get() = event.isValid()

    override val isInvalid: Boolean
        get() = event.isInvalid()

    override val isDelivered: Boolean
        get() = event.isDelivered()

    override fun beginDelivery(now: LocalDateTime): Boolean {
        return event.holdState4Delivery(now)
    }

    override fun cancelDelivery(now: LocalDateTime): Boolean {
        return event.cancelDelivery(now)
    }

    override fun occurredException(now: LocalDateTime, throwable: Throwable) {
        event.occurredException(now, throwable)
    }

    override fun confirmedDelivery(now: LocalDateTime) {
        event.confirmedDelivery(now)
    }
}
