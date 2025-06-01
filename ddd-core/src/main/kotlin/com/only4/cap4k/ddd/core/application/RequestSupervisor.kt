package com.only4.cap4k.ddd.core.application

import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.NoneResultCommandParam
import com.only4.cap4k.ddd.core.application.query.ListQuery
import com.only4.cap4k.ddd.core.application.query.PageQuery
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.saga.SagaHandler
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisor
import com.only4.cap4k.ddd.core.share.misc.ClassUtils
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrDefault

/**
 * 请求监督者接口
 * 负责管理和执行请求，支持同步、异步和延迟执行
 * 提供请求结果查询和状态管理功能
 *
 * @author binking338
 * @date 2024/8/24
 */
interface RequestSupervisor {
    /**
     * 同步执行请求
     * 立即执行请求并返回结果
     *
     * @param request 请求参数
     * @return 请求执行结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     * @throws IllegalStateException 当请求执行失败时
     */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行请求
     * 立即将请求加入执行队列
     *
     * @param request 请求参数
     * @return 请求ID，用于后续查询结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> async(request: REQUEST): String =
        schedule(request, LocalDateTime.now())

    /**
     * 定时执行请求
     * 在指定时间执行请求
     *
     * @param request 请求参数
     * @param schedule 计划执行时间
     * @return 请求ID，用于后续查询结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String

    /**
     * 延迟执行请求
     * 在指定延迟时间后执行请求
     *
     * @param request 请求参数
     * @param delay 延迟时间
     * @return 请求ID，用于后续查询结果
     * @throws ConstraintViolationException 当请求参数验证失败时
     */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> delay(
        request: REQUEST,
        delay: Duration
    ): String = schedule(request, LocalDateTime.now().plus(delay))

    /**
     * 获取请求执行结果
     *
     * @param requestId 请求ID
     * @param requestClass 请求参数类型
     * @return 请求执行结果，如果请求未完成则返回空
     */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE>

    companion object {
        /**
         * 获取请求监督者实例
         * 通过RequestSupervisorSupport获取全局唯一的请求监督者实例
         *
         * @return 请求监督者实例
         */
        val instance: RequestSupervisor
            get() = RequestSupervisorSupport.instance
    }
}

/**
 * 默认请求监督者实现
 * 提供请求的同步、异步和延迟执行功能
 * 支持请求参数验证、拦截器处理和结果管理
 *
 * @author binking338
 * @date 2024/8/24
 */
@Suppress("UNCHECKED_CAST")
open class DefaultRequestSupervisor(
    private val requestHandlers: List<RequestHandler<*, *>>,
    private val requestInterceptors: List<RequestInterceptor<*, *>>,
    private val threadPoolSize: Int,
    private val threadFactoryClassName: String,
    private val validator: Validator?,
    private val requestRecordRepository: RequestRecordRepository,
    private val svcName: String,
) : RequestSupervisor, RequestManager {

    /**
     * 请求处理器映射
     * 使用lazy委托属性实现线程安全的延迟初始化
     */
    private val requestHandlerMap by lazy {
        mutableMapOf<Class<*>, RequestHandler<*, *>>().apply {
            requestHandlers.forEach { handler ->
                val requestPayloadClass = ClassUtils.resolveGenericTypeClass(
                    handler, 0,
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

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> send(request: REQUEST): RESPONSE {
        if (request is SagaParam<*>) return SagaSupervisor.instance.send(request as SagaParam<RESPONSE>)
        validateRequest(request)
        return internalSend(request)
    }

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String {
        if (request is SagaParam<*>) return SagaSupervisor.instance.schedule(
            request as SagaParam<*>,
            schedule
        )
        validateRequest(request)
        val requestRecord = createRequestRecord(request.javaClass.name, request, schedule)
        scheduleRequest(request, requestRecord)
        return requestRecord.id
    }

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST>
    ): Optional<RESPONSE> {
        val requestRecord = requestRecordRepository.getById(requestId)
            .getOrDefault(RequestSupervisor.instance.result(requestId, requestClass)) as RequestRecord
        return Optional.ofNullable(requestRecord.getResult())
    }

    override fun resume(request: RequestRecord) {
        if (!request.beginRequest(LocalDateTime.now())) {
            requestRecordRepository.save(request)
            return
        }
        validateRequest(request.param)
        scheduleRequest(request.param, request)
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord> =
        requestRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int =
        requestRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)

    private fun <REQUEST : RequestParam<*>> validateRequest(request: REQUEST) {
        validator?.let {
            val constraintViolations = it.validate(request)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }
    }

    private fun createRequestRecord(
        requestType: String,
        request: RequestParam<*>,
        scheduleAt: LocalDateTime
    ): RequestRecord {
        val requestRecord = requestRecordRepository.create()
        requestRecord.init(
            request,
            svcName,
            requestType,
            scheduleAt,
            Duration.ofMinutes(DEFAULT_REQUEST_EXPIRE_MINUTES),
            DEFAULT_REQUEST_RETRY_TIMES
        )
        if (shouldBeginRequestImmediately(scheduleAt)) {
            requestRecord.beginRequest(scheduleAt)
        }
        requestRecordRepository.save(requestRecord)
        return requestRecord
    }

    private fun shouldBeginRequestImmediately(scheduleAt: LocalDateTime): Boolean =
        scheduleAt.isBefore(LocalDateTime.now()) ||
                Duration.between(LocalDateTime.now(), scheduleAt)
                    .toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES

    private fun <REQUEST : RequestParam<*>> scheduleRequest(
        request: REQUEST,
        requestRecord: RequestRecord
    ) {
        if (!requestRecord.isExecuting) return

        val now = LocalDateTime.now()
        val duration = if (now.isBefore(requestRecord.scheduleTime)) {
            Duration.between(now, requestRecord.scheduleTime)
        } else {
            Duration.ZERO
        }

        executorService.schedule(
            { internalSend(request as RequestParam<*>, requestRecord) },
            duration.toMillis(),
            TimeUnit.MILLISECONDS
        )
    }

    private fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> internalSend(
        request: REQUEST,
        requestRecord: RequestRecord
    ): RESPONSE {
        try {
            val response = internalSend(request)
            requestRecord.endRequest(LocalDateTime.now(), response)
            requestRecordRepository.save(requestRecord)
            return response
        } catch (throwable: Throwable) {
            requestRecord.occurredException(LocalDateTime.now(), throwable)
            requestRecordRepository.save(requestRecord)
            throw throwable
        }
    }

    private fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> internalSend(request: REQUEST): RESPONSE {
        // 执行前置拦截器
        requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
            .forEach { interceptor ->
                (interceptor as RequestInterceptor<RESPONSE, REQUEST>).preRequest(request)
            }

        // 执行请求处理器
        val response = (requestHandlerMap[request.javaClass] as RequestHandler<RESPONSE, REQUEST>).exec(request)

        // 执行后置拦截器
        requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
            .forEach { interceptor ->
                (interceptor as RequestInterceptor<RESPONSE, REQUEST>).postRequest(request, response)
            }

        return response
    }

    companion object {
        /**
         * 默认请求过期时间（分钟）
         * 一天 60*24 = 1440
         */
        const val DEFAULT_REQUEST_EXPIRE_MINUTES: Long = 1440L

        /**
         * 默认请求重试次数
         */
        const val DEFAULT_REQUEST_RETRY_TIMES: Int = 200

        /**
         * 本地调度时间阈值（分钟）
         */
        const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES: Int = 2
    }
}
