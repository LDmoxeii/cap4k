package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.application.*
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.NoneResultCommandParam
import com.only4.cap4k.ddd.core.application.query.ListQuery
import com.only4.cap4k.ddd.core.application.query.PageQuery
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisor
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.createScheduledThreadPool
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 默认请求管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
open class DefaultRequestSupervisor(
    private val requestHandlers: List<RequestHandler<*, *>>,
    private val requestInterceptors: List<RequestInterceptor<*, *>>,
    private val validator: Validator?,
    private val requestRecordRepository: RequestRecordRepository,
    private val svcName: String,
    private val threadPoolSize: Int,
    private val threadFactoryClassName: String
) : RequestSupervisor, RequestManager {

    companion object {
        /**
         * 默认Request过期时间（分钟）
         * 一天 60*24 = 1440
         */
        private const val DEFAULT_REQUEST_EXPIRE_MINUTES = 1440

        /**
         * 默认Request重试次数
         */
        private const val DEFAULT_REQUEST_RETRY_TIMES = 200

        /**
         * 本地调度时间阈值
         */
        private const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES = 2
    }

    private val requestHandlerMap by lazy {
        buildMap<Class<*>, RequestHandler<*, *>> {
            requestHandlers.forEach { requestHandler ->
                val requestPayloadClass = resolveGenericTypeClass(
                    requestHandler, 0,
                    RequestHandler::class.java,
                    Command::class.java, NoneResultCommandParam::class.java,
                    Query::class.java, ListQuery::class.java, PageQuery::class.java
                )
                put(requestPayloadClass, requestHandler)
            }
        }.toMap()
    }

    private val requestInterceptorMap by lazy {
        buildMap<Class<*>, MutableList<RequestInterceptor<*, *>>> {
            // 初始化请求拦截器映射
            requestInterceptors.forEach { requestInterceptor ->
                val requestPayloadClass = resolveGenericTypeClass(
                    requestInterceptor, 0,
                    RequestInterceptor::class.java
                )
                computeIfAbsent(requestPayloadClass) { mutableListOf() }
                    .add(requestInterceptor)
            }
        }.toMap()
    }

    private val executorService by lazy {
        createScheduledThreadPool(
            threadPoolSize,
            threadFactoryClassName,
            this::class.java.classLoader
        )
    }

    fun init() {
        requestHandlerMap
        requestInterceptorMap
        executorService
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE {
        if (request is SagaParam<*>) {
            @Suppress("UNCHECKED_CAST")
            return SagaSupervisor.instance.send(request as SagaParam<RESPONSE>)
        }

        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        return internalSend(request)
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String {
        if (request is SagaParam<*>) {
            return SagaSupervisor.instance.schedule(request as SagaParam<*>, schedule)
        }

        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        val requestRecord = createRequestRecord(
            requestType = request::class.java.name,
            request = request,
            scheduleAt = schedule
        )

        if (requestRecord.isExecuting) {
            scheduleExecution(request, requestRecord)
        }

        return requestRecord.id
    }

    override fun <R : Any> result(requestId: String): R? = requestRecordRepository.getById(requestId).getResult()

    override fun resume(request: RequestRecord, minNextTryTime: LocalDateTime) {
        val now = LocalDateTime.now()
        val requestTime = Duration.between(request.nextTryTime, now).let {
            if (it.isNegative) now else request.nextTryTime
        }

        request.beginRequest(requestTime)

        var maxTry = 65535
        while (!(Duration.between(request.nextTryTime, minNextTryTime).isZero) && request.isValid) {
            request.beginRequest(request.nextTryTime)
            if (maxTry-- <= 0) {
                throw DomainException("疑似死循环")
            }
        }

        requestRecordRepository.save(request)

        val param = request.param

        // 参数验证
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        if (request.isExecuting) {
            scheduleExecution(param, request)
        }
    }

    override fun retry(uuid: String) {
        val request = requestRecordRepository.getById(uuid)

        val param = request.param

        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        internalSend(param, request)
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord> =
        requestRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int =
        requestRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)

    protected open fun createRequestRecord(
        requestType: String,
        request: RequestParam<*>,
        scheduleAt: LocalDateTime
    ): RequestRecord {
        val requestRecord = requestRecordRepository.create()

        requestRecord.init(
            requestParam = request,
            svcName = svcName,
            requestType = requestType,
            scheduleAt = scheduleAt,
            expireAfter = Duration.ofMinutes(DEFAULT_REQUEST_EXPIRE_MINUTES.toLong()),
            retryTimes = DEFAULT_REQUEST_RETRY_TIMES
        )

        val now = LocalDateTime.now()
        val shouldExecuteImmediately = Duration.between(now, scheduleAt).let {
            it.isNegative || it.toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES
        }

        if (shouldExecuteImmediately) {
            requestRecord.beginRequest(scheduleAt)
        }

        requestRecordRepository.save(requestRecord)
        return requestRecord
    }

    private fun scheduleExecution(
        request: RequestParam<*>,
        requestRecord: RequestRecord
    ) {
        val duration = Duration.between(LocalDateTime.now(), requestRecord.scheduleTime).let {
            if (it.isNegative) Duration.ZERO else it
        }

        executorService.schedule({
            internalSend(request, requestRecord)
        }, duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    protected open fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> internalSend(
        request: REQUEST,
        requestRecord: RequestRecord
    ): RESPONSE {
        return try {
            val response = internalSend(request)
            requestRecord.endRequest(LocalDateTime.now(), response)
            requestRecordRepository.save(requestRecord)
            response
        } catch (throwable: Throwable) {
            requestRecord.occurredException(LocalDateTime.now(), throwable)
            requestRecordRepository.save(requestRecord)
            throw throwable
        }
    }

    protected open fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> internalSend(request: REQUEST): RESPONSE {
        val requestClass = request::class.java
        val interceptors = requestInterceptorMap[requestClass] ?: emptyList()

        // 前置拦截器处理
        interceptors.forEach { interceptor ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as RequestInterceptor<REQUEST, RESPONSE>).preRequest(request)
        }

        // 执行请求处理
        @Suppress("UNCHECKED_CAST")
        val handler = requestHandlerMap[requestClass] as? RequestHandler<REQUEST, RESPONSE>
            ?: throw IllegalStateException("No handler found for request type: ${requestClass.name}")
        val response = handler.exec(request)

        // 后置拦截器处理
        interceptors.forEach { interceptor ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as RequestInterceptor<REQUEST, RESPONSE>).postRequest(request, response)
        }

        return response
    }
}
