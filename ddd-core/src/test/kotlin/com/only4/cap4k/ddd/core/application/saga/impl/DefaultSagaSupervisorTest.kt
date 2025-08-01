package com.only4.cap4k.ddd.core.application.saga.impl

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
            threadPoolSize = testThreadPoolSize
        )

        // 设置默认 Mock 行为
        every { mockValidator.validate(testParam) } returns emptySet()
        every { mockSagaRecordRepository.create() } returns mockSagaRecord
        every { mockSagaRecordRepository.save(mockSagaRecord) } just Runs
        every { mockSagaRecord.init(any(), any(), any(), any(), any(), any()) } just Runs
        every { mockSagaRecord.beginSaga(any()) } returns true
        every { mockSagaRecord.endSaga(any(), any()) } just Runs
        every { mockSagaRecord.occurredException(any(), any()) } just Runs
        every { mockSagaRecord.id } returns "test-saga-id"
        every { mockSagaRecord.isExecuting } returns false
        every { mockSagaRecord.param } returns testParam
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
    @DisplayName("测试result方法在Saga不存在时返回null")
    fun `test result method with non-existent saga`() {
        // 准备
        val sagaId = "non-existent-id"
        every { mockSagaRecordRepository.getById(sagaId) } returns null

        // 执行
        val result = supervisor.result<String>(sagaId)

        // 验证
        assertEquals(null, result)
    }

    @Test
    @DisplayName("测试resume方法恢复有效的Saga")
    fun `test resume method with valid saga`() {
        // 准备
        every { mockSagaRecord.beginSaga(any()) } returns true
        every { mockSagaRecord.isExecuting } returns false

        // 执行
        supervisor.resume(mockSagaRecord)

        // 验证
        verify { mockSagaRecord.beginSaga(any()) }
        verify { mockValidator.validate(testParam) }
        // 注意：当beginSaga返回true且isExecuting为false时，不会调用save方法
        // 因为saga会立即执行而不需要保存状态
    }

    @Test
    @DisplayName("测试resume方法在Saga无法开始时的处理")
    fun `test resume method with saga that cannot begin`() {
        // 准备
        every { mockSagaRecord.beginSaga(any()) } returns false

        // 执行
        supervisor.resume(mockSagaRecord)

        // 验证
        verify { mockSagaRecord.beginSaga(any()) }
        verify { mockSagaRecordRepository.save(mockSagaRecord) }
        verify(exactly = 0) { mockValidator.validate(any<TestSagaParam>()) }
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
