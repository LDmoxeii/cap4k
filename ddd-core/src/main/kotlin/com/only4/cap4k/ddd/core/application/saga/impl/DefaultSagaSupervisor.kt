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
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import org.springframework.objenesis.instantiator.util.ClassUtils as SpringClassUtils

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
class DefaultSagaSupervisor(
    private val requestHandlers: List<RequestHandler<*, *>>,
    private val requestInterceptors: List<RequestInterceptor<*, *>>,
    private val validator: Validator?,
    private val sagaRecordRepository: SagaRecordRepository,
    private val svcName: String,
    private val threadPoolSize: Int = 10,
    private val threadFactoryClassName: String? = null
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

    // 使用lazy懒加载的成员变量
    private val requestHandlerMap: Map<Class<*>, RequestHandler<*, *>> by lazy {
        initRequestHandlers()
    }

    private val requestInterceptorMap: Map<Class<*>, List<RequestInterceptor<*, *>>> by lazy {
        initRequestInterceptors()
    }

    private val executorService: ScheduledExecutorService by lazy {
        initExecutorService()
    }

    /**
     * 初始化请求处理器映射
     */
    private fun initRequestHandlers(): Map<Class<*>, RequestHandler<*, *>> {
        val handlerMap = mutableMapOf<Class<*>, RequestHandler<*, *>>()

        // 构建处理器映射
        requestHandlers.forEach { handler ->
            val requestPayloadClass = resolveGenericTypeClass(
                handler, 0,
                RequestHandler::class.java,
                Command::class.java, NoneResultCommandParam::class.java,
                Query::class.java, ListQuery::class.java, PageQuery::class.java,
                SagaHandler::class.java
            )
            handlerMap[requestPayloadClass] = handler
        }

        return handlerMap
    }

    /**
     * 初始化请求拦截器映射
     */
    private fun initRequestInterceptors(): Map<Class<*>, List<RequestInterceptor<*, *>>> {
        val interceptorMap = mutableMapOf<Class<*>, MutableList<RequestInterceptor<*, *>>>()

        // 构建拦截器映射
        requestInterceptors.forEach { interceptor ->
            val requestPayloadClass = resolveGenericTypeClass(
                interceptor, 0,
                RequestInterceptor::class.java
            )
            interceptorMap.computeIfAbsent(requestPayloadClass) { mutableListOf() }
                .add(interceptor)
        }

        return interceptorMap.mapValues { it.value.toList() }
    }

    /**
     * 初始化调度线程池
     */
    private fun initExecutorService(): ScheduledExecutorService {
        return when {
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
                    // 如果自定义ThreadFactory创建失败，使用默认的
                    Executors.newScheduledThreadPool(threadPoolSize)
                }
            }
        }
    }

    override fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> send(request: REQUEST): RESPONSE {
        // 参数验证
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        val sagaRecord = createSagaRecord(
            sagaType = request::class.java.name,
            request = request,
            scheduleAt = LocalDateTime.now()
        )

        return internalSend(request, sagaRecord)
    }

    override fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String {
        // 参数验证
        validator?.validate(request)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        val sagaRecord = createSagaRecord(
            sagaType = request::class.java.name,
            request = request,
            scheduleAt = schedule
        )

        // 如果Saga正在执行且需要延迟调度
        if (sagaRecord.isExecuting) {
            scheduleExecution(request, sagaRecord)
        }

        return sagaRecord.id
    }

    override fun <R> result(id: String): R? {
        val sagaRecord = sagaRecordRepository.getById(id)
        @Suppress("UNCHECKED_CAST")
        return sagaRecord?.getResult() as? R
    }

    override fun resume(saga: SagaRecord) {
        if (!saga.beginSaga(LocalDateTime.now())) {
            sagaRecordRepository.save(saga)
            return
        }

        val param = saga.param

        // 参数验证
        validator?.validate(param)?.takeIf { it.isNotEmpty() }?.let { violations ->
            throw ConstraintViolationException(violations)
        }

        // 如果Saga正在执行且需要延迟调度
        if (saga.isExecuting) {
            scheduleExecution(param, saga)
        }
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord> {
        return sagaRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)
    }

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int {
        return sagaRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE {
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
    protected fun createSagaRecord(
        sagaType: String,
        request: SagaParam<*>,
        scheduleAt: LocalDateTime
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
        val shouldExecuteImmediately = scheduleAt.isBefore(now) ||
                Duration.between(now, scheduleAt).toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLD_MINUTES

        if (shouldExecuteImmediately) {
            sagaRecord.beginSaga(scheduleAt)
        }

        sagaRecordRepository.save(sagaRecord)
        return sagaRecord
    }

    /**
     * 调度Saga执行
     */
    private fun scheduleExecution(request: SagaParam<*>, sagaRecord: SagaRecord) {
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
     * 内部执行Saga逻辑
     */
    protected fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> internalSend(
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

    protected fun <REQUEST : SagaParam<RESPONSE>, RESPONSE> internalSend(request: REQUEST): RESPONSE {
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
