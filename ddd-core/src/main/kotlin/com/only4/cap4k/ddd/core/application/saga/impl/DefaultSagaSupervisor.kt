package com.only4.cap4k.ddd.core.application.saga.impl

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestInterceptor
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.NoneResultCommandParam
import com.only4.cap4k.ddd.core.application.query.ListQuery
import com.only4.cap4k.ddd.core.application.query.PageQuery
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.saga.*
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.createScheduledThreadPool
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 默认Saga监督者实现
 *
 * 实现Saga模式的编排和管理，支持：
 * - 同步和异步Saga执行
 * - 自动重试和错误处理
 * - 子流程管理
 * - 延迟调度执行
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
open class DefaultSagaSupervisor(
    private val requestHandlers: List<RequestHandler<*, *>>,
    private val requestInterceptors: List<RequestInterceptor<*, *>>,
    private val validator: Validator?,
    private val sagaRecordRepository: SagaRecordRepository,
    private val svcName: String,
    private val threadPoolSize: Int = 10,
    private val threadFactoryClassName: String = ""
) : SagaSupervisor, SagaProcessSupervisor, SagaManager {

    companion object {
        /**
         * 默认Saga过期时间（分钟）
         * 一天 60*24 = 1440
         */
        private const val DEFAULT_SAGA_EXPIRE_MINUTES = 1440

        /**
         * 默认Saga重试次数
         */
        private const val DEFAULT_SAGA_RETRY_TIMES = 200

        /**
         * 本地调度时间阈值（分钟）
         * 小于此阈值的调度时间将立即执行
         */
        private const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLD_MINUTES = 2

        /**
         * Saga记录线程本地存储
         */
        private val SAGA_RECORD_THREAD_LOCAL = ThreadLocal<SagaRecord>()
    }

    private val requestHandlerMap by lazy {
        buildMap {
            requestHandlers.forEach { handler ->
                val requestPayloadClass = resolveGenericTypeClass(
                    handler, 0,
                    RequestHandler::class.java,
                    Command::class.java, NoneResultCommandParam::class.java,
                    Query::class.java, ListQuery::class.java, PageQuery::class.java,
                    SagaHandler::class.java
                )
                put(requestPayloadClass, handler)
            }
        }.toMap()
    }

    private val requestInterceptorMap by lazy {
        buildMap {
            requestInterceptors.forEach { interceptor ->
                val requestPayloadClass = resolveGenericTypeClass(
                    interceptor, 0,
                    RequestInterceptor::class.java
                )
                computeIfAbsent(requestPayloadClass) { mutableListOf() }
                    .add(interceptor)
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

    override fun <REQUEST : SagaParam<out RESPONSE>, RESPONSE: Any> send(request: REQUEST): RESPONSE {
        // 参数验证
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        val sagaRecord = createSagaRecord(
            sagaType = request::class.java.name,
            request = request
        )

        return internalSend(request, sagaRecord)
    }

    override fun <REQUEST : SagaParam<out RESPONSE>, RESPONSE: Any> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String {
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        val sagaRecord = createSagaRecord(
            sagaType = request::class.java.name,
            request = request,
            scheduleAt = schedule
        )

        if (sagaRecord.isExecuting) {
            scheduleExecution(request, sagaRecord)
        }

        return sagaRecord.id
    }

    override fun <R : Any> result(id: String): R? = sagaRecordRepository.getById(id).getResult()


    override fun resume(saga: SagaRecord, minNextTryTime: LocalDateTime) {
        val now = LocalDateTime.now()
        val sagaTime = Duration.between(saga.nextTryTime, now).let {
            if (it.isNegative) now else saga.nextTryTime
        }

        saga.beginSaga(sagaTime)

        var maxTry = 65535
        while (!(Duration.between(saga.nextTryTime, minNextTryTime).isZero) && saga.isValid) {
            saga.beginSaga(saga.nextTryTime)
            if (maxTry-- <= 0) {
                throw DomainException("疑似死循环")
            }
        }
        sagaRecordRepository.save(saga)

        val param = saga.param

        validator?.validate(saga)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        if (saga.isExecuting) {
            scheduleExecution(param, saga)
        }
    }

    override fun retry(uuid: String) {
        val saga = sagaRecordRepository.getById(uuid)

        val param = saga.param

        validator?.validate(saga)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        internalSend(param, saga)
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord> =
        sagaRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int =
        sagaRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)

    override fun <REQUEST : RequestParam<out RESPONSE>, RESPONSE: Any> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE? {
        val sagaRecord = SAGA_RECORD_THREAD_LOCAL.get()
            ?: throw IllegalStateException("No SagaRecord found in thread local")

        // 如果子流程已经执行过，直接返回结果
        if (sagaRecord.isSagaProcessExecuted(processCode)) {
            return sagaRecord.getSagaProcessResult(processCode)
        }

        val now = LocalDateTime.now()

        // 开始子流程执行
        sagaRecord.beginSagaProcess(now, processCode, request)
        sagaRecordRepository.save(sagaRecord)

        return try {
            val response = RequestSupervisor.instance.send(request)

            // 子流程执行成功
            sagaRecord.endSagaProcess(now, processCode, response)
            sagaRecordRepository.save(sagaRecord)
            response
        } catch (throwable: Throwable) {
            // 子流程执行异常
            sagaRecord.sagaProcessOccurredException(now, processCode, throwable)
            sagaRecordRepository.save(sagaRecord)
            throw throwable
        }
    }

    /**
     * 创建Saga记录
     */
    protected open fun createSagaRecord(
        sagaType: String,
        request: SagaParam<out Any>,
        scheduleAt: LocalDateTime = LocalDateTime.now()
    ): SagaRecord {
        val sagaRecord = sagaRecordRepository.create()

        sagaRecord.init(
            sagaParam = request,
            svcName = svcName,
            sagaType = sagaType,
            scheduleAt = scheduleAt,
            expireAfter = Duration.ofMinutes(DEFAULT_SAGA_EXPIRE_MINUTES.toLong()),
            retryTimes = DEFAULT_SAGA_RETRY_TIMES
        )

        // 如果调度时间已过或在阈值时间内，立即开始执行
        val now = LocalDateTime.now()
        val shouldExecuteImmediately = Duration.between(now, scheduleAt).let {
            it.isNegative || it.toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLD_MINUTES
        }

        if (shouldExecuteImmediately) {
            sagaRecord.beginSaga(scheduleAt)
        }

        sagaRecordRepository.save(sagaRecord)
        return sagaRecord
    }

    /**
     * 内部执行Saga逻辑
     */
    protected open fun <REQUEST : SagaParam<out RESPONSE>, RESPONSE : Any> internalSend(
        request: REQUEST,
        sagaRecord: SagaRecord
    ): RESPONSE {
        return try {
            SAGA_RECORD_THREAD_LOCAL.set(sagaRecord)
            val response = internalSend(request)
            sagaRecord.endSaga(LocalDateTime.now(), response)
            sagaRecordRepository.save(sagaRecord)
            response
        } catch (throwable: Throwable) {
            // Saga执行异常
            sagaRecord.occurredException(LocalDateTime.now(), throwable)
            sagaRecordRepository.save(sagaRecord)
            throw throwable
        } finally {
            SAGA_RECORD_THREAD_LOCAL.remove()
        }
    }

    protected open fun <REQUEST : SagaParam<out RESPONSE>, RESPONSE : Any> internalSend(request: REQUEST): RESPONSE {
        val requestClass = request::class.java
        val interceptors = getInterceptorsForRequest(requestClass)
        val handler = getHandlerForRequest(requestClass)

        // 执行前置拦截器
        interceptors.forEach { interceptor ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as RequestInterceptor<REQUEST, RESPONSE>).preRequest(request)
        }

        // 执行主要业务逻辑
        @Suppress("UNCHECKED_CAST")
        val response = (handler as RequestHandler<REQUEST, RESPONSE>).exec(request)

        // 执行后置拦截器
        interceptors.forEach { interceptor ->
            @Suppress("UNCHECKED_CAST")
            (interceptor as RequestInterceptor<REQUEST, RESPONSE>).postRequest(request, response)
        }
        return response
    }

    /**
     * 调度Saga执行
     */
    private fun scheduleExecution(request: SagaParam<out Any>, sagaRecord: SagaRecord) {
        val now = LocalDateTime.now()
        val delay = if (now.isBefore(sagaRecord.scheduleTime)) {
            Duration.between(now, sagaRecord.scheduleTime)
        } else {
            Duration.ZERO
        }

        executorService.schedule({
            @Suppress("UNCHECKED_CAST")
            internalSend(request as SagaParam<Any>, sagaRecord)
        }, delay.toMillis(), TimeUnit.MILLISECONDS)
    }

    /**
     * 获取请求对应的拦截器列表
     */
    private fun getInterceptorsForRequest(requestClass: Class<*>): List<RequestInterceptor<*, *>> {
        return requestInterceptorMap[requestClass] ?: emptyList()
    }

    /**
     * 获取请求对应的处理器
     */
    private fun getHandlerForRequest(requestClass: Class<*>): RequestHandler<*, *> {
        return requestHandlerMap[requestClass]
            ?: throw IllegalStateException("No handler found for request class: ${requestClass.name}")
    }
}
