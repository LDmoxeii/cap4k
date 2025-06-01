package com.only4.cap4k.ddd.core.application.saga

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestInterceptor
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.NoneResultCommandParam
import com.only4.cap4k.ddd.core.application.query.ListQuery
import com.only4.cap4k.ddd.core.application.query.PageQuery
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.share.misc.ClassUtils
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Saga事务流程控制器接口
 * 负责管理和控制Saga事务流程的执行
 * 提供同步执行、异步执行和延迟执行等核心功能
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaSupervisor {
    /**
     * 同步执行Saga事务流程
     * 立即执行并等待结果返回
     *
     * @param request 请求参数
     * @return 执行结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行Saga事务流程
     * 立即调度执行，不等待结果
     *
     * @param request 请求参数
     * @return Saga事务ID
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> async(request: REQUEST): String =
        schedule(request, LocalDateTime.now())

    /**
     * 延迟执行Saga事务流程
     * 在指定时间执行事务流程
     *
     * @param request 请求参数
     * @param schedule 计划执行时间
     * @param delay 额外延迟时间，默认为0
     * @return Saga事务ID
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime,
        delay: Duration = Duration.ZERO
    ): String

    /**
     * 获取Saga事务执行结果
     *
     * @param id Saga事务ID
     * @return 执行结果，如果不存在则返回空
     */
    fun <R : Any> result(id: String): Optional<R>

    /**
     * 获取Saga事务执行结果
     * 支持指定请求类型的结果获取
     *
     * @param requestId Saga事务ID
     * @param requestClass 请求参数类型
     * @return 执行结果，如果不存在则返回空
     */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE> = result(requestId)

    companion object {
        /**
         * 获取Saga事务流程控制器实例
         *
         * @return Saga事务流程控制器实例
         */
        val instance: SagaSupervisor
            get() = SagaSupervisorSupport.instance
    }
}

/**
 * Saga事务流程控制器默认实现
 * 提供完整的Saga事务流程管理功能
 *
 * @author binking338
 * @date 2024/10/12
 */
