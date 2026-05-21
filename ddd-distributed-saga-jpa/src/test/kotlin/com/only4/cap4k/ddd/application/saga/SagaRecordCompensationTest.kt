package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.saga.SagaCompensationRequestedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class SagaRecordCompensationTest {

    private lateinit var sagaRecord: SagaRecordImpl
    private val now: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        sagaRecord = SagaRecordImpl()
        sagaRecord.init(TestSagaParam("publish", emptyMap()), "svc", "PUBLISH_SAGA", now, Duration.ofMinutes(10), 3)
        sagaRecord.beginSaga(now.plusMinutes(1))
    }

    @Test
    fun `register compensation stores final reverse request and marks process ready`() {
        sagaRecord.beginSagaProcess(now.plusMinutes(2), "create-plan", TestRequestParam("create", mapOf("contentId" to "content-9")))
        sagaRecord.endSagaProcess(now.plusMinutes(3), "create-plan", mapOf("planId" to "plan-9"))

        sagaRecord.registerSagaProcessCompensation(
            "create-plan",
            "cancel-plan",
            TestRequestParam("cancel", mapOf("planId" to "plan-9"))
        )

        val reloadedRecord = reloadRecord(sagaRecord)
        val process = reloadedRecord.saga.getSagaProcess("create-plan")!!
        val compensationRequest = reloadedRecord.getSagaProcessCompensationRequest("create-plan") as TestRequestParam

        assertNotSame(sagaRecord.saga, reloadedRecord.saga)
        assertEquals("cancel-plan", process.compensationCode)
        assertEquals(SagaProcess.SagaCompensationState.READY, process.compensationState)
        assertTrue(process.compensationParam.contains("plan-9"))
        assertEquals("cancel", compensationRequest.action)
        assertEquals(mapOf("planId" to "plan-9"), compensationRequest.data)
    }

    @Test
    fun `request compensation stores root metadata and changes saga state`() {
        sagaRecord.requestCompensation(
            now.plusMinutes(4),
            "PAYMENT_REJECTED",
            "payment declined",
            SagaCompensationRequestedBy.INTERNAL,
            "publish-content"
        )

        val reloadedRecord = reloadRecord(sagaRecord)

        assertEquals(Saga.SagaState.COMPENSATION_REQUESTED, reloadedRecord.saga.sagaState)
        assertEquals("PAYMENT_REJECTED", reloadedRecord.saga.compensationRequestCode)
        assertEquals("payment declined", reloadedRecord.saga.compensationRequestReason)
        assertEquals(now.plusMinutes(4), reloadedRecord.saga.compensationRequestedAt)
        assertEquals(SagaCompensationRequestedBy.INTERNAL.name, reloadedRecord.saga.compensationRequestedBy)
        assertEquals("publish-content", reloadedRecord.saga.compensationSourceProcessCode)
    }

    @Test
    fun `compensation ordering resumes from failed reverse step`() {
        val hold = SagaProcess().apply {
            processCode = "reserve-hold"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = now.plusMinutes(2)
            compensationCode = "release-hold"
            compensationState = SagaProcess.SagaCompensationState.COMPENSATED
        }
        val plan = SagaProcess().apply {
            processCode = "create-plan"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = now.plusMinutes(3)
            compensationCode = "cancel-plan"
            compensationState = SagaProcess.SagaCompensationState.FAILED
        }
        sagaRecord.saga.sagaProcesses = mutableListOf(hold, plan)

        val reloadedRecord = reloadRecord(sagaRecord)

        assertEquals(listOf("create-plan"), reloadedRecord.compensationProcessCodesToRun())
    }

    private data class TestRequestParam(
        val action: String,
        val data: Any
    ) : RequestParam<Any>

    private fun reloadRecord(sourceRecord: SagaRecordImpl): SagaRecordImpl {
        val reloadedRecord = SagaRecordImpl()
        reloadedRecord.resume(clonePersistedSaga(sourceRecord.saga))
        return reloadedRecord
    }

    private fun clonePersistedSaga(source: Saga): Saga {
        return Saga().apply {
            id = source.id
            sagaUuid = source.sagaUuid
            svcName = source.svcName
            sagaType = source.sagaType
            param = source.param
            paramType = source.paramType
            result = source.result
            resultType = source.resultType
            exception = source.exception
            compensationRequestCode = source.compensationRequestCode
            compensationRequestReason = source.compensationRequestReason
            compensationRequestedAt = source.compensationRequestedAt
            compensationRequestedBy = source.compensationRequestedBy
            compensationSourceProcessCode = source.compensationSourceProcessCode
            expireAt = source.expireAt
            createAt = source.createAt
            sagaState = source.sagaState
            lastTryTime = source.lastTryTime
            nextTryTime = source.nextTryTime
            triedTimes = source.triedTimes
            tryTimes = source.tryTimes
            version = source.version
            sagaProcesses = source.sagaProcesses.map(::clonePersistedSagaProcess).toMutableList()
        }
    }

    private fun clonePersistedSagaProcess(source: SagaProcess): SagaProcess {
        return SagaProcess().apply {
            id = source.id
            processCode = source.processCode
            param = source.param
            paramType = source.paramType
            result = source.result
            resultType = source.resultType
            exception = source.exception
            executedAt = source.executedAt
            compensationCode = source.compensationCode
            compensationParam = source.compensationParam
            compensationParamType = source.compensationParamType
            compensationResult = source.compensationResult
            compensationResultType = source.compensationResultType
            compensationException = source.compensationException
            compensationState = source.compensationState
            compensationLastTryTime = source.compensationLastTryTime
            compensationTriedTimes = source.compensationTriedTimes
            compensatedAt = source.compensatedAt
            processState = source.processState
            createAt = source.createAt
            lastTryTime = source.lastTryTime
            triedTimes = source.triedTimes
        }
    }
}
