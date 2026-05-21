package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.saga.SagaCompensationRequestedBy
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaRecord
import com.only4.cap4k.ddd.core.share.DomainException
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
        if (saga.sagaState in setOf(
                Saga.SagaState.COMPENSATION_REQUESTED,
                Saga.SagaState.COMPENSATING,
                Saga.SagaState.MANUAL_REPAIR_REQUIRED,
                Saga.SagaState.COMPENSATED
            )
        ) {
            throw DomainException(
                buildString {
                    append("Saga compensation recorded: ")
                    append("code=").append(saga.compensationRequestCode)
                    append(", reason=").append(saga.compensationRequestReason)
                }
            )
        }
        @Suppress("UNCHECKED_CAST")
        val result = saga.sagaResult as? R
        if (result == null && !saga.exception.isNullOrEmpty()) {
            throw DomainException(saga.exception!!)
        }
        return result
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

    override fun registerSagaProcessCompensation(processCode: String, compensationCode: String, param: RequestParam<*>) {
        saga.getSagaProcess(processCode)!!.registerCompensation(compensationCode, param)
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
        val result = sagaProcess.sagaProcessResult as? R
        if (result == null && !sagaProcess.exception.isNullOrEmpty()) {
            throw DomainException(sagaProcess.exception!!)
        }
        return result
    }

    override fun requestCompensation(
        now: LocalDateTime,
        code: String,
        reason: String,
        requestedBy: SagaCompensationRequestedBy,
        sourceProcessCode: String?
    ) {
        saga.requestCompensation(now, code, reason, requestedBy, sourceProcessCode)
    }

    override fun beginCompensation(now: LocalDateTime): Boolean {
        return saga.beginCompensation(now)
    }

    override fun endCompensation(now: LocalDateTime) {
        saga.endCompensation(now)
    }

    override fun markManualRepairRequired(now: LocalDateTime) {
        saga.markManualRepairRequired(now)
    }

    override fun compensationProcessCodesToRun(): List<String> {
        val processes = saga.sagaProcesses
            .filter { it.processState == SagaProcess.SagaProcessState.EXECUTED }
            .sortedWith(
                compareByDescending<SagaProcess> { it.executedAt ?: LocalDateTime.MIN }
                    .thenByDescending { it.createAt }
                    .thenByDescending { it.id ?: Long.MIN_VALUE }
            )
            .filterNot { it.compensationState == SagaProcess.SagaCompensationState.COMPENSATED }
        return processes.map { it.processCode }
    }

    override fun getSagaProcessCompensationRequest(processCode: String): RequestParam<*>? {
        return saga.getSagaProcess(processCode)?.compensationRequestParam
    }

    override fun beginSagaCompensationProcess(now: LocalDateTime, processCode: String) {
        saga.getSagaProcess(processCode)!!.beginCompensation(now)
    }

    override fun endSagaCompensationProcess(now: LocalDateTime, processCode: String, result: Any) {
        saga.getSagaProcess(processCode)!!.endCompensation(now, result)
    }

    override fun sagaCompensationProcessOccurredException(now: LocalDateTime, processCode: String, throwable: Throwable) {
        saga.getSagaProcess(processCode)!!.occurredCompensationException(now, throwable)
    }
}
