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
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import org.springframework.objenesis.instantiator.util.ClassUtils as SpringClassUtils

/**
 * 默认请求管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
class DefaultRequestSupervisor(
    private val requestHandlers: List<RequestHandler<*, *>>,
    private val requestInterceptors: List<RequestInterceptor<*, *>>,
    private val validator: Validator?,
    private val requestRecordRepository: RequestRecordRepository,
    private val svcName: String,
    private val threadPoolSize: Int,
    private val threadFactoryClassName: String?
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
        val handlerMap = mutableMapOf<Class<*>, RequestHandler<*, *>>()

        // 初始化请求处理器映射
        requestHandlers.forEach { requestHandler ->
            val requestPayloadClass = resolveGenericTypeClass(
                requestHandler, 0,
                RequestHandler::class.java,
                Command::class.java, NoneResultCommandParam::class.java,
                Query::class.java, ListQuery::class.java, PageQuery::class.java
            )
            handlerMap[requestPayloadClass] = requestHandler
        }

        handlerMap.toMap()
    }

    private val requestInterceptorMap by lazy {
        val interceptorMap = mutableMapOf<Class<*>, MutableList<RequestInterceptor<*, *>>>()

        // 初始化请求拦截器映射
        requestInterceptors.forEach { requestInterceptor ->
            val requestPayloadClass = resolveGenericTypeClass(
                requestInterceptor, 0,
                RequestInterceptor::class.java
            )
            interceptorMap.computeIfAbsent(requestPayloadClass) { mutableListOf() }
                .add(requestInterceptor)
        }

        interceptorMap.toMap()
    }

    private val executorService by lazy {
        when {
            threadFactoryClassName.isNullOrEmpty() -> {
                Executors.newScheduledThreadPool(threadPoolSize)
            }

            else -> {
                try {
                    val threadFactoryClass = SpringClassUtils.getExistingClass<ThreadFactory>(
                        this::class.java.classLoader,
                        threadFactoryClassName
                    )
                    val threadFactory = SpringClassUtils.newInstance(threadFactoryClass)
                    if (threadFactory != null) {
                        Executors.newScheduledThreadPool(threadPoolSize, threadFactory)
                    } else {
                        Executors.newScheduledThreadPool(threadPoolSize)
                    }
                } catch (e: Exception) {
                    Executors.newScheduledThreadPool(threadPoolSize)
                }
            }
        }
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> send(request: REQUEST): RESPONSE {
        // 如果是Saga请求，委托给SagaSupervisor处理
        if (request is SagaParam<*>) {
            @Suppress("UNCHECKED_CAST")
            return SagaSupervisor.instance.send(request as SagaParam<RESPONSE>)
        }

        // 参数验证
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        return internalSend(request)
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String {
        // 如果是Saga请求，委托给SagaSupervisor处理
        if (request is SagaParam<*>) {
            @Suppress("UNCHECKED_CAST")
            return SagaSupervisor.instance.schedule(request as SagaParam<*>, schedule)
        }

        // 参数验证
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

    override fun <R> result(requestId: String): R? {
        val requestRecord = requestRecordRepository.getById(requestId)
        return if (requestRecord == null) {
            RequestSupervisor.instance.result(requestId)
        } else {
            @Suppress("UNCHECKED_CAST")
            requestRecord.getResult()
        }
    }

    override fun resume(request: RequestRecord, minNextTryTime: LocalDateTime) {
        val now = LocalDateTime.now()
        val requestTime = if (request.nextTryTime.isAfter(now)) {
            request.nextTryTime
        } else {
            now
        }

        request.beginRequest(requestTime)

        var maxTry = 65535
        while (request.nextTryTime.isBefore(minNextTryTime) && request.isValid) {
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

        // 参数验证
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        internalSend(param, request)
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord> {
        return requestRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)
    }

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int {
        return requestRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)
    }

    protected fun createRequestRecord(
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
        val shouldExecuteImmediately = scheduleAt.isBefore(now) ||
                Duration.between(now, scheduleAt)
                    .toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES

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
        val now = LocalDateTime.now()
        val duration = if (now.isBefore(requestRecord.scheduleTime)) {
            Duration.between(now, requestRecord.scheduleTime)
        } else {
            Duration.ZERO
        }

        executorService.schedule({
            internalSend(request, requestRecord)
        }, duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    protected fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> internalSend(
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

    protected fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> internalSend(request: REQUEST): RESPONSE {
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
