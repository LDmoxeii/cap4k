package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.application.ReliableRequestSupervisor
import com.only4.cap4k.ddd.core.application.RequestManager
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestRecord
import com.only4.cap4k.ddd.core.application.RequestRecordRepository
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisor
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.createScheduledThreadPool
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Optional persisted-request provider.
 */
open class DefaultReliableRequestSupervisor(
    private val requestSupervisor: RequestSupervisor,
    private val validator: Validator?,
    private val requestRecordRepository: RequestRecordRepository,
    private val svcName: String,
    private val threadPoolSize: Int,
    private val threadFactoryClassName: String,
) : ReliableRequestSupervisor, RequestManager {

    companion object {
        private const val DEFAULT_REQUEST_EXPIRE_MINUTES = 1440
        private const val DEFAULT_REQUEST_RETRY_TIMES = 200
        private const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES = 2
    }

    private val executorService by lazy {
        createScheduledThreadPool(threadPoolSize, threadFactoryClassName, javaClass.classLoader)
    }

    fun init() {
        executorService
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> schedule(
        request: REQUEST,
        schedule: LocalDateTime,
    ): String {
        if (request is SagaParam<*>) {
            return SagaSupervisor.instance.schedule(request, schedule)
        }

        validate(request)
        val requestRecord = createRequestRecord(request::class.java.name, request, schedule)
        if (requestRecord.isExecuting) {
            scheduleExecution(request, requestRecord)
        }
        return requestRecord.id
    }

    override fun <R : Any> result(requestId: String): R? = requestRecordRepository.getById(requestId).getResult()

    override fun resume(request: RequestRecord, minNextTryTime: LocalDateTime) {
        val now = LocalDateTime.now()
        val requestTime = if (Duration.between(request.nextTryTime, now).isNegative) now else request.nextTryTime
        request.beginRequest(requestTime)

        var maxTry = 65535
        while (request.nextTryTime.isBefore(minNextTryTime) && request.isValid) {
            request.beginRequest(request.nextTryTime)
            if (maxTry-- <= 0) throw DomainException("疑似死循环")
        }

        requestRecordRepository.save(request)
        val param = request.param
        validate(param)
        if (request.isExecuting) scheduleExecution(param, request)
    }

    override fun retry(uuid: String) {
        val requestRecord = requestRecordRepository.getById(uuid)
        val param = requestRecord.param
        validate(param)
        internalSend(param, requestRecord)
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord> =
        requestRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int =
        requestRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)

    protected open fun createRequestRecord(
        requestType: String,
        request: RequestParam<*>,
        scheduleAt: LocalDateTime,
    ): RequestRecord {
        val requestRecord = requestRecordRepository.create()
        requestRecord.init(
            requestParam = request,
            svcName = svcName,
            requestType = requestType,
            scheduleAt = scheduleAt,
            expireAfter = Duration.ofMinutes(DEFAULT_REQUEST_EXPIRE_MINUTES.toLong()),
            retryTimes = DEFAULT_REQUEST_RETRY_TIMES,
        )

        val duration = Duration.between(LocalDateTime.now(), scheduleAt)
        if (duration.isNegative || duration.toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES) {
            requestRecord.beginRequest(scheduleAt)
        }
        requestRecordRepository.save(requestRecord)
        return requestRecord
    }

    private fun scheduleExecution(request: RequestParam<*>, requestRecord: RequestRecord) {
        val duration = Duration.between(LocalDateTime.now(), requestRecord.scheduleTime)
            .let { if (it.isNegative) Duration.ZERO else it }
        executorService.schedule({ internalSend(request, requestRecord) }, duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    protected open fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> internalSend(
        request: REQUEST,
        requestRecord: RequestRecord,
    ): RESPONSE = try {
        val response = requestSupervisor.send(request)
        requestRecord.endRequest(LocalDateTime.now(), response)
        requestRecordRepository.save(requestRecord)
        response
    } catch (throwable: Throwable) {
        requestRecord.occurredException(LocalDateTime.now(), throwable)
        requestRecordRepository.save(requestRecord)
        throw throwable
    }

    private fun validate(request: Any) {
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }
    }
}
