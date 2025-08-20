package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.application.persistence.Request
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestRecord
import com.only4.cap4k.ddd.core.share.DomainException
import java.time.Duration
import java.time.LocalDateTime

/**
 * 请求记录实现
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
class RequestRecordImpl : RequestRecord {
    lateinit var request: Request

    /**
     * 恢复请求
     */
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
        request = Request()
        request.init(requestParam, svcName, requestType, scheduleAt, expireAfter, retryTimes)
    }

    override val id: String
        get() = request.requestUuid

    override val type: String
        get() = request.requestType

    override val param: RequestParam<*>
        get() = request.requestParam!!

    override fun <R : Any> getResult(): R? {
        @Suppress("UNCHECKED_CAST")
        val result = request.requestResult as? R
        if (result == null && !request.exception.isNullOrEmpty()) {
            throw DomainException(request.exception!!)
        }
        return result
    }

    override val scheduleTime: LocalDateTime
        get() = request.lastTryTime

    override val nextTryTime: LocalDateTime
        get() = request.nextTryTime

    override val isValid: Boolean
        get() = request.isValid

    override val isInvalid: Boolean
        get() = request.isInvalid

    override val isExecuting: Boolean
        get() = request.isExecuting

    override val isExecuted: Boolean
        get() = request.isExecuted

    override fun beginRequest(now: LocalDateTime): Boolean = request.beginRequest(now)

    override fun cancelRequest(now: LocalDateTime): Boolean = request.cancelRequest(now)

    override fun endRequest(now: LocalDateTime, result: Any) {
        request.endRequest(now, result)
    }

    override fun occurredException(now: LocalDateTime, throwable: Throwable) {
        request.occurredException(now, throwable)
    }
}
