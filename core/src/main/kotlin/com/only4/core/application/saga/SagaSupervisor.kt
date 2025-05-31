package com.only4.core.application.saga

import com.only4.core.application.RequestHandler
import com.only4.core.application.RequestInterceptor
import com.only4.core.application.RequestParam
import com.only4.core.application.RequestSupervisor
import com.only4.core.application.command.Command
import com.only4.core.application.command.NoneResultCommandParam
import com.only4.core.application.query.ListQuery
import com.only4.core.application.query.PageQuery
import com.only4.core.application.query.Query
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

/**
 * Saga控制器
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaSupervisor {
    /**
     * 执行Saga流程
     *
     * @param request   请求参数
     * @param <REQUEST> 请求参数类型
    </REQUEST> */
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行Saga流程
     *
     * @param request
     * @param <REQUEST>
     * @param <RESPONSE> 响应参数类型
     * @return Saga ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> async(request: REQUEST): String {
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
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> schedule(
        request: REQUEST, schedule: LocalDateTime, delay: Duration = Duration.ZERO
    ): String

    /**
     * 获取Saga结果
     *
     * @param id  Saga ID
     * @param <R>
     * @return 请求结果
    </R> */
    fun <R : Any> result(id: String): Optional<R>

    /**
     * 获取Saga结果
     *
     * @param requestId    请求ID
     * @param requestClass 请求参数类型
     * @param <REQUEST>    请求参数类型
     * @param <RESPONSE>   响应参数类型
     * @return 请求结果
    </RESPONSE></REQUEST> */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> result(
        requestId: String, requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE> {
        return this.result(requestId)
    }

    companion object {
        val instance: SagaSupervisor
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = SagaSupervisorSupport.instance
    }
}

/**
 * 默认SagaSupervisor实现
 *
 * @author binking338
 * @date 2024/10/12
 */
open class DefaultSagaSupervisor(
    requestHandlers: List<RequestHandler<*, *>>,
    requestInterceptors: List<RequestInterceptor<*, *>>,
    threadPoolSize: Int,
    threadFactoryClassName: String,
    private val validator: Validator?,
    private val sagaRecordRepository: SagaRecordRepository,
    private val svcName: String,
) : SagaSupervisor, SagaProcessSupervisor, SagaManager {

    private val requestHandlerMap: MutableMap<Class<*>, RequestHandler<*, *>> = mutableMapOf()
    private val requestInterceptorMap: MutableMap<Class<*>, MutableList<RequestInterceptor<*, *>>> = mutableMapOf()

    private val executorService: ScheduledExecutorService

    init {

        requestHandlers.forEach { handler ->
            val requestPayloadClass = ClassUtils.resolveGenericTypeClass(
                handler,
                0,
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
            (org.springframework.objenesis.instantiator.util.ClassUtils.newInstance(threadFactoryClass) as ThreadFactory?)?.let {
                Executors.newScheduledThreadPool(threadPoolSize, it)
            } ?: Executors.newScheduledThreadPool(threadPoolSize)
        }
    }

    override fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> send(request: REQUEST): RESPONSE {
        validator?.let {
            val constraintViolations = it.validate(request)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }
        val sagaRecord = createSagaRecord(request.javaClass.name, request, LocalDateTime.now())
        return internalSend(request, sagaRecord)
    }

    override fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime,
        delay: Duration
    ): String {
        validator?.let {
            val constraintViolations = it.validate(request)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }

        val sagaRecord = createSagaRecord(request.javaClass.name, request, schedule)
        if (sagaRecord.isExecuting) {
            val now = LocalDateTime.now()
            val duration = if (now.isBefore(sagaRecord.scheduleTime)) Duration.between(
                LocalDateTime.now(),
                sagaRecord.scheduleTime
            )
            else Duration.ZERO
            executorService.schedule(
                {
                    internalSend(
                        request,
                        sagaRecord
                    )
                },
                duration.toMillis(), TimeUnit.MILLISECONDS
            )
        }

        return sagaRecord.id
    }

    override fun <R : Any> result(id: String): Optional<R> {
        return sagaRecordRepository.getById(id).getResult()
    }

    override fun resume(saga: SagaRecord) {
        if (!saga.beginSaga(LocalDateTime.now())) sagaRecordRepository.save(saga).apply { return }
        val param = saga.param

        validator?.let {
            val constraintViolations = it.validate(param)
            if (constraintViolations.isNotEmpty()) {
                throw ConstraintViolationException(constraintViolations)
            }
        }
        if (saga.isExecuting) {
            val now = LocalDateTime.now()
            val duration = if (now.isBefore(saga.scheduleTime)) Duration.between(
                LocalDateTime.now(),
                saga.scheduleTime
            )
            else Duration.ZERO
            executorService.schedule(
                {
                    internalSend(
                        param,
                        saga
                    )
                },
                duration.toMillis(), TimeUnit.MILLISECONDS
            )
        }
    }

    override fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord> {
        return sagaRecordRepository.getByNextTryTime(svcName, maxNextTryTime, limit)
    }

    override fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int {
        return sagaRecordRepository.archiveByExpireAt(svcName, maxExpireAt, limit)
    }

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE {
        val sagaRecord = SAGA_RECORD_THREAD_LOCAL.get()
        requireNotNull(sagaRecord) { "No SagaRecord found in thread local" }
        if (sagaRecord.isSagaProcessExecuted(processCode))
            return sagaRecord.getSagaProcessResult(processCode)

        sagaRecord.beginSagaProcess(LocalDateTime.now(), processCode, request)
        sagaRecordRepository.save(sagaRecord)
        try {
            val response = RequestSupervisor.instance.send(request)

            sagaRecord.endSagaProcess(LocalDateTime.now(), processCode, response)
            sagaRecordRepository.save(sagaRecord)
            return response
        } catch (throwable: Throwable) {
            sagaRecord.sagaProcessOccurredException(LocalDateTime.now(), processCode, throwable)
            sagaRecordRepository.save(sagaRecord)
            throw throwable
        }
    }

    /**
     * 创建SagaRecord
     *
     * @param sagaType
     * @param request
     * @return
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
        if (scheduleAt.isBefore(LocalDateTime.now()) || Duration.between(LocalDateTime.now(), scheduleAt)
                .toMinutes() < LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES
        ) {
            sagaRecord.beginSaga(scheduleAt)
        }
        sagaRecordRepository.save(sagaRecord)
        return sagaRecord
    }

    /**
     * 执行Saga
     *
     * @param request
     * @param sagaRecord
     * @param <REQUEST>
     * @param <RESPONSE>
     * @return
     */
    protected fun <RESPONSE : Any, REQUEST : SagaParam<RESPONSE>> internalSend(
        request: REQUEST,
        sagaRecord: SagaRecord
    ): RESPONSE {
        try {
            SAGA_RECORD_THREAD_LOCAL.set(sagaRecord)
            requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
                .forEach { interceptor ->
                    (interceptor as RequestInterceptor<RESPONSE, REQUEST>).preRequest(request)
                }
            val response = (requestHandlerMap[request.javaClass] as RequestHandler<RESPONSE, REQUEST>).exec(request)
            requestInterceptorMap.getOrDefault(request.javaClass, emptyList())
                .forEach { interceptor ->
                    (interceptor as RequestInterceptor<RESPONSE, REQUEST>).postRequest(request, response)
                }

            sagaRecord.endSaga(LocalDateTime.now(), response)
            sagaRecordRepository.save(sagaRecord)
            return response
        } catch (throwable: Throwable) {
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
         * 本地调度时间阈值
         */
        const val LOCAL_SCHEDULE_ON_INIT_TIME_THRESHOLDS_MINUTES: Int = 2
    }
}
