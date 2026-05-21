package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSaga
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaProcess
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
    fun `compensation ordering drops compensated prefix and resumes remaining reverse steps`() {
        val hold = SagaProcess().apply {
            id = 1L
            processCode = "reserve-hold"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = now.plusMinutes(4)
            createAt = now.plusMinutes(4)
            compensationCode = "release-hold"
            compensationState = SagaProcess.SagaCompensationState.COMPENSATED
        }
        val plan = SagaProcess().apply {
            id = 2L
            processCode = "create-plan"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = now.plusMinutes(3)
            createAt = now.plusMinutes(3)
            compensationCode = "cancel-plan"
            compensationState = SagaProcess.SagaCompensationState.FAILED
        }
        sagaRecord.saga.sagaProcesses = mutableListOf(hold, plan)

        val reloadedRecord = reloadRecord(sagaRecord)

        assertEquals(listOf("create-plan"), reloadedRecord.compensationProcessCodesToRun())
    }

    @Test
    fun `hydrated reverse scan keeps non compensable latest step before earlier compensable work`() {
        val compensable = SagaProcess().apply {
            id = 1L
            processCode = "create-plan"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = null
            createAt = now.plusMinutes(2)
            compensationCode = "cancel-plan"
            compensationState = SagaProcess.SagaCompensationState.READY
        }
        val nonCompensable = SagaProcess().apply {
            id = 2L
            processCode = "publish-content"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = null
            createAt = now.plusMinutes(3)
            compensationCode = ""
            compensationState = SagaProcess.SagaCompensationState.NONE
        }
        sagaRecord.saga.sagaProcesses = mutableListOf(compensable, nonCompensable)

        val reloadedRecord = reloadRecord(sagaRecord)

        assertEquals(listOf("publish-content", "create-plan"), reloadedRecord.compensationProcessCodesToRun())
    }

    @Test
    fun `hydrated reverse scan skips compensated step even when interleaved between runnable steps`() {
        val earlierReady = SagaProcess().apply {
            id = 1L
            processCode = "reserve-hold"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = null
            createAt = now.plusMinutes(2)
            compensationCode = "release-hold"
            compensationState = SagaProcess.SagaCompensationState.READY
        }
        val middleCompensated = SagaProcess().apply {
            id = 2L
            processCode = "create-plan"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = null
            createAt = now.plusMinutes(3)
            compensationCode = "cancel-plan"
            compensationState = SagaProcess.SagaCompensationState.COMPENSATED
        }
        val latestNonCompensable = SagaProcess().apply {
            id = 3L
            processCode = "publish-content"
            processState = SagaProcess.SagaProcessState.EXECUTED
            executedAt = null
            createAt = now.plusMinutes(4)
            compensationCode = ""
            compensationState = SagaProcess.SagaCompensationState.NONE
        }
        sagaRecord.saga.sagaProcesses = mutableListOf(earlierReady, middleCompensated, latestNonCompensable)

        val reloadedRecord = reloadRecord(sagaRecord)

        assertEquals(listOf("publish-content", "reserve-hold"), reloadedRecord.compensationProcessCodesToRun())
    }

    @Test
    fun `archive copies compensation fields from saga and process`() {
        val saga = Saga().apply {
            id = 99L
            sagaUuid = "saga-99"
            svcName = "svc"
            sagaType = "ARCHIVE_SAGA"
            param = """{"action":"publish"}"""
            paramType = TestSagaParam::class.java.name
            result = """{"success":true}"""
            resultType = Map::class.java.name
            exception = "forward exception"
            compensationRequestCode = "PUBLISH_REJECTED"
            compensationRequestReason = "moderation failed"
            compensationRequestedAt = now.plusMinutes(4)
            compensationRequestedBy = SagaCompensationRequestedBy.OPERATOR.name
            compensationSourceProcessCode = "publish-content"
            expireAt = now.plusHours(1)
            createAt = now
            sagaState = Saga.SagaState.MANUAL_REPAIR_REQUIRED
            lastTryTime = now.plusMinutes(6)
            nextTryTime = now.plusMinutes(7)
            triedTimes = 2
            tryTimes = 5
            version = 3
        }
        val process = SagaProcess().apply {
            id = 11L
            processCode = "publish-content"
            param = """{"action":"publish"}"""
            paramType = TestRequestParam::class.java.name
            result = """{"contentId":"content-9"}"""
            resultType = Map::class.java.name
            exception = "process exception"
            executedAt = now.plusMinutes(3)
            compensationCode = "unpublish-content"
            compensationParam = """{"action":"unpublish","data":{"contentId":"content-9"}}"""
            compensationParamType = TestRequestParam::class.java.name
            compensationResult = """{"status":"done"}"""
            compensationResultType = Map::class.java.name
            compensationException = "compensation exception"
            compensationState = SagaProcess.SagaCompensationState.MANUAL_REPAIR_REQUIRED
            compensationLastTryTime = now.plusMinutes(5)
            compensationTriedTimes = 4
            compensatedAt = now.plusMinutes(6)
            processState = SagaProcess.SagaProcessState.EXECUTED
            createAt = now.plusMinutes(1)
            lastTryTime = now.plusMinutes(3)
            triedTimes = 2
        }
        saga.sagaProcesses = mutableListOf(process)

        val archivedSaga = ArchivedSaga().archiveFrom(saga)
        val archivedProcess = ArchivedSagaProcess().archiveFrom(process)

        assertEquals("PUBLISH_REJECTED", archivedSaga.compensationRequestCode)
        assertEquals("moderation failed", archivedSaga.compensationRequestReason)
        assertEquals(now.plusMinutes(4), archivedSaga.compensationRequestedAt)
        assertEquals(SagaCompensationRequestedBy.OPERATOR.name, archivedSaga.compensationRequestedBy)
        assertEquals("publish-content", archivedSaga.compensationSourceProcessCode)
        assertEquals(Saga.SagaState.MANUAL_REPAIR_REQUIRED, archivedSaga.sagaState)

        assertEquals(now.plusMinutes(3), archivedSaga.sagaProcesses.single().executedAt)
        assertEquals("unpublish-content", archivedSaga.sagaProcesses.single().compensationCode)
        assertEquals(process.compensationParam, archivedSaga.sagaProcesses.single().compensationParam)
        assertEquals(process.compensationParamType, archivedSaga.sagaProcesses.single().compensationParamType)
        assertEquals(process.compensationResult, archivedSaga.sagaProcesses.single().compensationResult)
        assertEquals(process.compensationResultType, archivedSaga.sagaProcesses.single().compensationResultType)
        assertEquals(process.compensationException, archivedSaga.sagaProcesses.single().compensationException)
        assertEquals(process.compensationState, archivedSaga.sagaProcesses.single().compensationState)
        assertEquals(process.compensationLastTryTime, archivedSaga.sagaProcesses.single().compensationLastTryTime)
        assertEquals(process.compensationTriedTimes, archivedSaga.sagaProcesses.single().compensationTriedTimes)
        assertEquals(process.compensatedAt, archivedSaga.sagaProcesses.single().compensatedAt)

        assertEquals(process.executedAt, archivedProcess.executedAt)
        assertEquals(process.compensationCode, archivedProcess.compensationCode)
        assertEquals(process.compensationParam, archivedProcess.compensationParam)
        assertEquals(process.compensationParamType, archivedProcess.compensationParamType)
        assertEquals(process.compensationResult, archivedProcess.compensationResult)
        assertEquals(process.compensationResultType, archivedProcess.compensationResultType)
        assertEquals(process.compensationException, archivedProcess.compensationException)
        assertEquals(process.compensationState, archivedProcess.compensationState)
        assertEquals(process.compensationLastTryTime, archivedProcess.compensationLastTryTime)
        assertEquals(process.compensationTriedTimes, archivedProcess.compensationTriedTimes)
        assertEquals(process.compensatedAt, archivedProcess.compensatedAt)
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
