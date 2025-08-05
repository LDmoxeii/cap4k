package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaRecord
import java.time.Duration
import java.time.LocalDateTime

/**
 * Saga记录实现
 *
 * @author binking338
 * @date 2024/10/12
 */
class SagaRecordImpl : SagaRecord {
    lateinit var saga: Saga

    /**
     * 恢复Saga
     */
    fun resume(saga: Saga) {
        this.saga = saga
    }

    override fun toString(): String = saga.toString()

    override fun init(
        sagaParam: SagaParam<*>,
        svcName: String,
        sagaType: String,
        scheduleAt: LocalDateTime,
        expireAfter: Duration,
        retryTimes: Int
    ) {
        saga = Saga()
        saga.init(sagaParam, svcName, sagaType, scheduleAt, expireAfter, retryTimes)
    }

    override val id: String
        get() = saga.sagaUuid

    override val type: String
        get() = saga.sagaType

    override val param: SagaParam<*>
        get() = saga.sagaParam!!

    override fun <R : Any> getResult(): R? {
        @Suppress("UNCHECKED_CAST")
        return saga.sagaResult as? R
    }

    override val scheduleTime: LocalDateTime
        get() = saga.lastTryTime

    override val nextTryTime: LocalDateTime
        get() = saga.nextTryTime

    override val isValid: Boolean
        get() = saga.isValid

    override val isInvalid: Boolean
        get() = saga.isInvalid

    override val isExecuting: Boolean
        get() = saga.isExecuting

    override val isExecuted: Boolean
        get() = saga.isExecuted

    override fun beginSaga(now: LocalDateTime): Boolean {
        return saga.beginSaga(now)
    }

    override fun cancelSaga(now: LocalDateTime): Boolean {
        return saga.cancelSaga(now)
    }

    override fun endSaga(now: LocalDateTime, result: Any) {
        saga.endSaga(now, result)
    }

    override fun occurredException(now: LocalDateTime, throwable: Throwable) {
        saga.occurredException(now, throwable)
    }

    override fun beginSagaProcess(now: LocalDateTime, processCode: String, param: RequestParam<*>) {
        saga.beginSagaProcess(now, processCode, param)
    }

    override fun endSagaProcess(now: LocalDateTime, processCode: String, result: Any) {
        saga.endSagaProcess(now, processCode, result)
    }

    override fun sagaProcessOccurredException(now: LocalDateTime, processCode: String, throwable: Throwable) {
        saga.getSagaProcess(processCode)!!.occurredException(now, throwable)
    }

    override fun isSagaProcessExecuted(processCode: String): Boolean {
        val sagaProcess = saga.getSagaProcess(processCode) ?: return false
        return sagaProcess.processState == SagaProcess.SagaProcessState.EXECUTED
    }

    override fun <R : Any> getSagaProcessResult(processCode: String): R? {
        val sagaProcess = saga.getSagaProcess(processCode) ?: return null
        @Suppress("UNCHECKED_CAST")
        return sagaProcess.sagaProcessResult as? R
    }
}
