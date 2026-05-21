package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.application.*
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.EventAttachment
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeScopeType
import com.only4.cap4k.ddd.core.domain.event.impl.RequestDispatchException
import io.mockk.*
import jakarta.validation.Validator
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

/**
 * DefaultRequestSupervisor 单元测试
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
@DisplayName("DefaultRequestSupervisor 测试")
class DefaultRequestSupervisorTest {

    // Mock 依赖
    private lateinit var mockRequestRecordRepository: RequestRecordRepository
    private lateinit var mockValidator: Validator
    private lateinit var mockRequestRecord: RequestRecord
    private lateinit var mockSagaSupervisor: SagaSupervisor
    private lateinit var mockRequestSupervisor: RequestSupervisor

    // 测试对象
    private lateinit var supervisor: DefaultRequestSupervisor

    // 测试常量
    private val testSvcName = "test-service"
    private val testThreadPoolSize = 2
    private val testRequestId = "test-request-id"

    // 测试数据类
    data class TestCommandRequest(val value: String) : RequestParam<String>
    data class TestQueryRequest(val id: Int) : RequestParam<Int>
    data class TestSagaRequest(val data: String) : SagaParam<String>
    data class TestDomainEvent(val message: String)

    // 测试处理器
    class TestCommandHandler : Command<TestCommandRequest, String> {
        override fun exec(request: TestCommandRequest): String = "success"
    }

    class TestQueryHandler : Query<TestQueryRequest, Int> {
        override fun exec(request: TestQueryRequest): Int = 42
    }

    // 测试拦截器
    class TestInterceptor : RequestInterceptor<TestCommandRequest, String> {
        override fun preRequest(request: TestCommandRequest) {}
        override fun postRequest(request: TestCommandRequest, response: String) {}
    }

    @BeforeEach
    fun setUp() {
        // 初始化 Mock 对象
        mockRequestRecordRepository = mockk()
        mockValidator = mockk()
        mockRequestRecord = mockk()

        // Mock SagaSupervisor
        mockSagaSupervisor = mockk()
        mockkObject(SagaSupervisor)
        every { SagaSupervisor.instance } returns mockSagaSupervisor

        // Mock RequestSupervisor
        mockRequestSupervisor = mockk()
        mockkObject(RequestSupervisor)
        every { RequestSupervisor.instance } returns mockRequestSupervisor

        // Mock 基本行为
        every { mockRequestRecord.id } returns testRequestId
        every { mockRequestRecord.isExecuting } returns true
        every { mockRequestRecord.scheduleTime } returns LocalDateTime.now().plusMinutes(1)
        every { mockRequestRecord.param } returns TestCommandRequest("test")
        every { mockRequestRecord.beginRequest(any()) } returns true
        every { mockRequestRecord.endRequest(any(), any()) } just Runs
        every { mockRequestRecord.occurredException(any(), any()) } just Runs
        every { mockRequestRecord.getResult<String>() } returns "test-result"
        every { mockRequestRecord.init(any(), any(), any(), any(), any(), any()) } just Runs

        every { mockRequestRecordRepository.create() } returns mockRequestRecord
        every { mockRequestRecordRepository.save(any()) } just Runs
        every { mockRequestRecordRepository.getById(any()) } returns mockRequestRecord
        every { mockRequestRecordRepository.getByNextTryTime(any(), any(), any()) } returns emptyList()
        every { mockRequestRecordRepository.archiveByExpireAt(any(), any(), any()) } returns 0

        every { mockValidator.validate(any<Any>()) } returns emptySet()
    }

    @AfterEach
    fun tearDown() {
        EventRuntimeContext.reset()
        unmockkAll()
    }

    @Nested
    @DisplayName("send() 方法测试")
    inner class SendTests {

        @Test
        @DisplayName("成功发送Command请求")
        fun `should send command successfully`() {
            // Given
            val handler = TestCommandHandler()
            val interceptor = TestInterceptor()
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(handler),
                requestInterceptors = listOf(interceptor),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("test")

            // When
            val result = testSupervisor.send(request)

            // Then
            assertEquals("success", result)
            verify { mockValidator.validate(request) }
        }

        @Test
        @DisplayName("成功发送Query请求")
        fun `should send query successfully`() {
            // Given
            val handler = TestQueryHandler()
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(handler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestQueryRequest(123)

            // When
            val result = testSupervisor.send(request)

            // Then
            assertEquals(42, result)
            verify { mockValidator.validate(request) }
        }

        @Test
        @DisplayName("发送Saga请求应委托给SagaSupervisor")
        fun `should delegate saga request to SagaSupervisor`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val sagaRequest = TestSagaRequest("saga-data")
            every { mockSagaSupervisor.send(sagaRequest) } returns "saga-result"

            // When
            val result = supervisor.send(sagaRequest)

            // Then
            assertEquals("saga-result", result)
            verify { mockSagaSupervisor.send(sagaRequest) }
            verify(exactly = 0) { mockValidator.validate(any<Any>()) }
        }

        @Test
        @DisplayName("处理器执行异常应包装为请求调度诊断异常")
        fun `handler failure throws request dispatch exception with request and handler classes`() {
            // Given
            val cause = RuntimeException("Handler error")
            val failingHandler = object : RequestHandler<TestCommandRequest, String> {
                override fun exec(request: TestCommandRequest): String {
                    throw cause
                }
            }
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(failingHandler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("error")

            // When & Then
            val thrownException = assertThrows<RuntimeException> {
                testSupervisor.send(request)
            }
            val requestDispatchException = assertInstanceOf(RequestDispatchException::class.java, thrownException)
            assertEquals(TestCommandRequest::class.java, requestDispatchException.requestParamClass)
            assertEquals(failingHandler::class.java, requestDispatchException.requestHandlerClass)
            assertSame(cause, requestDispatchException.cause)
        }

        @Test
        @DisplayName("处理器抛出Error时不应包装为请求调度异常")
        fun `handler error is not wrapped in request dispatch exception`() {
            // Given
            val error = AssertionError("handler fatal error")
            val failingHandler = object : RequestHandler<TestCommandRequest, String> {
                override fun exec(request: TestCommandRequest): String {
                    throw error
                }
            }
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(failingHandler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )

            // When & Then
            val thrownError = assertThrows<AssertionError> {
                testSupervisor.send(TestCommandRequest("fatal"))
            }
            assertSame(error, thrownError)
        }

        @Test
        @DisplayName("拦截器抛出Error时不应包装为请求调度异常")
        fun `interceptor error is not wrapped in request dispatch exception`() {
            // Given
            val error = AssertionError("interceptor fatal error")
            val failingInterceptor = object : RequestInterceptor<TestCommandRequest, String> {
                override fun preRequest(request: TestCommandRequest) {
                    throw error
                }

                override fun postRequest(request: TestCommandRequest, response: String) {}
            }
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(TestCommandHandler()),
                requestInterceptors = listOf(failingInterceptor),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )

            // When & Then
            val thrownError = assertThrows<AssertionError> {
                testSupervisor.send(TestCommandRequest("fatal-interceptor"))
            }
            assertSame(error, thrownError)
        }

        @Test
        @DisplayName("找不到处理器应抛出请求调度诊断异常")
        fun `missing handler throws request dispatch exception without handler class`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            data class UnknownRequest(val data: String) : RequestParam<String>

            val request = UnknownRequest("test")

            // When & Then
            val exception = assertThrows<RequestDispatchException> {
                supervisor.send(request)
            }
            assertEquals(UnknownRequest::class.java, exception.requestParamClass)
            assertEquals(null, exception.requestHandlerClass)
            assertTrue(exception.message!!.contains("No handler found for request type"))
        }

        @Test
        @DisplayName("事件监听器内请求失败应包含监听器诊断元数据")
        fun `request failure inside listener context includes listener diagnostic metadata`() {
            // Given
            val scope = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
            scope.listenerBeanName = "diagnosticListener"
            scope.listenerClass = TestCommandHandler::class.java
            scope.listenerMethod = TestCommandHandler::class.java.getDeclaredMethod("exec", TestCommandRequest::class.java)
            val failingHandler = object : RequestHandler<TestCommandRequest, String> {
                override fun exec(request: TestCommandRequest): String {
                    throw RuntimeException("listener request failure")
                }
            }
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(failingHandler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )

            // When & Then
            val exception = assertThrows<RequestDispatchException> {
                testSupervisor.send(TestCommandRequest("listener"))
            }
            assertEquals("diagnosticListener", exception.diagnosticContext?.listenerBeanName)
            assertEquals(TestCommandHandler::class.java.name, exception.diagnosticContext?.listenerClassName)
            assertEquals("exec", exception.diagnosticContext?.listenerMethodName)
        }

        @Test
        @DisplayName("嵌套请求作用域不应清理外层领域事件附件")
        fun `nested request execution does not clear outer domain dispatch attachments`() {
            // Given
            val outerScope = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
            val entity = Any()
            outerScope.attachDomain(entity, EventAttachment.eager(TestDomainEvent("outer")))
            val handler = object : RequestHandler<TestCommandRequest, String> {
                override fun exec(request: TestCommandRequest): String {
                    assertEquals(EventRuntimeScopeType.REQUEST, EventRuntimeContext.current().type)
                    return "nested-success"
                }
            }
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(handler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )

            // When
            val result = testSupervisor.send(TestCommandRequest("nested"))

            // Then
            assertEquals("nested-success", result)
            assertSame(outerScope, EventRuntimeContext.current())
            assertEquals(listOf(TestDomainEvent("outer")), outerScope.domainAttachments[entity]?.map { it.resolve() })
        }

        @Test
        @DisplayName("请求异常清理泄漏嵌套作用域时保留原始异常并恢复外层作用域")
        fun `request cleanup restores outer scope and preserves original exception when nested scope leaks`() {
            // Given
            val outerScope = EventRuntimeContext.push(EventRuntimeScopeType.DOMAIN_DISPATCH)
            val cause = IllegalArgumentException("original handler failure")
            val failingHandler = object : RequestHandler<TestCommandRequest, String> {
                override fun exec(request: TestCommandRequest): String {
                    EventRuntimeContext.push(EventRuntimeScopeType.REQUEST)
                    throw cause
                }
            }
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(failingHandler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )

            // When & Then
            val exception = assertThrows<RequestDispatchException> {
                testSupervisor.send(TestCommandRequest("leaked-nested"))
            }
            assertSame(cause, exception.cause)
            assertSame(outerScope, EventRuntimeContext.current())
        }

        @Test
        @DisplayName("无验证器时应正常执行")
        fun `should work normally when validator is null`() {
            // Given
            val handler = TestCommandHandler()
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(handler),
                requestInterceptors = emptyList(),
                validator = null,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("test")

            // When
            val result = testSupervisor.send(request)

            // Then
            assertEquals("success", result)
        }
    }

    @Nested
    @DisplayName("schedule() 方法测试")
    inner class ScheduleTests {

        @Test
        @DisplayName("成功调度立即执行的请求")
        fun `should schedule immediate execution successfully`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("scheduled")
            val scheduleTime = LocalDateTime.now()
            every { mockRequestRecord.isExecuting } returns true
            every { mockRequestRecord.scheduleTime } returns scheduleTime

            // When
            val requestId = supervisor.schedule(request, scheduleTime)

            // Then
            assertEquals(testRequestId, requestId)
            verify { mockValidator.validate(request) }
            verify { mockRequestRecordRepository.create() }
            verify {
                mockRequestRecord.init(
                    request,
                    testSvcName,
                    request::class.java.name,
                    scheduleTime,
                    any(),
                    any()
                )
            }
            verify { mockRequestRecordRepository.save(mockRequestRecord) }
        }

        @Test
        @DisplayName("调度Saga请求应委托给SagaSupervisor")
        fun `should delegate saga schedule to SagaSupervisor`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val sagaRequest = TestSagaRequest("saga-scheduled")
            val scheduleTime = LocalDateTime.now().plusMinutes(5)
            every { mockSagaSupervisor.schedule(sagaRequest, scheduleTime) } returns "saga-request-id"

            // When
            val requestId = supervisor.schedule(sagaRequest, scheduleTime)

            // Then
            assertEquals("saga-request-id", requestId)
            verify { mockSagaSupervisor.schedule(sagaRequest, scheduleTime) }
            verify(exactly = 0) { mockRequestRecordRepository.create() }
        }

        @Test
        @DisplayName("非执行状态的请求不应调度")
        fun `should not schedule non-executing request`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("non-executing")
            val scheduleTime = LocalDateTime.now()
            every { mockRequestRecord.isExecuting } returns false

            // When
            val requestId = supervisor.schedule(request, scheduleTime)

            // Then
            assertEquals(testRequestId, requestId)
            verify { mockRequestRecordRepository.save(mockRequestRecord) }
        }
    }

    @Nested
    @DisplayName("result() 方法测试")
    inner class ResultTests {

        @Test
        @DisplayName("成功获取本地请求结果")
        fun `should get local request result successfully`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            every { mockRequestRecordRepository.getById(testRequestId) } returns mockRequestRecord

            // When
            val result = supervisor.result<String>(testRequestId)

            // Then
            assertEquals("test-result", result)
            verify { mockRequestRecordRepository.getById(testRequestId) }
            verify { mockRequestRecord.getResult<String>() }
        }

//        @Test
//        @DisplayName("本地找不到记录时应委托给RequestSupervisor")
//        fun `should delegate to RequestSupervisor when local record not found`() {
//            // Given
//            supervisor = DefaultRequestSupervisor(
//                requestHandlers = emptyList(),
//                requestInterceptors = emptyList(),
//                validator = mockValidator,
//                requestRecordRepository = mockRequestRecordRepository,
//                svcName = testSvcName,
//                threadPoolSize = testThreadPoolSize,
//                threadFactoryClassName = ""
//            )
//            every { mockRequestRecordRepository.getById(testRequestId) } returns null
//            every { mockRequestSupervisor.result<String>(testRequestId) } returns "delegated-result"
//
//            // When
//            val result = supervisor.result<String>(testRequestId)
//
//            // Then
//            assertEquals("delegated-result", result)
//            verify { mockRequestSupervisor.result<String>(testRequestId) }
//        }
    }

    @Nested
    @DisplayName("resume() 方法测试")
    inner class ResumeTests {

        @Test
        @DisplayName("成功恢复执行请求")
        fun `should resume request execution successfully`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("resume")
            val minNextTryTime = LocalDateTime.now().plusMinutes(5)
            val nextTryTime = LocalDateTime.now().plusMinutes(10) // 设置为大于minNextTryTime的时间
            every { mockRequestRecord.param } returns request

            // 配置nextTryTime的返回序列，第一次调用返回nextTryTime，之后返回minNextTryTime
            every { mockRequestRecord.nextTryTime } returnsMany listOf(nextTryTime, minNextTryTime)

            every { mockRequestRecord.beginRequest(any()) } answers {
                // 模拟beginRequest会更新nextTryTime为minNextTryTime，这样可以跳出while循环
                every { mockRequestRecord.nextTryTime } returns minNextTryTime
                true
            }
            every { mockRequestRecord.isExecuting } returns true
            every { mockRequestRecord.isValid } returns true

            // When
            supervisor.resume(mockRequestRecord, minNextTryTime)

            // Then
            verify { mockRequestRecord.beginRequest(any()) }
            verify { mockValidator.validate(mockRequestRecord) }
            verify { mockRequestRecordRepository.save(mockRequestRecord) }
        }

        @Test
        @DisplayName("开始请求失败时应保存记录并返回")
        fun `should save record and return when begin request fails`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val minNextTryTime = LocalDateTime.now().plusMinutes(5)
            val nextTryTime = LocalDateTime.now().plusMinutes(10) // 设置为大于minNextTryTime的时间

            // 配置nextTryTime的返回序列，第一次调用返回nextTryTime，之后返回minNextTryTime
            every { mockRequestRecord.nextTryTime } returnsMany listOf(nextTryTime, minNextTryTime)

            every { mockRequestRecord.beginRequest(any()) } answers {
                // 模拟beginRequest会更新nextTryTime为minNextTryTime，这样可以跳出while循环
                every { mockRequestRecord.nextTryTime } returns minNextTryTime
                false // 返回false表示beginRequest失败
            }
            every { mockRequestRecord.isExecuting } returns false
            every { mockRequestRecord.isValid } returns true

            // When
            supervisor.resume(mockRequestRecord, minNextTryTime)

            // Then
            verify { mockRequestRecord.beginRequest(any()) }
            verify { mockRequestRecordRepository.save(mockRequestRecord) }
            verify { mockValidator.validate(mockRequestRecord) }
        }
    }

    @Nested
    @DisplayName("retry() 方法测试")
    inner class RetryTests {

        @Test
        @DisplayName("成功重试请求")
        fun `should retry request successfully`() {
            // Given
            val handler = TestCommandHandler()
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(handler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("retry")
            every { mockRequestRecord.param } returns request
            every { mockRequestRecordRepository.getById(testRequestId) } returns mockRequestRecord
            every { mockRequestRecord.endRequest(any(), any()) } just Runs

            // When
            testSupervisor.retry(testRequestId)

            // Then
            verify { mockRequestRecordRepository.getById(testRequestId) }
            verify { mockValidator.validate(mockRequestRecord) }
            verify { mockRequestRecord.endRequest(any(), "success") }
            verify { mockRequestRecordRepository.save(mockRequestRecord) }
        }

        @Test
        @DisplayName("重试请求时处理器异常应记录异常")
        fun `should record exception when retry request fails`() {
            // Given
            val failingHandler = object : RequestHandler<TestCommandRequest, String> {
                override fun exec(request: TestCommandRequest): String {
                    throw RuntimeException("Retry handler error")
                }
            }
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(failingHandler),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("retry-error")
            val recordedException = slot<Throwable>()
            every { mockRequestRecord.param } returns request
            every { mockRequestRecordRepository.getById(testRequestId) } returns mockRequestRecord
            every { mockRequestRecord.occurredException(any(), capture(recordedException)) } just Runs

            // When & Then
            val thrownException = assertThrows<RequestDispatchException> {
                testSupervisor.retry(testRequestId)
            }
            assertEquals("Retry handler error", thrownException.cause?.message)
            assertSame(thrownException, recordedException.captured)
            verify { mockRequestRecord.occurredException(any(), any()) }
            verify { mockRequestRecordRepository.save(mockRequestRecord) }
        }
    }

    @Nested
    @DisplayName("RequestManager 接口方法测试")
    inner class RequestManagerTests {

        @Test
        @DisplayName("成功获取需要重试的请求")
        fun `should get requests by next try time successfully`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val maxNextTryTime = LocalDateTime.now().plusHours(1)
            val limit = 10
            val expectedRecords = listOf(mockRequestRecord)
            every {
                mockRequestRecordRepository.getByNextTryTime(
                    testSvcName,
                    maxNextTryTime,
                    limit
                )
            } returns expectedRecords

            // When
            val result = supervisor.getByNextTryTime(maxNextTryTime, limit)

            // Then
            assertEquals(expectedRecords, result)
            verify { mockRequestRecordRepository.getByNextTryTime(testSvcName, maxNextTryTime, limit) }
        }

        @Test
        @DisplayName("成功归档过期请求")
        fun `should archive expired requests successfully`() {
            // Given
            supervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = mockValidator,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val maxExpireAt = LocalDateTime.now().minusDays(1)
            val limit = 100
            val expectedCount = 5
            every {
                mockRequestRecordRepository.archiveByExpireAt(
                    testSvcName,
                    maxExpireAt,
                    limit
                )
            } returns expectedCount

            // When
            val result = supervisor.archiveByExpireAt(maxExpireAt, limit)

            // Then
            assertEquals(expectedCount, result)
            verify { mockRequestRecordRepository.archiveByExpireAt(testSvcName, maxExpireAt, limit) }
        }
    }

    @Nested
    @DisplayName("线程池配置测试")
    inner class ThreadPoolConfigTests {

        @Test
        @DisplayName("自定义ThreadFactory应正确配置")
        fun `should configure custom ThreadFactory correctly`() {
            // Given
            val customThreadFactoryClassName = "java.util.concurrent.Executors\$DefaultThreadFactory"

            // When & Then
            assertDoesNotThrow {
                DefaultRequestSupervisor(
                    requestHandlers = emptyList(),
                    requestInterceptors = emptyList(),
                    validator = null,
                    requestRecordRepository = mockRequestRecordRepository,
                    svcName = testSvcName,
                    threadPoolSize = testThreadPoolSize,
                    threadFactoryClassName = customThreadFactoryClassName
                )
            }
        }

        @Test
        @DisplayName("无效ThreadFactory类名应使用默认配置")
        fun `should use default configuration for invalid ThreadFactory class`() {
            // Given
            val invalidThreadFactoryClassName = "com.invalid.NonExistentThreadFactory"

            // When & Then
            assertDoesNotThrow {
                DefaultRequestSupervisor(
                    requestHandlers = emptyList(),
                    requestInterceptors = emptyList(),
                    validator = null,
                    requestRecordRepository = mockRequestRecordRepository,
                    svcName = testSvcName,
                    threadPoolSize = testThreadPoolSize,
                    threadFactoryClassName = invalidThreadFactoryClassName
                )
            }
        }
    }

    @Nested
    @DisplayName("边界条件和异常情况测试")
    inner class EdgeCasesAndExceptionTests {

        @Test
        @DisplayName("空处理器列表应正常工作")
        fun `should work with empty handler list`() {
            // Given
            val emptySupervisor = DefaultRequestSupervisor(
                requestHandlers = emptyList(),
                requestInterceptors = emptyList(),
                validator = null,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("empty")

            // When & Then
            val exception = assertThrows<RequestDispatchException> {
                emptySupervisor.send(request)
            }
            assertTrue(exception.message!!.contains("No handler found"))
        }

        @Test
        @DisplayName("多个相同类型拦截器应按顺序执行")
        fun `should execute multiple interceptors of same type in order`() {
            // Given
            val executionOrder = mutableListOf<String>()

            val interceptor1 = object : RequestInterceptor<TestCommandRequest, String> {
                override fun preRequest(request: TestCommandRequest) {
                    executionOrder.add("interceptor1-pre")
                }

                override fun postRequest(request: TestCommandRequest, response: String) {
                    executionOrder.add("interceptor1-post")
                }
            }

            val interceptor2 = object : RequestInterceptor<TestCommandRequest, String> {
                override fun preRequest(request: TestCommandRequest) {
                    executionOrder.add("interceptor2-pre")
                }

                override fun postRequest(request: TestCommandRequest, response: String) {
                    executionOrder.add("interceptor2-post")
                }
            }

            val handler = TestCommandHandler()
            val multiInterceptorSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(handler),
                requestInterceptors = listOf(interceptor1, interceptor2),
                validator = null,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("multi-interceptor")

            // When
            multiInterceptorSupervisor.send(request)

            // Then
            assertEquals(
                listOf("interceptor1-pre", "interceptor2-pre", "interceptor1-post", "interceptor2-post"),
                executionOrder
            )
        }

        @Test
        @DisplayName("拦截器异常应中断执行")
        fun `should interrupt execution when interceptor throws exception`() {
            // Given
            val failingInterceptor = object : RequestInterceptor<TestCommandRequest, String> {
                override fun preRequest(request: TestCommandRequest) {
                    throw RuntimeException("Interceptor error")
                }

                override fun postRequest(request: TestCommandRequest, response: String) {}
            }

            val handler = TestCommandHandler()
            val testSupervisor = DefaultRequestSupervisor(
                requestHandlers = listOf(handler),
                requestInterceptors = listOf(failingInterceptor),
                validator = null,
                requestRecordRepository = mockRequestRecordRepository,
                svcName = testSvcName,
                threadPoolSize = testThreadPoolSize,
                threadFactoryClassName = ""
            )
            val request = TestCommandRequest("interceptor-error")

            // When & Then
            val thrownException = assertThrows<RuntimeException> {
                testSupervisor.send(request)
            }
            val requestDispatchException = assertInstanceOf(RequestDispatchException::class.java, thrownException)
            assertEquals("Interceptor error", requestDispatchException.cause?.message)
        }
    }
}