open class DefaultSagaSupervisor(
    private val requestHandlers: List<RequestHandler<*, *>>,
    private val requestInterceptors: List<RequestInterceptor<*, *>>,
    private val threadFactoryClassName: String,
    private val threadPoolSize: Int,
    private val validator: Validator?,
    private val sagaRecordRepository: SagaRecordRepository,
    private val svcName: String,
) : SagaSupervisor, SagaProcessSupervisor, SagaManager {

    /**
     * 请求处理器映射
     * 使用lazy委托属性实现线程安全的延迟初始化
     */
    private val requestHandlerMap by lazy {
        mutableMapOf<Class<*>, RequestHandler<*, *>>().apply {
            requestHandlers.forEach { handler ->
                val requestPayloadClass = ClassUtils.resolveGenericTypeClass(
                    handler,
                    0,
                    RequestHandler::class.java,
                    Command::class.java, NoneResultCommandParam::class.java,
                    Query::class.java, ListQuery::class.java, PageQuery::class.java,
                    SagaHandler::class.java
                )
                put(requestPayloadClass, handler)
            }
        }
    }

    /**
     * 请求拦截器映射
     * 使用lazy委托属性实现线程安全的延迟初始化
     */
    private val requestInterceptorMap by lazy {
        mutableMapOf<Class<*>, MutableList<RequestInterceptor<*, *>>>().apply {
            requestInterceptors.forEach { interceptor ->
                val requestPayloadClass = ClassUtils.resolveGenericTypeClass(
                    interceptor, 0,
                    RequestInterceptor::class.java
                )
                val interceptors = getOrPut(requestPayloadClass) { mutableListOf() }
                interceptors.add(interceptor)
            }
        }
    }

    /**
     * 线程池服务
     * 使用lazy委托属性实现线程安全的延迟初始化
     */
    private val executorService by lazy {
        if (threadFactoryClassName.isBlank()) {
            Executors.newScheduledThreadPool(threadPoolSize)
        } else {
            val threadFactoryClass = org.springframework.objenesis.instantiator.util.ClassUtils.getExistingClass<Any>(
                javaClass.classLoader, threadFactoryClassName
            )
            (org.springframework.objenesis.instantiator.util.ClassUtils.newInstance(threadFactoryClass) as ThreadFactory?)?.let {
                Executors.newScheduledThreadPool(threadPoolSize, it)
            } ?: Executors.newScheduledThreadPool(threadPoolSize)
        }
    }

    override fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> send(request: REQUEST): RESPONSE {
        // 验证请求参数
        validator?.let {
            val constraintViolations = it.validate(request)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }
        // 创建并执行Saga记录
        val sagaRecord = createSagaRecord(request.javaClass.name, request, LocalDateTime.now())
        return internalSend(request, sagaRecord)
    }

    override fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime,
        delay: Duration
    ): String {
        // 验证请求参数
        validator?.let {
            val constraintViolations = it.validate(request)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }

        // 创建Saga记录
        val sagaRecord = createSagaRecord(request.javaClass.name, request, schedule)
        if (sagaRecord.isExecuting) {
            val now = LocalDateTime.now()
            val duration = if (now.isBefore(sagaRecord.scheduleTime)) {
                Duration.between(LocalDateTime.now(), sagaRecord.scheduleTime)
            } else {
                Duration.ZERO
            }
            // 调度执行
            executorService.schedule(
                { internalSend(request, sagaRecord) },
                duration.toMillis(),
                TimeUnit.MILLISECONDS
            )
        }

        return sagaRecord.id
    }

    override fun <R : Any> result(id: String): Optional<R> =
        sagaRecordRepository.getById(id).getResult()

    override fun resume(saga: SagaRecord) {
        if (!saga.beginSaga(LocalDateTime.now())) {
            sagaRecordRepository.save(saga)
            return
        }

        val param = saga.param
        // 验证请求参数
        validator?.let {
            val constraintViolations = it.validate(param)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }

        if (saga.isExecuting) {
            val now = LocalDateTime.now()
            val duration = if (now.isBefore(saga.scheduleTime)) {
                Duration.between(LocalDateTime.now(), saga.scheduleTime)
            } else {
                Duration.ZERO
            }
            // 调度执行
            executorService.schedule(
                { internalSend(param, saga) },
                duration.toMillis(),
                TimeUnit.MILLISECONDS
            )
        }
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord> =
        sagaRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int =
        sagaRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE {
        val sagaRecord = SAGA_RECORD_THREAD_LOCAL.get()
            ?: throw IllegalStateException("No SagaRecord found in thread local")

        if (sagaRecord.isSagaProcessExecuted(processCode)) {
            return sagaRecord.getSagaProcessResult(processCode)
        }

        sagaRecord.beginSagaProcess(LocalDateTime.now(), processCode, request)
        sagaRecordRepository.save(sagaRecord)

        return try {
            val response = RequestSupervisor.instance.send(request)
            sagaRecord.endSagaProcess(LocalDateTime.now(), processCode, response)
            sagaRecordRepository.save(sagaRecord)
            response
        } catch (throwable: Throwable) {
            sagaRecord.sagaProcessOccurredException(LocalDateTime.now(), processCode, throwable)
            sagaRecordRepository.save(sagaRecord)
            throw throwable
        }
    }

    /**
     * 创建Saga记录
     * 初始化Saga记录的基本信息
     *
     * @param sagaType Saga类型
     * @param request 请求参数
     * @param scheduleAt 计划执行时间
     * @return 创建的Saga记录
     */
    protected fun createSagaRecord(
        sagaType: String,
        request: SagaParam<*>,
        scheduleAt: LocalDateTime
    ): SagaRecord {
        val sagaRecord = sagaRecordRepository.create()
        sagaRecord.init(
            request,
            svcName,
            sagaType,
            scheduleAt,
            Duration.ofMinutes(DEFAULT_SAGA_EXPIRE_MINUTES),
            DEFAULT_SAGA_RETRY_TIMES
        )

        if (scheduleAt.isBefore(LocalDateTime.now()) ||
            Duration.between(LocalDateTime.now(), scheduleAt)
                .toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES
        ) {
            sagaRecord.beginSaga(scheduleAt)
        }

        sagaRecordRepository.save(sagaRecord)
        return sagaRecord
    }

    /**
     * 内部执行Saga事务流程
     * 处理请求拦截、执行和结果保存
     *
     * @param request 请求参数
     * @param sagaRecord Saga记录
     * @return 执行结果
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> internalSend(
        request: REQUEST,
        sagaRecord: SagaRecord
    ): RESPONSE {
        try {
            SAGA_RECORD_THREAD_LOCAL.set(sagaRecord)
            // 执行前置拦截器
            requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
                .forEach { interceptor ->
                    (interceptor as RequestInterceptor<RESPONSE, REQUEST>).preRequest(request)
                }

            // 执行请求处理
            val response = (requestHandlerMap[request.javaClass] as RequestHandler<RESPONSE, REQUEST>).exec(request)

            // 执行后置拦截器
            requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
                .forEach { interceptor ->
                    (interceptor as RequestInterceptor<RESPONSE, REQUEST>).postRequest(request, response)
                }

            // 保存执行结果
            sagaRecord.endSaga(LocalDateTime.now(), response)
            sagaRecordRepository.save(sagaRecord)
            return response
        } catch (throwable: Throwable) {
            // 处理执行异常
            sagaRecord.occurredException(LocalDateTime.now(), throwable)
            sagaRecordRepository.save(sagaRecord)
            throw throwable
        } finally {
            SAGA_RECORD_THREAD_LOCAL.remove()
        }
    }

    companion object {
        private val SAGA_RECORD_THREAD_LOCAL = ThreadLocal<SagaRecord>()

        /**
         * 默认Saga过期时间（分钟）
         * 一天 60*24 = 1440
         */
        const val DEFAULT_SAGA_EXPIRE_MINUTES: Long = 1440L

        /**
         * 默认Saga重试次数
         */
        const val DEFAULT_SAGA_RETRY_TIMES: Int = 200

        /**
         * 本地调度时间阈值（分钟）
         */
        const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES: Int = 2
    }
}
