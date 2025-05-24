package com.only4.core.application

import com.only4.core.application.command.Command
import com.only4.core.application.command.NoneResultCommandParam
import com.only4.core.application.query.ListQuery
import com.only4.core.application.query.PageQuery
import com.only4.core.application.query.Query
import com.only4.core.application.saga.SagaHandler
import com.only4.core.application.saga.SagaParam
import com.only4.core.application.saga.SagaSupervisor
import com.only4.core.share.misc.ClassUtils
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrDefault

/**
 * 请求管理器
 *
 * @author binking338
 * @date 2024/8/24
 */
interface RequestSupervisor {
    /**
     * 执行请求
     *
     * @param request    请求参数
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
    </RESPONSE></REQUEST> */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行请求
     *
     * @param request    请求参数
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> async(request: REQUEST): String {
        return schedule(request, LocalDateTime.now())
    }

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param schedule   计划时间
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param delay      延迟时间
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> delay(
        request: REQUEST,
        delay: Duration
    ): String {
        return schedule(request, LocalDateTime.now().plus(delay))
    }

    /**
     * 获取请求结果
     *
     * @param requestId    请求ID
     * @param requestClass 请求参数类型
     * @param <REQUEST>    请求参数类型
     * @param <RESPONSE>   响应参数类型
     * @return 请求结果
    </RESPONSE></REQUEST> */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE>

    companion object {
        val instance: RequestSupervisor
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = RequestSupervisorSupport.instance
    }
}

/**
 * 默认请求管理器
 *
 * @author binking338
 * @date 2024/8/24
 */
open class DefaultRequestSupervisor(
    requestHandlers: List<RequestHandler<Any, RequestParam<Any>>>,
    requestInterceptors: List<RequestInterceptor<Any, RequestParam<Any>>>,
    threadPoolSize: Int,
    threadFactoryClassName: String,
    private val validator: Validator?,
    private val requestRecordRepository: RequestRecordRepository,
    private val svcName: String,
) : RequestSupervisor, RequestManager {

    private var requestHandlerMap: MutableMap<Class<*>, RequestHandler<Any, RequestParam<Any>>> = mutableMapOf()
    private var requestInterceptorMap: MutableMap<Class<*>, MutableList<RequestInterceptor<Any, RequestParam<Any>>>> =
        mutableMapOf()

    private var executorService: ScheduledExecutorService

    init {
        requestHandlers.forEach { handler ->
            val requestPayloadClass = ClassUtils.resolveGenericTypeClass(
                handler, 0,
                RequestHandler::class.java,
                Command::class.java, NoneResultCommandParam::class.java,
                Query::class.java, ListQuery::class.java, PageQuery::class.java,
                SagaHandler::class.java
            )
            requestHandlerMap[requestPayloadClass] = handler
        }

        requestInterceptors.forEach { interceptor ->
            val requestPayloadClass = ClassUtils.resolveGenericTypeClass(
                interceptor, 0,
                RequestInterceptor::class.java
            )
            val interceptors = requestInterceptorMap.getOrPut(requestPayloadClass) { mutableListOf() }
            interceptors.add(interceptor)
        }
        this.executorService = if (threadFactoryClassName.isBlank()) {
            Executors.newScheduledThreadPool(threadPoolSize)
        } else {
            val threadFactoryClass = org.springframework.objenesis.instantiator.util.ClassUtils.getExistingClass<Any>(
                javaClass.classLoader, threadFactoryClassName
            )
            val threadFactory =
                org.springframework.objenesis.instantiator.util.ClassUtils.newInstance(threadFactoryClass) as ThreadFactory
            Executors.newScheduledThreadPool(threadPoolSize, threadFactory)
        }
    }

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> send(request: REQUEST): RESPONSE {
        if (request as RequestParam<Any> is SagaParam<Any>) return SagaSupervisor.instance.send(request as SagaParam<Any>) as RESPONSE
        validator?.let {
            val constraintViolations = it.validate(request)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }
        return internalSend(request as REQUEST)
    }

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String {
        if (request as RequestParam<Any> is SagaParam<Any>) return SagaSupervisor.instance.schedule(
            request as SagaParam<Any>,
            schedule
        )
        validator?.let {
            val constraintViolations = it.validate(request)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }
        val requestRecord = createRequestRecord(request.javaClass.name, request, schedule)
        if (requestRecord.isExecuting) {
            val now = LocalDateTime.now()
            val duration = if (now.isBefore(requestRecord.scheduleTime)) Duration.between(
                LocalDateTime.now(),
                requestRecord.scheduleTime
            )
            else Duration.ZERO
            executorService.schedule(
                {
                    internalSend(
                        request,
                        requestRecord
                    )
                },
                duration.toMillis(), TimeUnit.MILLISECONDS
            )
        }

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
        if (!request.beginRequest(LocalDateTime.now())) requestRecordRepository.save(request).apply { return }
        val param = request.param

        validator?.let {
            val constraintViolations = it.validate(param)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }
        if (request.isExecuting) {
            val now = LocalDateTime.now()
            val duration = if (now.isBefore(request.scheduleTime)) Duration.between(
                LocalDateTime.now(),
                request.scheduleTime
            )
            else Duration.ZERO
            executorService.schedule(
                {
                    internalSend(
                        param,
                        request
                    )
                },
                duration.toMillis(), TimeUnit.MILLISECONDS
            )
        }
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord> {
        return requestRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)
    }

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int {
        return requestRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)
    }

    protected fun createRequestRecord(
        requestType: String,
        request: RequestParam<Any>,
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
        if (scheduleAt.isBefore(LocalDateTime.now()) || Duration.between(LocalDateTime.now(), scheduleAt)
                .toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES
        ) {
            requestRecord.beginRequest(scheduleAt)
        }
        requestRecordRepository.save(requestRecord)
        return requestRecord
    }

    protected fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> internalSend(
        request: REQUEST,
        requestRecord: RequestRecord
    ): RESPONSE {
        try {
            val response: RESPONSE = internalSend(request)
            requestRecord.endRequest(LocalDateTime.now(), response)
            requestRecordRepository.save(requestRecord)
            return response
        } catch (throwable: Throwable) {
            requestRecord.occurredException(LocalDateTime.now(), throwable)
            requestRecordRepository.save(requestRecord)
            throw throwable
        }
    }

    protected fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> internalSend(request: REQUEST): RESPONSE {
        requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
            .forEach { interceptor ->
                interceptor.preRequest(request as RequestParam<Any>)
            }
        val response: RESPONSE =
            (requestHandlerMap[request.javaClass] as RequestHandler<RESPONSE, REQUEST>).exec(
                request
            )
        requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
            .forEach { interceptor ->
                interceptor.postRequest(request as RequestParam<Any>, response)
            }
        return response
    }


    companion object {
        /**
         * 默认Request过期时间（分钟）
         * 一天 60*24 = 1440
         */
        const val DEFAULT_REQUEST_EXPIRE_MINUTES: Long = 1440L

        /**
         * 默认Request重试次数
         */
        const val DEFAULT_REQUEST_RETRY_TIMES: Int = 200

        /**
         * 本地调度时间阈值
         */
        const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES: Int = 2
    }
}
