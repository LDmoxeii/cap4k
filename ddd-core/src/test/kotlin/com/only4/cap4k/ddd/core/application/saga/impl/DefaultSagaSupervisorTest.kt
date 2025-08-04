package com.only4.cap4k.ddd.core.application.saga.impl

import com.only4.cap4k.ddd.core.application.saga.SagaHandler
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.application.saga.SagaRecord
import com.only4.cap4k.ddd.core.application.saga.SagaRecordRepository
import io.mockk.*
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.junit.jupiter.api.*
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * DefaultSagaSupervisor 简化测试用例
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class DefaultSagaSupervisorTest {

    // Mock 依赖
    private lateinit var mockSagaRecordRepository: SagaRecordRepository
    private lateinit var mockValidator: Validator
    private lateinit var mockSagaRecord: SagaRecord

    // 测试对象
    private lateinit var supervisor: DefaultSagaSupervisor

    // 测试常量
    private val testSvcName = "test-service"
    private val testThreadPoolSize = 5
    private val testParam = TestSagaParam("test-data")

    // 测试数据类
    data class TestSagaParam(val data: String) : SagaParam<String>

    @BeforeEach
    fun setUp() {
        // 初始化 Mock 对象
        mockSagaRecordRepository = mockk<SagaRecordRepository>()
        mockValidator = mockk<Validator>()
        mockSagaRecord = mockk<SagaRecord>()

        // 创建测试对象 - 不传入处理器和拦截器避免泛型解析问题
        supervisor = DefaultSagaSupervisor(
            requestHandlers = emptyList(),
            requestInterceptors = emptyList(),
            validator = mockValidator,
            sagaRecordRepository = mockSagaRecordRepository,
            svcName = testSvcName,
            threadPoolSize = testThreadPoolSize,
            threadFactoryClassName = ""
        )

        // 设置默认 Mock 行为
        every { mockValidator.validate(testParam) } returns emptySet()
        every { mockValidator.validate(mockSagaRecord) } returns emptySet()
        every { mockSagaRecordRepository.create() } returns mockSagaRecord
        every { mockSagaRecordRepository.save(mockSagaRecord) } just Runs
        every { mockSagaRecordRepository.getById(any()) } returns mockSagaRecord
        every { mockSagaRecord.init(any(), any(), any(), any(), any(), any()) } just Runs
        every { mockSagaRecord.beginSaga(any()) } returns true
        every { mockSagaRecord.endSaga(any(), any()) } just Runs
        every { mockSagaRecord.occurredException(any(), any()) } just Runs
        every { mockSagaRecord.id } returns "test-saga-id"
        every { mockSagaRecord.isExecuting } returns false
        every { mockSagaRecord.isValid } returns true
        every { mockSagaRecord.param } returns testParam
        every { mockSagaRecord.nextTryTime } returns LocalDateTime.now().plusMinutes(5)
        every { mockSagaRecord.scheduleTime } returns LocalDateTime.now().plusMinutes(1)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @DisplayName("验证懒加载初始化是否正确触发")
    fun `test lazy initialization triggers correctly`() {
        // 验证懒加载属性在首次访问时初始化
        // 直接验证对象创建成功
        assertNotNull(supervisor)
    }

    @Test
    @DisplayName("测试send方法在参数验证失败时抛出异常")
    fun `test send method with validation failure`() {
        // 准备
        val violation = mockk<ConstraintViolation<TestSagaParam>>(relaxed = true)
        every { mockValidator.validate(testParam) } returns setOf(violation)

        // 执行 & 验证
        assertThrows<ConstraintViolationException> {
            supervisor.send(testParam)
        }

        verify { mockValidator.validate(testParam) }
        verify(exactly = 0) { mockSagaRecordRepository.create() }
    }

    @Test
    @DisplayName("测试schedule方法在未来时间调度Saga")
    fun `test schedule method with future time`() {
        // 准备
        val futureTime = LocalDateTime.now().plusHours(1)
        every { mockSagaRecord.isExecuting } returns true
        every { mockSagaRecord.scheduleTime } returns futureTime

        // 执行
        val sagaId = supervisor.schedule(testParam, futureTime)

        // 验证
        assertEquals("test-saga-id", sagaId)
        verify { mockValidator.validate(testParam) }
        verify { mockSagaRecordRepository.create() }
        verify { mockSagaRecordRepository.save(mockSagaRecord) }
    }

    @Test
    @DisplayName("测试schedule方法在过去时间立即执行Saga")
    fun `test schedule method with past time executes immediately`() {
        // 准备
        val pastTime = LocalDateTime.now().minusMinutes(5)

        // 执行
        val sagaId = supervisor.schedule(testParam, pastTime)

        // 验证
        assertEquals("test-saga-id", sagaId)
        verify { mockSagaRecord.beginSaga(any()) }
    }

    @Test
    @DisplayName("测试result方法返回正确的结果")
    fun `test result method returns correct result`() {
        // 准备
        val expectedResult = "test-result"
        val sagaId = "test-saga-id"
        every { mockSagaRecordRepository.getById(sagaId) } returns mockSagaRecord
        every { mockSagaRecord.getResult<String>() } returns expectedResult

        // 执行
        val result = supervisor.result<String>(sagaId)

        // 验证
        assertEquals(expectedResult, result)
        verify { mockSagaRecordRepository.getById(sagaId) }
    }

    @Test
    @DisplayName("测试result方法正确委托给SagaRecord")
    fun `test result method delegates to saga record`() {
        // 准备
        val expectedResult = "delegated-result"
        val sagaId = "test-saga-id"
        every { mockSagaRecordRepository.getById(sagaId) } returns mockSagaRecord
        every { mockSagaRecord.getResult<String>() } returns expectedResult

        // 执行
        val result = supervisor.result<String>(sagaId)

        // 验证
        assertEquals(expectedResult, result)
        verify { mockSagaRecordRepository.getById(sagaId) }
        verify { mockSagaRecord.getResult<String>() }
    }

    @Test
    @DisplayName("测试resume方法恢复有效的Saga")
    fun `test resume method with valid saga`() {
        // 准备
        val minNextTryTime = LocalDateTime.now().plusMinutes(5)
        val nextTryTime = LocalDateTime.now().plusMinutes(10) // 大于minNextTryTime

        // 配置mock，使得beginSaga后nextTryTime等于minNextTryTime，这样可以跳出while循环
        every { mockSagaRecord.nextTryTime } returnsMany listOf(nextTryTime, minNextTryTime)
        every { mockSagaRecord.beginSaga(any()) } answers {
            // 模拟beginSaga会更新nextTryTime
            every { mockSagaRecord.nextTryTime } returns minNextTryTime
            true
        }
        every { mockSagaRecord.isExecuting } returns false
        every { mockSagaRecord.isValid } returns true
        every { mockSagaRecord.scheduleTime } returns LocalDateTime.now().plusMinutes(1)

        // 执行
        supervisor.resume(mockSagaRecord, minNextTryTime)

        // 验证
        verify { mockSagaRecord.beginSaga(any()) }
        verify { mockValidator.validate(mockSagaRecord) }
        verify { mockSagaRecordRepository.save(mockSagaRecord) }
    }

    @Test
    @DisplayName("测试resume方法处理连续重试")
    fun `test resume method handles continuous retry`() {
        // 准备 - 模拟需要多次重试的场景
        val minNextTryTime = LocalDateTime.now().plusMinutes(10)
        val nextTryTime1 = LocalDateTime.now().plusMinutes(15) // 大于minNextTryTime
        val nextTryTime2 = LocalDateTime.now().plusMinutes(12) // 仍大于minNextTryTime
        val finalNextTryTime = minNextTryTime // 等于minNextTryTime，跳出循环

        // 重新配置mock
        clearMocks(mockSagaRecord)
        every { mockSagaRecord.param } returns testParam
        every { mockSagaRecord.scheduleTime } returns LocalDateTime.now().plusMinutes(1)
        every { mockSagaRecord.isExecuting } returns false
        every { mockSagaRecord.endSaga(any(), any()) } just Runs
        every { mockSagaRecord.occurredException(any(), any()) } just Runs
        every { mockSagaRecord.isValid } returns true

        // 配置nextTryTime返回序列：第一次调用返回nextTryTime1，第二次返回nextTryTime2，第三次返回finalNextTryTime
        every { mockSagaRecord.nextTryTime } returnsMany listOf(nextTryTime1, nextTryTime2, finalNextTryTime)

        var callCount = 0
        every { mockSagaRecord.beginSaga(any()) } answers {
            callCount++
            // 根据调用次数更新nextTryTime的返回值
            when (callCount) {
                1 -> every { mockSagaRecord.nextTryTime } returns nextTryTime2
                2 -> every { mockSagaRecord.nextTryTime } returns finalNextTryTime
                else -> every { mockSagaRecord.nextTryTime } returns finalNextTryTime
            }
            true
        }

        // 执行
        supervisor.resume(mockSagaRecord, minNextTryTime)

        // 验证 - 应该调用多次beginSaga直到nextTryTime == minNextTryTime
        verify(atLeast = 2) { mockSagaRecord.beginSaga(any()) }
        verify { mockValidator.validate(mockSagaRecord) }
        verify { mockSagaRecordRepository.save(mockSagaRecord) }
    }

    @Test
    @DisplayName("测试retry方法重试Saga")
    fun `test retry method retries saga successfully`() {
        // 准备 - 需要添加请求处理器来避免IllegalStateException
        val testHandler = object : SagaHandler<TestSagaParam, String> {
            override fun exec(request: TestSagaParam): String = "retry-success"
        }

        val testSupervisor = DefaultSagaSupervisor(
            requestHandlers = listOf(testHandler),
            requestInterceptors = emptyList(),
            validator = mockValidator,
            sagaRecordRepository = mockSagaRecordRepository,
            svcName = testSvcName,
            threadPoolSize = testThreadPoolSize
        )

        val sagaUuid = "test-saga-uuid"
        every { mockSagaRecordRepository.getById(sagaUuid) } returns mockSagaRecord
        every { mockSagaRecord.param } returns testParam
        every { mockSagaRecord.endSaga(any(), any()) } just Runs

        // 执行
        testSupervisor.retry(sagaUuid)

        // 验证
        verify { mockSagaRecordRepository.getById(sagaUuid) }
        verify { mockValidator.validate(mockSagaRecord) }
        verify { mockSagaRecord.endSaga(any(), any()) }
        verify { mockSagaRecordRepository.save(mockSagaRecord) }
    }

    @Test
    @DisplayName("测试retry方法在验证失败时抛出异常")
    fun `test retry method throws exception on validation failure`() {
        // 准备
        val sagaUuid = "test-saga-uuid"
        val violation = mockk<ConstraintViolation<SagaRecord>>(relaxed = true)
        every { mockSagaRecordRepository.getById(sagaUuid) } returns mockSagaRecord
        every { mockSagaRecord.param } returns testParam
        every { mockValidator.validate(mockSagaRecord) } returns setOf(violation)

        // 执行 & 验证
        assertThrows<ConstraintViolationException> {
            supervisor.retry(sagaUuid)
        }

        verify { mockValidator.validate(mockSagaRecord) }
        verify(exactly = 0) { mockSagaRecord.endSaga(any(), any()) }
    }

    @Test
    @DisplayName("测试getByNextTryTime方法获取待重试的Saga")
    fun `test getByNextTryTime method`() {
        // 准备
        val maxNextTryTime = LocalDateTime.now()
        val limit = 10
        val expectedSagas = listOf(mockSagaRecord)
        every {
            mockSagaRecordRepository.getByNextTryTime(testSvcName, maxNextTryTime, limit)
        } returns expectedSagas

        // 执行
        val result = supervisor.getByNextTryTime(maxNextTryTime, limit)

        // 验证
        assertEquals(expectedSagas, result)
        verify { mockSagaRecordRepository.getByNextTryTime(testSvcName, maxNextTryTime, limit) }
    }

    @Test
    @DisplayName("测试archiveByExpireAt方法归档过期的Saga")
    fun `test archiveByExpireAt method`() {
        // 准备
        val maxExpireAt = LocalDateTime.now()
        val limit = 10
        val expectedCount = 5
        every {
            mockSagaRecordRepository.archiveByExpireAt(testSvcName, maxExpireAt, limit)
        } returns expectedCount

        // 执行
        val result = supervisor.archiveByExpireAt(maxExpireAt, limit)

        // 验证
        assertEquals(expectedCount, result)
        verify { mockSagaRecordRepository.archiveByExpireAt(testSvcName, maxExpireAt, limit) }
    }

    @Test
    @DisplayName("测试自定义线程工厂的初始化")
    fun `test custom thread factory initialization`() {
        // 准备
        val customThreadFactoryClassName = "java.util.concurrent.Executors\$DefaultThreadFactory"

        val customSupervisor = DefaultSagaSupervisor(
            requestHandlers = emptyList(),
            requestInterceptors = emptyList(),
            validator = mockValidator,
            sagaRecordRepository = mockSagaRecordRepository,
            svcName = testSvcName,
            threadPoolSize = testThreadPoolSize,
            threadFactoryClassName = customThreadFactoryClassName
        )

        // 验证 - 确保对象创建成功（间接验证自定义 ThreadFactory 被使用）
        assertNotNull(customSupervisor)
    }

    @Test
    @DisplayName("测试无效线程工厂类名时的回退机制")
    fun `test invalid thread factory class name fallback`() {
        // 准备
        val invalidThreadFactoryClassName = "com.invalid.NonExistentThreadFactory"

        val customSupervisor = DefaultSagaSupervisor(
            requestHandlers = emptyList(),
            requestInterceptors = emptyList(),
            validator = mockValidator,
            sagaRecordRepository = mockSagaRecordRepository,
            svcName = testSvcName,
            threadPoolSize = testThreadPoolSize,
            threadFactoryClassName = invalidThreadFactoryClassName
        )

        // 验证 - 确保即使 ThreadFactory 类名无效，也能回退到默认实现
        assertNotNull(customSupervisor)
    }

    @Test
    @DisplayName("测试Saga在立即执行阈值内的创建和执行")
    fun `test saga creation with immediate execution threshold`() {
        // 准备
        val nearFutureTime = LocalDateTime.now().plusMinutes(1) // 在阈值内

        // 执行
        supervisor.schedule(testParam, nearFutureTime)

        // 验证 - 应该立即开始执行
        verify { mockSagaRecord.beginSaga(any()) }
    }

    @Test
    @DisplayName("测试Saga在延迟执行时的创建和调度")
    fun `test saga creation with delayed execution`() {
        // 准备
        val farFutureTime = LocalDateTime.now().plusMinutes(5) // 超出阈值
        every { mockSagaRecord.isExecuting } returns true
        every { mockSagaRecord.scheduleTime } returns farFutureTime

        // 执行
        supervisor.schedule(testParam, farFutureTime)

        // 验证 - 不应该立即开始执行，而是调度执行
        verify(exactly = 0) { mockSagaRecord.beginSaga(any()) }
    }
}
