package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.application.persistence.Request
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestRecord
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

/**
 * 请求记录
 *
 * @author binking338
 * @date 2025/5/16
 */
class RequestRecordImpl : RequestRecord {
    private val logger = LoggerFactory.getLogger(RequestRecordImpl::class.java)

    internal lateinit var request: Request

    fun resume(request: Request) {
        this.request = request
    }

    override fun toString(): String = request.toString()

    override fun init(
        requestParam: RequestParam<*>,
        svcName: String,
        requestType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    ) {
        request = Request.builder().build()
        request.init(requestParam, svcName, requestType, scheduleAt, expireAfter, retryTimes)
    }

    override fun getId(): String = request.requestUuid

    override fun getType(): String = request.requestType

    override fun getParam(): RequestParam<*> = request.requestParam

    override fun <R> getResult(): R? {
        @Suppress("UNCHECKED_CAST")
        return request.requestResult as? R
    }

    override fun getScheduleTime(): LocalDateTime = request.lastTryTime

    override fun getNextTryTime(): LocalDateTime = request.nextTryTime

    override fun isValid(): Boolean = request.isValid

    override fun isInvalid(): Boolean = request.isInvalid

    override fun isExecuting(): Boolean = request.isExecuting

    override fun isExecuted(): Boolean = request.isExecuted

    override fun beginRequest(now: LocalDateTime): Boolean = request.beginRequest(now)

    override fun cancelRequest(now: LocalDateTime): Boolean = request.cancelRequest(now)

    override fun endRequest(now: LocalDateTime, result: Any?) {
        request.endRequest(now, result)
    }

    override fun occuredException(now: LocalDateTime, throwable: Throwable) {
        request.occurredException(now, throwable)
    }
}
