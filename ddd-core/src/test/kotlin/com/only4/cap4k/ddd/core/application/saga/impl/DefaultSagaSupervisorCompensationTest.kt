package com.only4.cap4k.ddd.core.application.saga.impl

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.RequestSupervisorSupport
import com.only4.cap4k.ddd.core.application.saga.SagaHandler
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaProcessSupervisor
import com.only4.cap4k.ddd.core.application.saga.SagaCompensationRequestedBy
import com.only4.cap4k.ddd.core.application.saga.SagaRecord
import com.only4.cap4k.ddd.core.application.saga.SagaRecordRepository
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisor
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisorSupport
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import jakarta.validation.Validator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class DefaultSagaSupervisorCompensationTest {

    private lateinit var mockSagaRecordRepository: SagaRecordRepository
    private lateinit var mockValidator: Validator
    private lateinit var mockSagaRecord: SagaRecord
    private lateinit var mockRequestSupervisor: RequestSupervisor

    private val testSvcName = "test-service"
    private val testThreadPoolSize = 5

    data class CreatePlanSagaParam(val planName: String) : SagaParam<CreatePlanResult>
    data class RejectPaymentSagaParam(val planName: String) : SagaParam<String>
    data class CreatePlanRequest(val planName: String) : RequestParam<CreatePlanResult>
    data class CreatePlanResult(val planId: String)
    data class CancelPlanRequest(val planId: String) : RequestParam<Unit>

    @BeforeEach
    fun setUp() {
        mockSagaRecordRepository = mockk(relaxUnitFun = true)
        mockValidator = mockk()
        mockSagaRecord = mockk()
        mockRequestSupervisor = TestRequestSupervisorHolder.instance
        clearMocks(mockRequestSupervisor)

        every { mockSagaRecordRepository.create() } returns mockSagaRecord
        every { mockSagaRecordRepository.save(any()) } just Runs
        every { mockSagaRecord.init(any(), any(), any(), any(), any(), any()) } just Runs
        every { mockSagaRecord.beginSaga(any()) } returns true
        every { mockSagaRecord.endSaga(any(), any()) } just Runs
        every { mockSagaRecord.occurredException(any(), any()) } just Runs
        every { mockSagaRecord.id } returns "test-saga-id"
        every { mockSagaRecord.isExecuting } returns false
        every { mockSagaRecord.isValid } returns true
        every { mockSagaRecord.scheduleTime } returns LocalDateTime.now()
        every { mockSagaRecord.isSagaProcessExecuted(any()) } returns false
        every { mockSagaRecord.beginSagaProcess(any(), any(), any()) } just Runs
        every { mockSagaRecord.endSagaProcess(any(), any(), any()) } just Runs
        every { mockSagaRecord.registerSagaProcessCompensation(any(), any(), any()) } just Runs
        every { mockSagaRecord.sagaProcessOccurredException(any(), any(), any()) } just Runs
        every { mockSagaRecord.requestCompensation(any(), any(), any(), any(), any()) } just Runs
        every { mockSagaRecord.beginCompensation(any()) } returns true
        every { mockSagaRecord.compensationProcessCodesToRun() } returns listOf("create-plan")
        every { mockSagaRecord.getSagaProcessCompensationRequest("create-plan") } returns CancelPlanRequest("plan-9")
        every { mockSagaRecord.beginSagaCompensationProcess(any(), any()) } just Runs
        every { mockSagaRecord.endSagaCompensationProcess(any(), any(), any()) } just Runs
        every { mockSagaRecord.sagaCompensationProcessOccurredException(any(), any(), any()) } just Runs
        every { mockSagaRecord.endCompensation(any()) } just Runs
        every { mockSagaRecord.markManualRepairRequired(any()) } just Runs

        SagaSupervisorSupport.configure(newSupervisor() as SagaSupervisor)
        SagaSupervisorSupport.configure(SagaSupervisorSupport.instance as SagaProcessSupervisor)
        RequestSupervisorSupport.configure(mockRequestSupervisor)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockSagaRecordRepository, mockValidator, mockSagaRecord, mockRequestSupervisor)
    }

    private fun newSupervisor(vararg handlers: RequestHandler<*, *>): DefaultSagaSupervisor =
        DefaultSagaSupervisor(
            requestHandlers = handlers.toList(),
            requestInterceptors = emptyList(),
            validator = mockValidator,
            sagaRecordRepository = mockSagaRecordRepository,
            svcName = testSvcName,
            threadPoolSize = testThreadPoolSize,
            threadFactoryClassName = ""
        )

    @Test
    @DisplayName("execCompensableProcess should register compensation metadata derived from the forward result")
    fun `execCompensableProcess registers compensation metadata derived from response`() {
        val request = CreatePlanSagaParam("plan-9")
        val handler = object : SagaHandler<CreatePlanSagaParam, CreatePlanResult> {
            override fun exec(request: CreatePlanSagaParam): CreatePlanResult = execCompensableProcess(
                processCode = "create-plan",
                request = CreatePlanRequest(request.planName),
                compensationCode = "cancel-plan"
            ) { response: CreatePlanResult ->
                CancelPlanRequest(response.planId)
            }
        }
        every { mockValidator.validate(request) } returns emptySet()
        every { mockRequestSupervisor.send(CreatePlanRequest("plan-9")) } returns
            CreatePlanResult("plan-9")

        val supervisor = newSupervisor(handler)
        val result = supervisor.send(request)

        assertEquals(CreatePlanResult("plan-9"), result)
        verify {
            mockSagaRecord.registerSagaProcessCompensation(
                "create-plan",
                "cancel-plan",
                CancelPlanRequest("plan-9")
            )
        }
    }

    @Test
    @DisplayName("requestCompensation should stop forward execution and surface a domain failure")
    fun `requestCompensation records explicit compensation intent and throws domain exception`() {
        val request = RejectPaymentSagaParam("plan-9")
        val handler = object : SagaHandler<RejectPaymentSagaParam, String> {
            override fun exec(request: RejectPaymentSagaParam): String {
                execCompensableProcess(
                    processCode = "create-plan",
                    request = CreatePlanRequest(request.planName),
                    compensationCode = "cancel-plan"
                ) { response: CreatePlanResult ->
                    CancelPlanRequest(response.planId)
                }
                requestCompensation("PAYMENT_REJECTED", "payment declined")
                throw AssertionError("unreachable")
            }
        }
        every { mockValidator.validate(request) } returns emptySet()
        every { mockRequestSupervisor.send(CreatePlanRequest("plan-9")) } returns
            CreatePlanResult("plan-9")
        every { mockRequestSupervisor.send(CancelPlanRequest("plan-9")) } returns Unit

        val supervisor = newSupervisor(handler)

        val ex = assertThrows<DomainException> {
            supervisor.send(request)
        }

        assertTrue(ex.message!!.contains("PAYMENT_REJECTED"))
        verify {
            mockSagaRecord.requestCompensation(
                any(),
                "PAYMENT_REJECTED",
                "payment declined",
                SagaCompensationRequestedBy.INTERNAL,
                null
            )
        }
        verify(exactly = 0) { mockSagaRecord.endSaga(any(), any()) }
    }

    @Test
    @DisplayName("requestCompensation should save compensation step state before sending reverse request")
    fun `requestCompensation saves compensation step state before reverse send`() {
        val request = RejectPaymentSagaParam("plan-9")
        val handler = object : SagaHandler<RejectPaymentSagaParam, String> {
            override fun exec(request: RejectPaymentSagaParam): String {
                execCompensableProcess(
                    processCode = "create-plan",
                    request = CreatePlanRequest(request.planName),
                    compensationCode = "cancel-plan"
                ) { response: CreatePlanResult ->
                    CancelPlanRequest(response.planId)
                }
                requestCompensation("PAYMENT_REJECTED", "payment declined")
            }
        }
        every { mockValidator.validate(request) } returns emptySet()
        every { mockRequestSupervisor.send(CreatePlanRequest("plan-9")) } returns CreatePlanResult("plan-9")
        every { mockRequestSupervisor.send(CancelPlanRequest("plan-9")) } returns Unit

        val supervisor = newSupervisor(handler)

        assertThrows<DomainException> {
            supervisor.send(request)
        }

        verifyOrder {
            mockSagaRecord.beginSagaCompensationProcess(any(), "create-plan")
            mockSagaRecordRepository.save(mockSagaRecord)
            mockRequestSupervisor.send(CancelPlanRequest("plan-9"))
            mockSagaRecord.endSagaCompensationProcess(any(), "create-plan", Unit)
        }
    }

    @Test
    @DisplayName("requestCompensation should preserve the original compensation code when reverse send fails")
    fun `requestCompensation keeps original code when reverse send fails`() {
        val request = RejectPaymentSagaParam("plan-9")
        val rollbackFailure = IllegalStateException("rollback failed")
        val handler = object : SagaHandler<RejectPaymentSagaParam, String> {
            override fun exec(request: RejectPaymentSagaParam): String {
                execCompensableProcess(
                    processCode = "create-plan",
                    request = CreatePlanRequest(request.planName),
                    compensationCode = "cancel-plan"
                ) { response: CreatePlanResult ->
                    CancelPlanRequest(response.planId)
                }
                requestCompensation("PAYMENT_REJECTED", "payment declined")
            }
        }
        every { mockValidator.validate(request) } returns emptySet()
        every { mockRequestSupervisor.send(CreatePlanRequest("plan-9")) } returns CreatePlanResult("plan-9")
        every { mockRequestSupervisor.send(CancelPlanRequest("plan-9")) } throws rollbackFailure

        val supervisor = newSupervisor(handler)

        val ex = assertThrows<DomainException> {
            supervisor.send(request)
        }

        assertTrue(ex.message!!.contains("PAYMENT_REJECTED"))
        assertEquals(rollbackFailure, ex.cause)
    }

    @Test
    @DisplayName("SagaRecord compensation defaults should fail fast")
    fun `SagaRecord compensation defaults fail fast`() {
        val record = object : SagaRecord {
            override fun init(
                sagaParam: SagaParam<*>,
                svcName: String,
                sagaType: String,
                scheduleAt: LocalDateTime,
                expireAfter: Duration,
                retryTimes: Int
            ) = Unit

            override val id: String = "id"
            override val type: String = "type"
            override val param: SagaParam<*> = CreatePlanSagaParam("plan-9")
            override fun <R : Any> getResult(): R? = null
            override fun beginSagaProcess(now: LocalDateTime, processCode: String, param: RequestParam<*>) = Unit
            override fun endSagaProcess(now: LocalDateTime, processCode: String, result: Any) = Unit
            override fun sagaProcessOccurredException(now: LocalDateTime, processCode: String, throwable: Throwable) = Unit
            override fun isSagaProcessExecuted(processCode: String): Boolean = false
            override fun <R : Any> getSagaProcessResult(processCode: String): R? = null
            override val scheduleTime: LocalDateTime = LocalDateTime.now()
            override val nextTryTime: LocalDateTime = LocalDateTime.now()
            override val isValid: Boolean = true
            override val isInvalid: Boolean = false
            override val isExecuting: Boolean = false
            override val isExecuted: Boolean = false
            override fun beginSaga(now: LocalDateTime): Boolean = true
            override fun cancelSaga(now: LocalDateTime): Boolean = true
            override fun endSaga(now: LocalDateTime, result: Any) = Unit
            override fun occurredException(now: LocalDateTime, throwable: Throwable) = Unit
        }

        assertThrows<UnsupportedOperationException> {
            record.beginCompensation(LocalDateTime.now())
        }
        assertThrows<UnsupportedOperationException> {
            record.compensationProcessCodesToRun()
        }
    }

    @Test
    @DisplayName("operator-triggered compensation should keep the original code when reverse send fails")
    fun `operator compensation keeps original code when reverse send fails`() {
        val rollbackFailure = IllegalStateException("rollback failed")
        every { mockSagaRecordRepository.getById("test-saga-id") } returns mockSagaRecord
        every { mockRequestSupervisor.send(CancelPlanRequest("plan-9")) } throws rollbackFailure
        val supervisor = newSupervisor()

        val ex = assertThrows<DomainException> {
            supervisor.requestCompensation("test-saga-id", "PAYMENT_REJECTED", "payment declined")
        }

        assertTrue(ex.message!!.contains("PAYMENT_REJECTED"))
        assertEquals(rollbackFailure, ex.cause)
    }

    @Test
    @DisplayName("operator-triggered compensation should skip reverse send when already compensating")
    fun `operator compensation skips send when begin compensation returns false`() {
        every { mockSagaRecordRepository.getById("test-saga-id") } returns mockSagaRecord
        every { mockSagaRecord.beginCompensation(any()) } returns false
        val supervisor = newSupervisor()

        supervisor.requestCompensation("test-saga-id", "PAYMENT_REJECTED", "payment declined")

        verify(exactly = 0) { mockRequestSupervisor.send(any<RequestParam<Any>>()) }
        verify(exactly = 0) { mockSagaRecord.beginSagaCompensationProcess(any(), any()) }
    }
}
