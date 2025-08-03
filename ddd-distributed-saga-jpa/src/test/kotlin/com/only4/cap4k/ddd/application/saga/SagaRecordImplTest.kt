package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaProcess
import com.only4.cap4k.ddd.core.application.RequestParam
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("SagaRecordImpl实现类测试")
class SagaRecordImplTest {

    private lateinit var sagaRecord: SagaRecordImpl
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        sagaRecord = SagaRecordImpl()
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTest {

        @Test
        @DisplayName("初始化简单Saga")
        fun `should initialize simple saga correctly`() {
            // Given
            val sagaParam = TestSagaParam("create-user", mapOf("username" to "john"), 123456789L)
            val svcName = "user-service"
            val sagaType = "CREATE_USER_SAGA"
            val expireAfter = Duration.ofMinutes(30)
            val retryTimes = 3

            // When
            sagaRecord.init(sagaParam, svcName, sagaType, testTime, expireAfter, retryTimes)

            // Then
            assertNotNull(sagaRecord.id)
            assertTrue(sagaRecord.id.isNotEmpty())
            assertEquals(sagaType, sagaRecord.type)
            assertEquals(sagaParam, sagaRecord.param)
            assertEquals(testTime, sagaRecord.scheduleTime)
            assertNotNull(sagaRecord.nextTryTime)
        }

        @Test
        @DisplayName("初始化复杂Saga")
        fun `should initialize complex saga correctly`() {
            // Given
            val items = listOf(
                ComplexSagaParam.SagaItem("product1", 2),
                ComplexSagaParam.SagaItem("product2", 1)
            )
            val sagaParam = ComplexSagaParam("order123", "user456", 249.97, items)

            // When
            sagaRecord.init(sagaParam, "order-service", "PROCESS_ORDER_SAGA", testTime, Duration.ofHours(1), 5)

            // Then
            assertEquals("PROCESS_ORDER_SAGA", sagaRecord.type)
            assertEquals(sagaParam, sagaRecord.param)
            val orderParam = sagaRecord.param as ComplexSagaParam
            assertEquals("order123", orderParam.orderId)
            assertEquals(2, orderParam.items.size)
        }

        @Test
        @DisplayName("初始化后Saga应该被正确设置")
        fun `should set saga correctly after initialization`() {
            // Given
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))

