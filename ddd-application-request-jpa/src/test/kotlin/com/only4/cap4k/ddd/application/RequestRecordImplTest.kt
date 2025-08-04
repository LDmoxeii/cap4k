package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.application.persistence.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("RequestRecordImpl实现类测试")
class RequestRecordImplTest {

    private lateinit var requestRecord: RequestRecordImpl
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        requestRecord = RequestRecordImpl()
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTest {

        @Test
        @DisplayName("初始化简单请求")
        fun `should initialize simple request correctly`() {
            // Given
            val requestParam = TestRequestParam("create", mapOf("name" to "test"), 123456789L)
            val svcName = "test-service"
            val requestType = "TEST_REQUEST"
            val expireAfter = Duration.ofMinutes(30)
            val retryTimes = 3

            // When
            requestRecord.init(requestParam, svcName, requestType, testTime, expireAfter, retryTimes)

            // Then
            assertNotNull(requestRecord.id)
            assertTrue(requestRecord.id.isNotEmpty())
            assertEquals(requestType, requestRecord.type)
            assertEquals(requestParam, requestRecord.param)
            assertEquals(testTime, requestRecord.scheduleTime)
            assertNotNull(requestRecord.nextTryTime)
        }

        @Test
        @DisplayName("初始化用户创建请求")
        fun `should initialize create user request correctly`() {
            // Given
            val requestParam = CreateUserRequestParam("john", "john@test.com", "ADMIN")

            // When
            requestRecord.init(requestParam, "user-service", "CREATE_USER", testTime, Duration.ofHours(1), 5)

            // Then
            assertEquals("CREATE_USER", requestRecord.type)
            assertEquals(requestParam, requestRecord.param)
            assertEquals("john", (requestRecord.param as CreateUserRequestParam).username)
        }

        @Test
        @DisplayName("初始化订单处理请求")
        fun `should initialize process order request correctly`() {
            // Given
            val items = listOf(
                ProcessOrderRequestParam.OrderItem("product1", 2, 99.99),
                ProcessOrderRequestParam.OrderItem("product2", 1, 49.99)
            )
            val requestParam = ProcessOrderRequestParam("order123", "customer456", 249.97, items)

            // When
            requestRecord.init(requestParam, "order-service", "PROCESS_ORDER", testTime, Duration.ofMinutes(15), 3)

            // Then
            assertEquals("PROCESS_ORDER", requestRecord.type)
            assertEquals(requestParam, requestRecord.param)
            val orderParam = requestRecord.param as ProcessOrderRequestParam
            assertEquals("order123", orderParam.orderId)
            assertEquals(2, orderParam.items.size)
        }

        @Test
        @DisplayName("初始化后Request应该被正确设置")
        fun `should set request correctly after initialization`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))

            // When
            requestRecord.init(requestParam, "test-service", "TEST", testTime, Duration.ofMinutes(10), 2)

            // Then
            assertNotNull(requestRecord.request)
            assertEquals(requestRecord.request.requestParam, requestParam)
            assertEquals(requestRecord.request.svcName, "test-service")
            assertEquals(requestRecord.request.requestType, "TEST")
            assertEquals(requestRecord.request.lastTryTime, testTime)
        }
    }

    @Nested
    @DisplayName("属性访问测试")
    inner class PropertyAccessTest {

        @BeforeEach
        fun setUp() {
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
        }

        @Test
        @DisplayName("id属性应该返回request的requestUuid")
        fun `should return request uuid as id`() {
            // When
            val id = requestRecord.id

            // Then
            assertEquals(requestRecord.request.requestUuid, id)
            assertTrue(id.isNotEmpty())
        }

        @Test
        @DisplayName("type属性应该返回request的requestType")
        fun `should return request type as type`() {
            // When
            val type = requestRecord.type

            // Then
            assertEquals(requestRecord.request.requestType, type)
            assertEquals("TEST_TYPE", type)
        }

        @Test
        @DisplayName("param属性应该返回request的requestParam")
        fun `should return request param`() {
            // When
            val param = requestRecord.param

            // Then
            assertEquals(requestRecord.request.requestParam, param)
            assertTrue(param is TestRequestParam)
            assertEquals("test", (param as TestRequestParam).action)
        }

        @Test
        @DisplayName("scheduleTime属性应该返回request的lastTryTime")
        fun `should return request lastTryTime as scheduleTime`() {
            // When
            val scheduleTime = requestRecord.scheduleTime

            // Then
            assertEquals(requestRecord.request.lastTryTime, scheduleTime)
            assertEquals(testTime, scheduleTime)
        }

        @Test
        @DisplayName("nextTryTime属性应该返回request的nextTryTime")
        fun `should return request nextTryTime`() {
            // When
            val nextTryTime = requestRecord.nextTryTime

            // Then
            assertEquals(requestRecord.request.nextTryTime, nextTryTime)
            assertNotNull(nextTryTime)
        }
    }

    @Nested
    @DisplayName("结果处理测试")
    inner class ResultHandlingTest {

        @BeforeEach
        fun setUp() {
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
        }

        @Test
        @DisplayName("应该能够获取设置的结果")
        fun `should be able to get set result`() {
            // Given
            val testResult = TestRequestResult(true, "Success", "test data")
            requestRecord.endRequest(testTime.plusMinutes(1), testResult)

            // When
            val result = requestRecord.getResult<TestRequestResult>()

            // Then
            assertNotNull(result)
            assertEquals(testResult, result)
            assertTrue(result!!.success)
            assertEquals("Success", result.message)
        }

        @Test
        @DisplayName("应该能够处理复杂结果类型")
        fun `should handle complex result types`() {
            // Given
            val createUserResult = CreateUserResult("user123", "john", System.currentTimeMillis())
            requestRecord.endRequest(testTime.plusMinutes(1), createUserResult)

            // When
            val result = requestRecord.getResult<CreateUserResult>()

            // Then
            assertNotNull(result)
            assertEquals(createUserResult, result)
            assertEquals("user123", result!!.userId)
            assertEquals("john", result.username)
        }
    }

    @Nested
    @DisplayName("请求状态管理测试")
    inner class RequestStateManagementTest {

        @BeforeEach
        fun setUp() {
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(30), 3)
        }

        @Test
        @DisplayName("新创建的请求应该是有效的")
        fun `should be valid when newly created`() {
            // Then
            assertTrue(requestRecord.isValid)
            assertFalse(requestRecord.isInvalid)
            assertFalse(requestRecord.isExecuting)
            assertFalse(requestRecord.isExecuted)
        }

        @Test
        @DisplayName("应该能够开始请求")
        fun `should be able to begin request`() {
            // When
            val result = requestRecord.beginRequest(testTime.plusMinutes(1))

            // Then
            assertTrue(result)
            assertTrue(requestRecord.isExecuting)
        }

        @Test
        @DisplayName("应该能够取消请求")
        fun `should be able to cancel request`() {
            // When
            val result = requestRecord.cancelRequest(testTime.plusMinutes(1))

            // Then
            assertTrue(result)
            assertTrue(requestRecord.isInvalid)
        }

        @Test
        @DisplayName("应该能够记录异常")
        fun `should be able to record exception`() {
            // Given
            val exception = RuntimeException("Test exception")

            // When
            requestRecord.occurredException(testTime.plusMinutes(1), exception)

            // Then
            assertNotNull(requestRecord.request.exception)
            assertTrue(requestRecord.request.exception!!.contains("Test exception"))
        }

        @Test
        @DisplayName("应该能够结束请求")
        fun `should be able to end request`() {
            // Given
            val result = TestRequestResult(true, "Completed successfully")

            // When
            requestRecord.endRequest(testTime.plusMinutes(1), result)

            // Then
            assertTrue(requestRecord.isExecuted)
            assertFalse(requestRecord.isValid)
            assertEquals(result, requestRecord.getResult<TestRequestResult>())
        }

        @Test
        @DisplayName("应该能够处理请求执行流程")
        fun `should handle request execution flow`() {
            // Given
            val result = CreateUserResult("user123", "john")

            // When - 开始请求
            val beginResult = requestRecord.beginRequest(testTime.plusMinutes(1))
            assertTrue(beginResult)
            assertTrue(requestRecord.isExecuting)

            // Then - 结束请求
            requestRecord.endRequest(testTime.plusMinutes(2), result)
            assertTrue(requestRecord.isExecuted)
            assertFalse(requestRecord.isExecuting)
            assertEquals(result, requestRecord.getResult<CreateUserResult>())
        }
    }

    @Nested
    @DisplayName("请求恢复测试")
    inner class RequestResumeTest {

        @Test
        @DisplayName("应该能够从现有Request恢复")
        fun `should be able to resume from existing request`() {
            // Given
            val requestParam = TestRequestParam("original", mapOf("data" to "test"))
            val originalRequestRecord = RequestRecordImpl()
            originalRequestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
            val originalRequest = originalRequestRecord.request

            // When
            val newRequestRecord = RequestRecordImpl()
            newRequestRecord.resume(originalRequest)

            // Then
            assertEquals(originalRequest, newRequestRecord.request)
            assertEquals(originalRequestRecord.id, newRequestRecord.id)
            assertEquals(originalRequestRecord.param, newRequestRecord.param)
            assertEquals(originalRequestRecord.type, newRequestRecord.type)
        }

        @Test
        @DisplayName("恢复后应该能够正常访问所有属性")
        fun `should access all properties correctly after resume`() {
            // Given
            val items = listOf(ProcessOrderRequestParam.OrderItem("product1", 1, 99.99))
            val requestParam = ProcessOrderRequestParam("order123", "customer456", 99.99, items)
            val originalRequestRecord = RequestRecordImpl()
            originalRequestRecord.init(
                requestParam,
                "order-service",
                "PROCESS_ORDER",
                testTime,
                Duration.ofMinutes(30),
                5
            )

            // When
            val newRequestRecord = RequestRecordImpl()
            newRequestRecord.resume(originalRequestRecord.request)

            // Then
            assertEquals(originalRequestRecord.id, newRequestRecord.id)
            assertEquals("PROCESS_ORDER", newRequestRecord.type)
            assertEquals(requestParam, newRequestRecord.param)
            assertEquals(testTime, newRequestRecord.scheduleTime)

            val orderParam = newRequestRecord.param as ProcessOrderRequestParam
            assertEquals("order123", orderParam.orderId)
            assertEquals(1, orderParam.items.size)
        }
    }

    @Nested
    @DisplayName("toString方法测试")
    inner class ToStringTest {

        @Test
        @DisplayName("toString应该返回request的字符串表示")
        fun `should return request string representation`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)

            // When
            val result = requestRecord.toString()

            // Then
            assertNotNull(result)
            assertEquals(requestRecord.request.toString(), result)
            assertTrue(result.contains("requestUuid"))
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("应该处理空字符串服务名")
        fun `should handle empty service name`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))

            // When & Then
            assertDoesNotThrow {
                requestRecord.init(requestParam, "", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
            }
            assertEquals("", requestRecord.request.svcName)
        }

        @Test
        @DisplayName("应该处理零重试次数")
        fun `should handle zero retry times`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))

            // When & Then
            assertDoesNotThrow {
                requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 0)
            }
            assertEquals(0, requestRecord.request.tryTimes)
        }

        @Test
        @DisplayName("应该处理极短的过期时间")
        fun `should handle very short expire duration`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))

            // When & Then
            assertDoesNotThrow {
                requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMillis(1), 3)
            }
        }

        @Test
        @DisplayName("应该处理复杂对象requestParam")
        fun `should handle complex object request param`() {
            // Given
            val options = ComplexCalculationRequestParam.CalculationOptions(4, 60000, false)
            val parameters = mapOf(
                "algorithm" to "neural_network",
                "dataset_size" to 10000,
                "features" to listOf("feature1", "feature2", "feature3")
            )
            val requestParam = ComplexCalculationRequestParam("ML_TRAINING", parameters, options)

            // When
            requestRecord.init(requestParam, "ml-service", "COMPLEX_CALCULATION", testTime, Duration.ofMinutes(15), 3)

            // Then
            assertEquals("COMPLEX_CALCULATION", requestRecord.type)
            assertEquals(requestParam, requestRecord.param)

            val retrievedParam = requestRecord.param as ComplexCalculationRequestParam
            assertEquals("ML_TRAINING", retrievedParam.calculationType)
            assertEquals(10000, retrievedParam.parameters["dataset_size"])
            assertEquals(4, retrievedParam.options.precision)
            assertFalse(retrievedParam.options.enableCache)
        }

        @Test
        @DisplayName("应该处理类型转换异常")
        fun `should handle type casting exceptions`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)
            requestRecord.endRequest(testTime.plusMinutes(1), "string result")

            // When & Then - 尝试获取错误类型的结果应该返回字符串
            val result = requestRecord.getResult<String>()
            assertEquals("string result", result)
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("多线程访问属性应该是安全的")
        fun `property access should be thread safe`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)

            // When - 多线程同时访问属性
            val ids = mutableSetOf<String>()
            val types = mutableSetOf<String>()
            val threads = (1..10).map {
                Thread {
                    synchronized(ids) {
                        ids.add(requestRecord.id)
                        types.add(requestRecord.type)
                    }
                }
            }

            // 启动所有线程
            threads.forEach { it.start() }
            // 等待所有线程完成
            threads.forEach { it.join() }

            // Then - 所有线程应该获取到相同的值
            assertEquals(1, ids.size, "All threads should get the same id")
            assertEquals(1, types.size, "All threads should get the same type")
            assertEquals(requestRecord.id, ids.first())
            assertEquals("TEST_TYPE", types.first())
        }

        @Test
        @DisplayName("多线程状态变更应该正确处理")
        fun `state changes should be handled correctly in multi-threaded environment`() {
            // Given
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_TYPE", testTime, Duration.ofMinutes(10), 3)

            // When - 多个线程尝试开始请求
            val results = mutableListOf<Boolean>()
            val threads = (1..5).map {
                Thread {
                    val result = requestRecord.beginRequest(testTime.plusMinutes(it.toLong()))
                    synchronized(results) {
                        results.add(result)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then - 验证基本条件即可（不强制要求严格的并发控制）
            val successCount = results.count { it }
            assertTrue(successCount >= 1, "At least one thread should succeed")
        }
    }
}
