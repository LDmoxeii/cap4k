package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.saga.SagaCompensationRequestedBy
import org.junit.jupiter.api.Assertions.assertEquals
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

        val process = sagaRecord.saga.getSagaProcess("create-plan")!!
        assertEquals("cancel-plan", process.compensationCode)
        assertEquals(SagaProcess.SagaCompensationState.READY, process.compensationState)
        assertTrue(process.compensationParam.contains("plan-9"))
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

        assertEquals(Saga.SagaState.COMPENSATION_REQUESTED, sagaRecord.saga.sagaState)
        assertEquals("PAYMENT_REJECTED", sagaRecord.saga.compensationRequestCode)
        assertEquals("payment declined", sagaRecord.saga.compensationRequestReason)
        assertEquals(now.plusMinutes(4), sagaRecord.saga.compensationRequestedAt)
        assertEquals(SagaCompensationRequestedBy.INTERNAL.name, sagaRecord.saga.compensationRequestedBy)
        assertEquals("publish-content", sagaRecord.saga.compensationSourceProcessCode)
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

        assertEquals(listOf("create-plan"), sagaRecord.compensationProcessCodesToRun())
    }

    private data class TestRequestParam(
        val action: String,
        val data: Any
    ) : RequestParam<Any>
}