            // When
            sagaRecord.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 2)

            // Then
            assertNotNull(sagaRecord.saga)
            assertEquals(sagaRecord.saga.sagaParam, sagaParam)
            assertEquals(sagaRecord.saga.svcName, "test-service")
            assertEquals(sagaRecord.saga.sagaType, "TEST_SAGA")
            assertEquals(sagaRecord.saga.lastTryTime, testTime)
        }
    }

    @Nested
    @DisplayName("属性映射测试")
    inner class PropertyMappingTest {

        @BeforeEach
        fun setupSaga() {
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))
            sagaRecord.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 3)
        }

        @Test
        @DisplayName("应该正确映射ID属性")
        fun `should map id property correctly`() {
            assertEquals(sagaRecord.saga.sagaUuid, sagaRecord.id)
        }

        @Test
        @DisplayName("应该正确映射类型属性")
        fun `should map type property correctly`() {
            assertEquals(sagaRecord.saga.sagaType, sagaRecord.type)
        }

        @Test
        @DisplayName("应该正确映射参数属性")
        fun `should map param property correctly`() {
            assertEquals(sagaRecord.saga.sagaParam, sagaRecord.param)
        }

        @Test
        @DisplayName("应该正确映射调度时间属性")
        fun `should map schedule time property correctly`() {
            assertEquals(sagaRecord.saga.lastTryTime, sagaRecord.scheduleTime)
        }

        @Test
        @DisplayName("应该正确映射下次尝试时间属性")
        fun `should map next try time property correctly`() {
            assertEquals(sagaRecord.saga.nextTryTime, sagaRecord.nextTryTime)
        }

        @Test
        @DisplayName("应该正确映射状态属性")
        fun `should map state properties correctly`() {
            assertEquals(sagaRecord.saga.isValid, sagaRecord.isValid)
            assertEquals(sagaRecord.saga.isInvalid, sagaRecord.isInvalid)
            assertEquals(sagaRecord.saga.isExecuting, sagaRecord.isExecuting)
            assertEquals(sagaRecord.saga.isExecuted, sagaRecord.isExecuted)
        }
    }

    @Nested
    @DisplayName("Saga生命周期管理测试")
    inner class SagaLifecycleTest {

        @BeforeEach
        fun setupSaga() {
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))
            sagaRecord.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 3)
        }

        @Test
        @DisplayName("应该正确开始Saga")
        fun `should begin saga correctly`() {
            // When
            val result = sagaRecord.beginSaga(testTime.plusMinutes(1))

            // Then
            assertTrue(result)
            assertEquals(Saga.SagaState.EXECUTING, sagaRecord.saga.sagaState)
        }

        @Test
        @DisplayName("应该正确取消Saga")
        fun `should cancel saga correctly`() {
            // Given - 首先开始Saga
            sagaRecord.beginSaga(testTime.plusMinutes(1))

            // When
            val result = sagaRecord.cancelSaga(testTime.plusMinutes(2))

            // Then
            assertTrue(result)
            assertEquals(Saga.SagaState.CANCEL, sagaRecord.saga.sagaState)
        }

        @Test
        @DisplayName("应该正确结束Saga")
        fun `should end saga correctly`() {
            // Given
            sagaRecord.beginSaga(testTime.plusMinutes(1))
            val result = mapOf("success" to true, "userId" to "12345")

            // When
            sagaRecord.endSaga(testTime.plusMinutes(2), result)

            // Then
            assertEquals(Saga.SagaState.EXECUTED, sagaRecord.saga.sagaState)
            assertEquals(result, sagaRecord.getResult<Map<String, Any>>())
        }

        @Test
        @DisplayName("应该正确记录异常")
        fun `should record exception correctly`() {
            // Given
            sagaRecord.beginSaga(testTime.plusMinutes(1))
            val exception = RuntimeException("Test error")

            // When
            sagaRecord.occurredException(testTime.plusMinutes(2), exception)

            // Then
            assertEquals(Saga.SagaState.EXCEPTION, sagaRecord.saga.sagaState)
            assertNotNull(sagaRecord.saga.exception)
            assertTrue(sagaRecord.saga.exception!!.contains("Test error"))
        }
    }

    @Nested
    @DisplayName("Saga过程管理测试")
    inner class SagaProcessTest {

        @BeforeEach
        fun setupSaga() {
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))
            sagaRecord.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 3)
            sagaRecord.beginSaga(testTime.plusMinutes(1))
        }

        @Test
        @DisplayName("应该正确开始Saga过程")
        fun `should begin saga process correctly`() {
            // Given
            val processCode = "CREATE_USER"
            val processParam = TestRequestParam("john", "john@test.com")

            // When
            sagaRecord.beginSagaProcess(testTime.plusMinutes(2), processCode, processParam)

            // Then
            val sagaProcess = sagaRecord.saga.getSagaProcess(processCode)
            assertNotNull(sagaProcess)
            assertEquals(SagaProcess.SagaProcessState.EXECUTING, sagaProcess!!.processState)
        }

        @Test
        @DisplayName("应该正确结束Saga过程")
        fun `should end saga process correctly`() {
            // Given
            val processCode = "CREATE_USER"
            val processParam = TestRequestParam("john", "john@test.com")
            val processResult = mapOf("userId" to "12345", "success" to true)

            sagaRecord.beginSagaProcess(testTime.plusMinutes(2), processCode, processParam)

            // When
            sagaRecord.endSagaProcess(testTime.plusMinutes(3), processCode, processResult)

            // Then
            val sagaProcess = sagaRecord.saga.getSagaProcess(processCode)
            assertNotNull(sagaProcess)
            assertEquals(SagaProcess.SagaProcessState.EXECUTED, sagaProcess!!.processState)
            assertTrue(sagaRecord.isSagaProcessExecuted(processCode))
        }

        @Test
        @DisplayName("应该正确记录Saga过程异常")
        fun `should record saga process exception correctly`() {
            // Given
            val processCode = "CREATE_USER"
            val processParam = TestRequestParam("john", "john@test.com")
            val exception = RuntimeException("Process error")

            sagaRecord.beginSagaProcess(testTime.plusMinutes(2), processCode, processParam)

            // When
            sagaRecord.sagaProcessOccurredException(testTime.plusMinutes(3), processCode, exception)

            // Then
            val sagaProcess = sagaRecord.saga.getSagaProcess(processCode)
            assertNotNull(sagaProcess)
            assertEquals(SagaProcess.SagaProcessState.EXCEPTION, sagaProcess!!.processState)
            assertNotNull(sagaProcess.exception)
        }

        @Test
        @DisplayName("应该正确检查Saga过程执行状态")
        fun `should check saga process execution status correctly`() {
            // Given
            val processCode = "CREATE_USER"
            val processParam = TestRequestParam("john", "john@test.com")

            // When & Then - 过程不存在
            assertFalse(sagaRecord.isSagaProcessExecuted(processCode))

            // When & Then - 过程正在执行
            sagaRecord.beginSagaProcess(testTime.plusMinutes(2), processCode, processParam)
            assertFalse(sagaRecord.isSagaProcessExecuted(processCode))

            // When & Then - 过程已完成
            sagaRecord.endSagaProcess(testTime.plusMinutes(3), processCode, mapOf("result" to "success"))
            assertTrue(sagaRecord.isSagaProcessExecuted(processCode))
        }

        @Test
        @DisplayName("应该正确获取Saga过程结果")
        fun `should get saga process result correctly`() {
            // Given
            val processCode = "CREATE_USER"
            val processParam = TestRequestParam("john", "john@test.com")
            val processResult = mapOf("userId" to "12345", "email" to "john@test.com")

            // When & Then - 过程不存在
            assertNull(sagaRecord.getSagaProcessResult<Map<String, Any>>(processCode))

            // When & Then - 过程未完成
            sagaRecord.beginSagaProcess(testTime.plusMinutes(2), processCode, processParam)

            // When & Then - 过程已完成
            sagaRecord.endSagaProcess(testTime.plusMinutes(3), processCode, processResult)
            val result = sagaRecord.getSagaProcessResult<Map<String, Any>>(processCode)
            assertNotNull(result)
            assertEquals("12345", result!!["userId"])
            assertEquals("john@test.com", result["email"])
        }
    }

    @Nested
    @DisplayName("恢复和字符串表示测试")
    inner class ResumeAndStringTest {

        @Test
        @DisplayName("应该正确从Saga恢复")
        fun `should resume from saga correctly`() {
            // Given
            val saga = Saga()
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))
            saga.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 3)

            // When
            sagaRecord.resume(saga)

            // Then
            assertEquals(saga, sagaRecord.saga)
            assertEquals(saga.sagaUuid, sagaRecord.id)
            assertEquals(saga.sagaType, sagaRecord.type)
        }

        @Test
        @DisplayName("应该正确生成字符串表示")
        fun `should generate string representation correctly`() {
            // Given
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))
            sagaRecord.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 3)

            // When
            val stringRepresentation = sagaRecord.toString()

            // Then
            assertNotNull(stringRepresentation)
            assertTrue(stringRepresentation.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("复杂场景测试")
    inner class ComplexScenarioTest {

        @Test
        @DisplayName("多过程Saga完整流程测试")
        fun `should handle multi-process saga complete flow`() {
            // Given - 初始化订单处理Saga
            val items = listOf(
                ComplexSagaParam.SagaItem("product1", 2),
                ComplexSagaParam.SagaItem("product2", 1)
            )
            val sagaParam = ComplexSagaParam("order123", "user456", 249.97, items)
            sagaRecord.init(sagaParam, "order-service", "PROCESS_ORDER_SAGA", testTime, Duration.ofHours(1), 5)

            // When & Then - 开始Saga
            assertTrue(sagaRecord.beginSaga(testTime.plusMinutes(1)))
            assertTrue(sagaRecord.isExecuting)

            // When & Then - 执行创建订单过程
            val createOrderParam = TestRequestParam("create", mapOf("orderId" to "order123"))
            sagaRecord.beginSagaProcess(testTime.plusMinutes(2), "CREATE_ORDER", createOrderParam)
            sagaRecord.endSagaProcess(
                testTime.plusMinutes(3),
                "CREATE_ORDER",
                mapOf("orderId" to "order123", "status" to "created")
            )
            assertTrue(sagaRecord.isSagaProcessExecuted("CREATE_ORDER"))

            // When & Then - 执行库存扣减过程
            val inventoryParam = TestRequestParam("reserve", mapOf("items" to items))
            sagaRecord.beginSagaProcess(testTime.plusMinutes(4), "RESERVE_INVENTORY", inventoryParam)
            sagaRecord.endSagaProcess(
                testTime.plusMinutes(5),
                "RESERVE_INVENTORY",
                mapOf<String, Any>("reservationId" to "res123", "status" to "reserved")
            )
            assertTrue(sagaRecord.isSagaProcessExecuted("RESERVE_INVENTORY"))

            // When & Then - 执行支付过程
            val paymentParam = TestRequestParam("pay", mapOf<String, Any>("amount" to 249.97))
            sagaRecord.beginSagaProcess(testTime.plusMinutes(6), "PROCESS_PAYMENT", paymentParam)
            sagaRecord.endSagaProcess(
                testTime.plusMinutes(7),
                "PROCESS_PAYMENT",
                mapOf<String, Any>("paymentId" to "pay123", "status" to "paid")
            )
            assertTrue(sagaRecord.isSagaProcessExecuted("PROCESS_PAYMENT"))

            // When & Then - 完成Saga
            val sagaResult = mapOf(
                "orderId" to "order123",
                "status" to "completed",
                "totalAmount" to 249.97
            )
            sagaRecord.endSaga(testTime.plusMinutes(8), sagaResult)
            assertTrue(sagaRecord.isExecuted)

            // Then - 验证最终结果
            val finalResult = sagaRecord.getResult<Map<String, Any>>()!!
            assertEquals("order123", finalResult["orderId"])
            assertEquals("completed", finalResult["status"])
            assertEquals(249.97, finalResult["totalAmount"])

            // 验证各过程结果
            val createOrderResult = sagaRecord.getSagaProcessResult<Map<String, Any>>("CREATE_ORDER")
            assertEquals("order123", createOrderResult!!["orderId"])

            val inventoryResult = sagaRecord.getSagaProcessResult<Map<String, Any>>("RESERVE_INVENTORY")
            assertEquals("res123", inventoryResult!!["reservationId"])

            val paymentResult = sagaRecord.getSagaProcessResult<Map<String, Any>>("PROCESS_PAYMENT")
            assertEquals("pay123", paymentResult!!["paymentId"])
        }

        @Test
        @DisplayName("Saga异常回滚流程测试")
        fun `should handle saga exception rollback flow`() {
            // Given
            val sagaParam = TestSagaParam("rollback-test", mapOf("orderId" to "order999"))
            sagaRecord.init(sagaParam, "order-service", "ROLLBACK_SAGA", testTime, Duration.ofHours(1), 3)

            // When & Then - 开始Saga并执行一些过程
            sagaRecord.beginSaga(testTime.plusMinutes(1))

            sagaRecord.beginSagaProcess(
                testTime.plusMinutes(2),
                "CREATE_ORDER",
                TestRequestParam("create", emptyMap<String, Any>())
            )
            sagaRecord.endSagaProcess(
                testTime.plusMinutes(3),
                "CREATE_ORDER",
                mapOf<String, Any>("orderId" to "order999")
            )

            sagaRecord.beginSagaProcess(
                testTime.plusMinutes(4),
                "RESERVE_INVENTORY",
                TestRequestParam("reserve", emptyMap<String, Any>())
            )

            // 库存扣减失败
            sagaRecord.sagaProcessOccurredException(
                testTime.plusMinutes(5),
                "RESERVE_INVENTORY",
                RuntimeException("Insufficient inventory")
            )

            // Saga整体异常
            sagaRecord.occurredException(testTime.plusMinutes(6), RuntimeException("Saga failed due to inventory"))

            // Then - 验证状态
            assertTrue(sagaRecord.isValid) // 异常状态仍然是valid
            assertFalse(sagaRecord.isInvalid) // 异常状态不是invalid
            assertFalse(sagaRecord.isExecuted)
            assertTrue(sagaRecord.isSagaProcessExecuted("CREATE_ORDER"))
            assertFalse(sagaRecord.isSagaProcessExecuted("RESERVE_INVENTORY"))
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class BoundaryConditionTest {

        @Test
        @DisplayName("处理空参数")
        fun `should handle empty parameters`() {
            // Given
            val sagaParam = TestSagaParam("", emptyMap())

            // When & Then
            assertDoesNotThrow {
                sagaRecord.init(sagaParam, "test-service", "EMPTY_SAGA", testTime, Duration.ofMinutes(1), 1)
            }
            assertEquals("", (sagaRecord.param as TestSagaParam).action)
            assertTrue((sagaRecord.param as TestSagaParam).data.isEmpty())
        }

        @Test
        @DisplayName("处理不存在的过程")
        fun `should handle non-existent process`() {
            // Given
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))
            sagaRecord.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 3)

            // When & Then
            assertFalse(sagaRecord.isSagaProcessExecuted("NON_EXISTENT"))
            assertNull(sagaRecord.getSagaProcessResult<Any>("NON_EXISTENT"))
        }

        @Test
        @DisplayName("处理极短过期时间")
        fun `should handle very short expiration time`() {
            // Given
            val sagaParam = TestSagaParam("urgent", mapOf("priority" to "high"))

            // When & Then
            assertDoesNotThrow {
                sagaRecord.init(sagaParam, "urgent-service", "URGENT_SAGA", testTime, Duration.ofSeconds(1), 1)
            }
            assertNotNull(sagaRecord.nextTryTime)
        }
    }

    // 测试用请求参数类
    data class TestRequestParam(
        val action: String,
        val data: Any
    ) : RequestParam<Any>
}
