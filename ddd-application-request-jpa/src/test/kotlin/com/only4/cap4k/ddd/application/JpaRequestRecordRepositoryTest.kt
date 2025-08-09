package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.application.persistence.*
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@DisplayName("JpaRequestRecordRepository仓储实现测试")
class JpaRequestRecordRepositoryTest {

    private lateinit var repository: JpaRequestRecordRepository
    private lateinit var requestJpaRepository: RequestJpaRepository
    private lateinit var archivedRequestJpaRepository: ArchivedRequestJpaRepository
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        requestJpaRepository = mockk()
        archivedRequestJpaRepository = mockk()
        repository = JpaRequestRecordRepository(requestJpaRepository, archivedRequestJpaRepository)
    }

    @Nested
    @DisplayName("创建RequestRecord测试")
    inner class CreateRequestRecordTest {

        @Test
        @DisplayName("应该创建新的RequestRecordImpl实例")
        fun `should create new RequestRecordImpl instance`() {
            // When
            val requestRecord = repository.create()

            // Then
            assertNotNull(requestRecord)
            assertTrue(requestRecord is RequestRecordImpl)
        }

        @Test
        @DisplayName("每次调用create应该返回新的实例")
        fun `should return new instance on each create call`() {
            // When
            val requestRecord1 = repository.create()
            val requestRecord2 = repository.create()

            // Then
            assertNotSame(requestRecord1, requestRecord2)
        }
    }

    @Nested
    @DisplayName("保存RequestRecord测试")
    inner class SaveRequestRecordTest {

        @Test
        @DisplayName("应该保存RequestRecord并更新实例")
        fun `should save request record and update instance`() {
            // Given
            val requestRecord = RequestRecordImpl()
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_REQUEST", testTime, Duration.ofMinutes(10), 3)

            val savedRequest = mockk<Request> {
                every { requestUuid } returns "saved-uuid"
                every { svcName } returns "test-service"
                every { requestType } returns "TEST_REQUEST"
            }

            every { requestJpaRepository.saveAndFlush(any()) } returns savedRequest

            // When
            repository.save(requestRecord)

            // Then
            verify { requestJpaRepository.saveAndFlush(any()) }
            assertEquals(savedRequest, requestRecord.request)
        }

        @Test
        @DisplayName("应该能够保存复杂的RequestRecord")
        fun `should save complex request record`() {
            // Given
            val requestRecord = RequestRecordImpl()
            val items = listOf(
                ProcessOrderRequestParam.OrderItem("product1", 2, 99.99),
                ProcessOrderRequestParam.OrderItem("product2", 1, 49.99)
            )
            val requestParam = ProcessOrderRequestParam("order123", "customer456", 249.97, items)
            requestRecord.init(requestParam, "order-service", "PROCESS_ORDER", testTime, Duration.ofMinutes(15), 3)

            val savedRequest = mockk<Request>()
            every { requestJpaRepository.saveAndFlush(any()) } returns savedRequest

            // When
            repository.save(requestRecord)

            // Then
            verify { requestJpaRepository.saveAndFlush(any()) }
        }
    }

    @Nested
    @DisplayName("根据ID获取RequestRecord测试")
    inner class GetByIdTest {

        @Test
        @DisplayName("应该根据ID成功获取RequestRecord")
        fun `should get request record by id successfully`() {
            // Given
            val requestId = "test-request-id"
            val mockRequest = mockk<Request> {
                every { requestUuid } returns requestId
                every { requestType } returns "TEST_REQUEST"
                every { svcName } returns "test-service"
                every { lastTryTime } returns testTime
                every { requestParam } returns TestRequestParam("test", mapOf("key" to "value"))
            }

            every {
                requestJpaRepository.findOne(any<Specification<Request>>())
            } returns Optional.of(mockRequest)

            // When
            val requestRecord = repository.getById(requestId)

            // Then
            assertNotNull(requestRecord)
            assertTrue(requestRecord is RequestRecordImpl)
            val impl = requestRecord as RequestRecordImpl
            assertEquals(mockRequest, impl.request)

            verify {
                requestJpaRepository.findOne(any<Specification<Request>>())
            }
        }

        @Test
        @DisplayName("当请求不存在时应该抛出DomainException")
        fun `should throw DomainException when request not found`() {
            // Given
            val requestId = "non-existent-id"
            every {
                requestJpaRepository.findOne(any<Specification<Request>>())
            } returns Optional.empty()

            // When & Then
            val exception = assertThrows<DomainException> {
                repository.getById(requestId)
            }
            assertEquals("RequestRecord not found", exception.message)
        }

        @Test
        @DisplayName("应该使用正确的查询条件")
        fun `should use correct query specification`() {
            // Given
            val requestId = "test-request-id"
            val mockRequest = mockk<Request>()
            val specificationSlot = slot<Specification<Request>>()

            every {
                requestJpaRepository.findOne(capture(specificationSlot))
            } returns Optional.of(mockRequest)

            // When
            repository.getById(requestId)

            // Then
            verify {
                requestJpaRepository.findOne(any<Specification<Request>>())
            }
        }
    }

    @Nested
    @DisplayName("根据下次尝试时间获取RequestRecord测试")
    inner class GetByNextTryTimeTest {

        @Test
        @DisplayName("应该获取需要重试的请求记录")
        fun `should get request records for retry`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10

            val mockRequests = listOf(
                createMockRequest("request1", Request.RequestState.INIT),
                createMockRequest("request2", Request.RequestState.EXECUTING),
                createMockRequest("request3", Request.RequestState.EXCEPTION)
            )
            val mockPage = PageImpl(mockRequests)

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val requestRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, requestRecords.size)
            requestRecords.forEach { requestRecord ->
                assertTrue(requestRecord is RequestRecordImpl)
            }

            verify {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Request.F_NEXT_TRY_TIME))
                )
            }
        }

        @Test
        @DisplayName("应该返回空列表当没有符合条件的请求时")
        fun `should return empty list when no requests match criteria`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10
            val emptyPage = PageImpl<Request>(emptyList())

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            val requestRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertTrue(requestRecords.isEmpty())
        }

        @Test
        @DisplayName("应该使用正确的分页参数")
        fun `should use correct pagination parameters`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5
            val emptyPage = PageImpl<Request>(emptyList())

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            verify {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Request.F_NEXT_TRY_TIME))
                )
            }
        }
    }

    @Nested
    @DisplayName("归档过期请求测试")
    inner class ArchiveByExpireAtTest {

        @Test
        @DisplayName("应该成功归档过期的请求")
        fun `should archive expired requests successfully`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10

            val mockRequests = listOf(
                createMockRequest("request1", Request.RequestState.EXECUTED),
                createMockRequest("request2", Request.RequestState.CANCEL),
                createMockRequest("request3", Request.RequestState.EXPIRED)
            )
            val mockPage = PageImpl(mockRequests)

            val mockArchivedRequests = listOf(
                mockk<ArchivedRequest>(),
                mockk<ArchivedRequest>(),
                mockk<ArchivedRequest>()
            )

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            every { archivedRequestJpaRepository.saveAll(any<List<ArchivedRequest>>()) } returns mockArchivedRequests
            every { requestJpaRepository.deleteAllInBatch(any<List<Request>>()) } just Runs

            // When
            val archivedCount = repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            assertEquals(3, archivedCount)

            verify { archivedRequestJpaRepository.saveAll(any<List<ArchivedRequest>>()) }
            verify { requestJpaRepository.deleteAllInBatch(mockRequests) }
        }

        @Test
        @DisplayName("当没有请求需要归档时应该返回0")
        fun `should return 0 when no requests to archive`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10
            val emptyPage = PageImpl<Request>(emptyList())

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            val archivedCount = repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            assertEquals(0, archivedCount)

            verify(exactly = 0) { archivedRequestJpaRepository.saveAll(any<List<ArchivedRequest>>()) }
            verify(exactly = 0) { requestJpaRepository.deleteAllInBatch(any<List<Request>>()) }
        }

        @Test
        @DisplayName("应该使用正确的查询条件查找需要归档的请求")
        fun `should use correct criteria to find requests for archiving`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10
            val emptyPage = PageImpl<Request>(emptyList())

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            verify {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Request.F_NEXT_TRY_TIME))
                )
            }
        }
    }

    @Nested
    @DisplayName("迁移方法测试")
    inner class MigrateTest {

        @Test
        @DisplayName("应该成功迁移请求到归档表")
        fun `should migrate requests to archive table successfully`() {
            // Given
            val requests = listOf(
                createMockRequest("request1", Request.RequestState.EXECUTED),
                createMockRequest("request2", Request.RequestState.CANCEL)
            )
            val archivedRequests = listOf(mockk<ArchivedRequest>(), mockk<ArchivedRequest>())

            every { archivedRequestJpaRepository.saveAll(archivedRequests) } returns archivedRequests
            every { requestJpaRepository.deleteAllInBatch(requests) } returns Unit

            // When
            repository.migrate(requests, archivedRequests)

            // Then
            verify { archivedRequestJpaRepository.saveAll(archivedRequests) }
            verify { requestJpaRepository.deleteAllInBatch(requests) }
        }

        @Test
        @DisplayName("应该能够处理空列表")
        fun `should handle empty lists`() {
            // Given
            val emptyRequests = emptyList<Request>()
            val emptyArchivedRequests = emptyList<ArchivedRequest>()

            every { archivedRequestJpaRepository.saveAll(emptyArchivedRequests) } returns emptyArchivedRequests
            every { requestJpaRepository.deleteAllInBatch(emptyRequests) } returns Unit

            // When & Then
            assertDoesNotThrow {
                repository.migrate(emptyRequests, emptyArchivedRequests)
            }

            verify { archivedRequestJpaRepository.saveAll(emptyArchivedRequests) }
            verify { requestJpaRepository.deleteAllInBatch(emptyRequests) }
        }

        @Test
        @DisplayName("当保存归档请求失败时应该抛出异常")
        fun `should throw exception when saving archived requests fails`() {
            // Given
            val requests = listOf(createMockRequest("request1", Request.RequestState.EXECUTED))
            val archivedRequests = listOf(mockk<ArchivedRequest>())

            every {
                archivedRequestJpaRepository.saveAll(archivedRequests)
            } throws RuntimeException("Database error")

            // When & Then
            assertThrows<RuntimeException> {
                repository.migrate(requests, archivedRequests)
            }

            verify { archivedRequestJpaRepository.saveAll(archivedRequests) }
            verify(exactly = 0) { requestJpaRepository.deleteAllInBatch(any()) }
        }
    }

    @Nested
    @DisplayName("集成测试")
    inner class IntegrationTest {

        @Test
        @DisplayName("完整的请求生命周期测试")
        fun `should handle complete request lifecycle`() {
            // Given - 创建请求
            val requestRecord = repository.create()
            val requestParam = CreateUserRequestParam("john", "john@test.com", "ADMIN")
            requestRecord.init(requestParam, "user-service", "CREATE_USER", testTime, Duration.ofHours(1), 3)

            val savedRequest = mockk<Request>(relaxed = true) {
                every { id } returns 1L
                every { requestUuid } returns "saved-request-id"
                every { svcName } returns "user-service"
                every { requestType } returns "CREATE_USER"
                every { lastTryTime } returns testTime
                every { nextTryTime } returns testTime.plusMinutes(1)
                every { requestState } returns Request.RequestState.INIT
                every { param } returns """{"username":"john","email":"john@test.com","role":"ADMIN"}"""
                every { paramType } returns "CreateUserRequestParam"
                every { result } returns """{"success":true,"userId":"12345"}"""
                every { resultType } returns "CreateUserResult"
                every { exception } returns null
                every { expireAt } returns testTime.plusHours(1)
                every { createAt } returns testTime.minusHours(1)
                every { tryTimes } returns 3
                every { triedTimes } returns 0
                every { requestResult } returns null
                every { version } returns 1
            }

            every { requestJpaRepository.saveAndFlush(any()) } returns savedRequest
            every {
                requestJpaRepository.findOne(any<Specification<Request>>())
            } returns Optional.of(savedRequest)

            // When - 保存请求
            repository.save(requestRecord)

            // Then - 验证能够重新获取
            val retrievedRecord = repository.getById("saved-request-id")
            assertNotNull(retrievedRecord)
            assertEquals("CREATE_USER", retrievedRecord.type)
        }

        @Test
        @DisplayName("批量处理请求测试")
        fun `should handle batch processing of requests`() {
            // Given
            val svcName = "batch-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5

            val mockRequests = (1..3).map { i ->
                createMockRequest("batch-request-$i", Request.RequestState.INIT)
            }
            val mockPage = PageImpl(mockRequests)

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val requestRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, requestRecords.size)
            requestRecords.forEachIndexed { index, requestRecord ->
                assertTrue(requestRecord is RequestRecordImpl)
                val impl = requestRecord as RequestRecordImpl
                assertEquals(mockRequests[index], impl.request)
            }
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    inner class ErrorHandlingTest {

        @Test
        @DisplayName("应该处理数据库连接错误")
        fun `should handle database connection errors`() {
            // Given
            every {
                requestJpaRepository.saveAndFlush(any())
            } throws RuntimeException("Database connection failed")

            val requestRecord = RequestRecordImpl()
            val requestParam = TestRequestParam("test", mapOf("key" to "value"))
            requestRecord.init(requestParam, "test-service", "TEST_REQUEST", testTime, Duration.ofMinutes(10), 3)

            // When & Then
            assertThrows<RuntimeException> {
                repository.save(requestRecord)
            }
        }

        @Test
        @DisplayName("应该处理查询超时")
        fun `should handle query timeout`() {
            // Given
            every {
                requestJpaRepository.findOne(any<Specification<Request>>())
            } throws RuntimeException("Query timeout")

            // When & Then
            assertThrows<RuntimeException> {
                repository.getById("any-id")
            }
        }

        @Test
        @DisplayName("应该处理归档过程中的异常")
        fun `should handle exceptions during archiving`() {
            // Given
            val mockRequests = listOf(createMockRequest("request1", Request.RequestState.EXECUTED))
            val mockPage = PageImpl(mockRequests)

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            every {
                archivedRequestJpaRepository.saveAll(any<List<ArchivedRequest>>())
            } throws RuntimeException("Archive table full")

            // When & Then
            assertThrows<RuntimeException> {
                repository.archiveByExpireAt("test-service", testTime.minusDays(1), 10)
            }
        }
    }

    @Nested
    @DisplayName("性能测试")
    inner class PerformanceTest {

        @Test
        @DisplayName("大批量请求查询性能测试")
        fun `should handle large batch request query efficiently`() {
            // Given
            val batchSize = 1000
            val largeRequestList = (1..batchSize).map { createMockRequest("request$it", Request.RequestState.INIT) }
            val mockPage = PageImpl(largeRequestList)

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val startTime = System.currentTimeMillis()
            val requestRecords = repository.getByNextTryTime("test-service", testTime.plusHours(1), batchSize)
            val duration = System.currentTimeMillis() - startTime

            // Then
            assertEquals(batchSize, requestRecords.size)
            assertTrue(duration < 5000) // 应该在5秒内完成
        }

        @Test
        @DisplayName("大批量请求归档性能测试")
        fun `should handle large batch request archiving efficiently`() {
            // Given
            val batchSize = 1000
            val largeRequestList = (1..batchSize).map { createMockRequest("request$it", Request.RequestState.EXECUTED) }
            val mockPage = PageImpl(largeRequestList)

            every {
                requestJpaRepository.findAll(
                    any<Specification<Request>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            every { archivedRequestJpaRepository.saveAll(any<List<ArchivedRequest>>()) } returns emptyList()
            every { requestJpaRepository.deleteAllInBatch(any<List<Request>>()) } just Runs

            // When
            val startTime = System.currentTimeMillis()
            val archivedCount = repository.archiveByExpireAt("test-service", testTime.minusDays(1), batchSize)
            val duration = System.currentTimeMillis() - startTime

            // Then
            assertEquals(batchSize, archivedCount)
            assertTrue(duration < 3000) // 应该在3秒内完成
        }
    }

    private fun createMockRequest(requestId: String, state: Request.RequestState): Request {
        return mockk<Request> {
            every { id } returns 1L
            every { requestUuid } returns requestId
            every { requestState } returns state
            every { svcName } returns "test-service"
            every { lastTryTime } returns testTime
            every { nextTryTime } returns testTime.plusMinutes(1)
            every { requestType } returns "TEST_REQUEST"
            every { requestParam } returns TestRequestParam("test", mapOf("key" to "value"))
            every { param } returns """{"action":"test","data":{"key":"value"},"timestamp":123456789}"""
            every { paramType } returns "TestRequestParam"
            every { result } returns """{"success":true,"message":"completed"}"""
            every { resultType } returns "TestResult"
            every { exception } returns null
            every { expireAt } returns testTime.plusHours(1)
            every { createAt } returns testTime.minusHours(1)
            every { tryTimes } returns 3
            every { triedTimes } returns 0
            every { requestResult } returns null
            every { version } returns 1
        }
    }
}
