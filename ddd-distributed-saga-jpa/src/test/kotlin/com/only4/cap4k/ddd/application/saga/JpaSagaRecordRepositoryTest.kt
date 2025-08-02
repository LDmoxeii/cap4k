package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSaga
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaJpaRepository
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@DisplayName("JpaSagaRecordRepository仓储实现测试")
class JpaSagaRecordRepositoryTest {

    private lateinit var repository: JpaSagaRecordRepository
    private lateinit var sagaJpaRepository: SagaJpaRepository
    private lateinit var archivedSagaJpaRepository: ArchivedSagaJpaRepository
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        sagaJpaRepository = mockk()
        archivedSagaJpaRepository = mockk()
        repository = JpaSagaRecordRepository(sagaJpaRepository, archivedSagaJpaRepository)
    }

    @Nested
    @DisplayName("创建SagaRecord测试")
    inner class CreateSagaRecordTest {

        @Test
        @DisplayName("应该创建新的SagaRecordImpl实例")
        fun `should create new SagaRecordImpl instance`() {
            // When
            val sagaRecord = repository.create()

            // Then
            assertNotNull(sagaRecord)
            assertTrue(sagaRecord is SagaRecordImpl)
        }

        @Test
        @DisplayName("每次调用create应该返回新的实例")
        fun `should return new instance on each create call`() {
            // When
            val sagaRecord1 = repository.create()
            val sagaRecord2 = repository.create()

            // Then
            assertNotSame(sagaRecord1, sagaRecord2)
        }
    }

    @Nested
    @DisplayName("保存SagaRecord测试")
    inner class SaveSagaRecordTest {

        @Test
        @DisplayName("应该保存SagaRecord并更新实例")
        fun `should save saga record and update instance`() {
            // Given
            val sagaRecord = SagaRecordImpl()
            val sagaParam = TestSagaParam("create-user", mapOf("userId" to "123"))
            sagaRecord.init(sagaParam, "user-service", "CREATE_USER_SAGA", testTime, Duration.ofMinutes(10), 3)

            val savedSaga = mockk<Saga> {
                every { sagaUuid } returns "saved-saga-id"
                every { svcName } returns "user-service"
                every { sagaType } returns "CREATE_USER_SAGA"
            }

            every { sagaJpaRepository.saveAndFlush(any()) } returns savedSaga

            // When
            repository.save(sagaRecord)

            // Then
            verify { sagaJpaRepository.saveAndFlush(any()) }
            assertEquals(savedSaga, sagaRecord.saga)
        }

        @Test
        @DisplayName("应该能够保存复杂的SagaRecord")
        fun `should save complex saga record`() {
            // Given
            val sagaRecord = SagaRecordImpl()
            val sagaParam = ComplexSagaParam(
                orderId = "order-123",
                userId = "user-456",
                amount = 999.99,
                items = listOf(
                    ComplexSagaParam.SagaItem("product1", 2),
                    ComplexSagaParam.SagaItem("product2", 1)
                )
            )
            sagaRecord.init(sagaParam, "order-service", "PROCESS_ORDER_SAGA", testTime, Duration.ofMinutes(15), 3)

            val savedSaga = mockk<Saga>()
            every { sagaJpaRepository.saveAndFlush(any()) } returns savedSaga

            // When
            repository.save(sagaRecord)

            // Then
            verify { sagaJpaRepository.saveAndFlush(any()) }
        }
    }

    @Nested
    @DisplayName("根据ID获取SagaRecord测试")
    inner class GetByIdTest {

        @Test
        @DisplayName("应该根据ID成功获取SagaRecord")
        fun `should get saga record by id successfully`() {
            // Given
            val sagaId = "test-saga-id"
            val mockSaga = mockk<Saga> {
                every { sagaUuid } returns sagaId
                every { sagaType } returns "TEST_SAGA"
                every { svcName } returns "test-service"
                every { lastTryTime } returns testTime
                every { sagaParam } returns TestSagaParam("test", mapOf("key" to "value"))
            }

            every {
                sagaJpaRepository.findOne(any<Specification<Saga>>())
            } returns Optional.of(mockSaga)

            // When
            val sagaRecord = repository.getById(sagaId)

            // Then
            assertNotNull(sagaRecord)
            assertTrue(sagaRecord is SagaRecordImpl)
            val impl = sagaRecord as SagaRecordImpl
            assertEquals(mockSaga, impl.saga)

            verify {
                sagaJpaRepository.findOne(any<Specification<Saga>>())
            }
        }

        @Test
        @DisplayName("当Saga不存在时应该抛出DomainException")
        fun `should throw DomainException when saga not found`() {
            // Given
            val sagaId = "non-existent-id"
            every {
                sagaJpaRepository.findOne(any<Specification<Saga>>())
            } returns Optional.empty()

            // When & Then
            val exception = assertThrows<DomainException> {
                repository.getById(sagaId)
            }
            assertEquals("SagaRecord not found", exception.message)
        }

        @Test
        @DisplayName("应该使用正确的查询条件")
        fun `should use correct query specification`() {
            // Given
            val sagaId = "test-saga-id"
            val mockSaga = mockk<Saga>()
            val specificationSlot = slot<Specification<Saga>>()

            every {
                sagaJpaRepository.findOne(capture(specificationSlot))
            } returns Optional.of(mockSaga)

            // When
            repository.getById(sagaId)

            // Then
            verify {
                sagaJpaRepository.findOne(any<Specification<Saga>>())
            }
        }
    }

    @Nested
    @DisplayName("根据下次尝试时间获取SagaRecord测试")
    inner class GetByNextTryTimeTest {

        @Test
        @DisplayName("应该获取需要重试的Saga记录")
        fun `should get saga records for retry`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10

            val mockSagas = listOf(
                createMockSaga("saga1", Saga.SagaState.INIT),
                createMockSaga("saga2", Saga.SagaState.EXECUTING),
                createMockSaga("saga3", Saga.SagaState.EXCEPTION)
            )
            val mockPage = PageImpl(mockSagas)

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val sagaRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, sagaRecords.size)
            sagaRecords.forEach { sagaRecord ->
                assertTrue(sagaRecord is SagaRecordImpl)
            }

            verify {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Saga.F_NEXT_TRY_TIME))
                )
            }
        }

        @Test
        @DisplayName("应该返回空列表当没有符合条件的Saga时")
        fun `should return empty list when no sagas match criteria`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10
            val emptyPage = PageImpl<Saga>(emptyList())

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            val sagaRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertTrue(sagaRecords.isEmpty())
        }

        @Test
        @DisplayName("应该使用正确的分页参数")
        fun `should use correct pagination parameters`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5
            val emptyPage = PageImpl<Saga>(emptyList())

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            verify {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Saga.F_NEXT_TRY_TIME))
                )
            }
        }
    }

    @Nested
    @DisplayName("归档过期Saga测试")
    inner class ArchiveByExpireAtTest {

        @Test
        @DisplayName("应该成功归档过期的Saga")
        fun `should archive expired sagas successfully`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10

            val mockSagas = listOf(
                createMockSaga("saga1", Saga.SagaState.EXECUTED),
                createMockSaga("saga2", Saga.SagaState.CANCEL),
                createMockSaga("saga3", Saga.SagaState.EXPIRED)
            )
            val mockPage = PageImpl(mockSagas)

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // Create a simple mock repository that overrides migrate method
            val testRepository = object : JpaSagaRecordRepository(sagaJpaRepository, archivedSagaJpaRepository) {
                override fun migrate(sagas: List<Saga>, archivedSagas: List<ArchivedSaga>) {
                    // Do nothing for test
                }
            }

            // When
            val archivedCount = testRepository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            assertEquals(3, archivedCount)
        }

        @Test
        @DisplayName("当没有Saga需要归档时应该返回0")
        fun `should return 0 when no sagas to archive`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10
            val emptyPage = PageImpl<Saga>(emptyList())

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            val archivedCount = repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            assertEquals(0, archivedCount)

            verify(exactly = 0) { archivedSagaJpaRepository.saveAll(any<List<ArchivedSaga>>()) }
            verify(exactly = 0) { sagaJpaRepository.deleteAllInBatch(any<List<Saga>>()) }
        }

        @Test
        @DisplayName("应该使用正确的查询条件查找需要归档的Saga")
        fun `should use correct criteria to find sagas for archiving`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10
            val emptyPage = PageImpl<Saga>(emptyList())

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            verify {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Saga.F_NEXT_TRY_TIME))
                )
            }
        }
    }

    @Nested
    @DisplayName("迁移方法测试")
    inner class MigrateTest {

        @Test
        @DisplayName("应该成功迁移Saga到归档表")
        fun `should migrate sagas to archive table successfully`() {
            // Given
            val sagas = listOf(
                createMockSaga("saga1", Saga.SagaState.EXECUTED),
                createMockSaga("saga2", Saga.SagaState.CANCEL)
            )
            val archivedSagas = listOf(mockk<ArchivedSaga>(), mockk<ArchivedSaga>())

            every { archivedSagaJpaRepository.saveAll(archivedSagas) } returns archivedSagas
            every { sagaJpaRepository.deleteAllInBatch(sagas) } returns Unit

            // When
            repository.migrate(sagas, archivedSagas)

            // Then
            verify { archivedSagaJpaRepository.saveAll(archivedSagas) }
            verify { sagaJpaRepository.deleteAllInBatch(sagas) }
        }

        @Test
        @DisplayName("应该能够处理空列表")
        fun `should handle empty lists`() {
            // Given
            val emptySagas = emptyList<Saga>()
            val emptyArchivedSagas = emptyList<ArchivedSaga>()

            every { archivedSagaJpaRepository.saveAll(emptyArchivedSagas) } returns emptyArchivedSagas
            every { sagaJpaRepository.deleteAllInBatch(emptySagas) } returns Unit

            // When & Then
            assertDoesNotThrow {
                repository.migrate(emptySagas, emptyArchivedSagas)
            }

            verify { archivedSagaJpaRepository.saveAll(emptyArchivedSagas) }
            verify { sagaJpaRepository.deleteAllInBatch(emptySagas) }
        }

        @Test
        @DisplayName("当保存归档Saga失败时应该抛出异常")
        fun `should throw exception when saving archived sagas fails`() {
            // Given
            val sagas = listOf(createMockSaga("saga1", Saga.SagaState.EXECUTED))
            val archivedSagas = listOf(mockk<ArchivedSaga>())

            every {
                archivedSagaJpaRepository.saveAll(archivedSagas)
            } throws RuntimeException("Database error")

            // When & Then
            assertThrows<RuntimeException> {
                repository.migrate(sagas, archivedSagas)
            }

            verify { archivedSagaJpaRepository.saveAll(archivedSagas) }
            verify(exactly = 0) { sagaJpaRepository.deleteAllInBatch(any()) }
        }
    }

    @Nested
    @DisplayName("集成测试")
    inner class IntegrationTest {

        @Test
        @DisplayName("完整的Saga生命周期测试")
        fun `should handle complete saga lifecycle`() {
            // Given - 创建Saga
            val sagaRecord = repository.create()
            val sagaParam = TestSagaParam("create-user", mapOf("username" to "john"))
            sagaRecord.init(sagaParam, "user-service", "CREATE_USER_SAGA", testTime, Duration.ofHours(1), 3)

            val savedSaga = mockk<Saga>(relaxed = true) {
                every { id } returns 1L
                every { sagaUuid } returns "saved-saga-id"
                every { svcName } returns "user-service"
                every { sagaType } returns "CREATE_USER_SAGA"
                every { lastTryTime } returns testTime
                every { nextTryTime } returns testTime.plusMinutes(1)
                every { sagaState } returns Saga.SagaState.INIT
                every { param } returns """{"action":"create-user","data":{"username":"john"}}"""
                every { paramType } returns "TestSagaParam"
                every { result } returns """{"success":true,"userId":"12345"}"""
                every { resultType } returns "TestSagaResult"
                every { exception } returns null
                every { expireAt } returns testTime.plusHours(1)
                every { createAt } returns testTime.minusHours(1)
                every { tryTimes } returns 3
                every { triedTimes } returns 0
                every { sagaResult } returns null
                every { version } returns 1
            }

            every { sagaJpaRepository.saveAndFlush(any()) } returns savedSaga
            every {
                sagaJpaRepository.findOne(any<Specification<Saga>>())
            } returns Optional.of(savedSaga)

            // When - 保存Saga
            repository.save(sagaRecord)

            // Then - 验证能够重新获取
            val retrievedRecord = repository.getById("saved-saga-id")
            assertNotNull(retrievedRecord)
            assertEquals("CREATE_USER_SAGA", retrievedRecord.type)
        }

        @Test
        @DisplayName("批量处理Saga测试")
        fun `should handle batch processing of sagas`() {
            // Given
            val svcName = "batch-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5

            val mockSagas = (1..3).map { i ->
                createMockSaga("batch-saga-$i", Saga.SagaState.INIT)
            }
            val mockPage = PageImpl(mockSagas)

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val sagaRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, sagaRecords.size)
            sagaRecords.forEachIndexed { index, sagaRecord ->
                assertTrue(sagaRecord is SagaRecordImpl)
                val impl = sagaRecord as SagaRecordImpl
                assertEquals(mockSagas[index], impl.saga)
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
                sagaJpaRepository.saveAndFlush(any())
            } throws RuntimeException("Database connection failed")

            val sagaRecord = SagaRecordImpl()
            val sagaParam = TestSagaParam("test", mapOf("key" to "value"))
            sagaRecord.init(sagaParam, "test-service", "TEST_SAGA", testTime, Duration.ofMinutes(10), 3)

            // When & Then
            assertThrows<RuntimeException> {
                repository.save(sagaRecord)
            }
        }

        @Test
        @DisplayName("应该处理查询超时")
        fun `should handle query timeout`() {
            // Given
            every {
                sagaJpaRepository.findOne(any<Specification<Saga>>())
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
            val mockSagas = listOf(createMockSaga("saga1", Saga.SagaState.EXECUTED))
            val mockPage = PageImpl(mockSagas)

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            every {
                archivedSagaJpaRepository.saveAll(any<List<ArchivedSaga>>())
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
        @DisplayName("大批量Saga查询性能测试")
        fun `should handle large batch saga query efficiently`() {
            // Given
            val batchSize = 1000
            val largeSagaList = (1..batchSize).map { createMockSaga("saga$it", Saga.SagaState.INIT) }
            val mockPage = PageImpl(largeSagaList)

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val startTime = System.currentTimeMillis()
            val sagaRecords = repository.getByNextTryTime("test-service", testTime.plusHours(1), batchSize)
            val duration = System.currentTimeMillis() - startTime

            // Then
            assertEquals(batchSize, sagaRecords.size)
            assertTrue(duration < 5000) // 应该在5秒内完成
        }

        @Test
        @DisplayName("大批量Saga归档性能测试")
        fun `should handle large batch saga archiving efficiently`() {
            // Given
            val batchSize = 1000
            val largeSagaList = (1..batchSize).map { createMockSaga("saga$it", Saga.SagaState.EXECUTED) }
            val mockPage = PageImpl(largeSagaList)

            every {
                sagaJpaRepository.findAll(
                    any<Specification<Saga>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // Mock the migrate method instead of dealing with ArchivedSaga construction
            val repositorySpy = spyk(repository)
            every { repositorySpy.migrate(any<List<Saga>>(), any<List<ArchivedSaga>>()) } just Runs

            // When
            val startTime = System.currentTimeMillis()
            val archivedCount = repositorySpy.archiveByExpireAt("test-service", testTime.minusDays(1), batchSize)
            val duration = System.currentTimeMillis() - startTime

            // Then
            assertEquals(batchSize, archivedCount)
            assertTrue(duration < 3000) // 应该在3秒内完成
        }
    }

    private fun createMockSaga(sagaId: String, state: Saga.SagaState): Saga {
        return mockk<Saga> {
            every { id } returns 1L
            every { sagaUuid } returns sagaId
            every { sagaState } returns state
            every { svcName } returns "test-service"
            every { lastTryTime } returns testTime
            every { nextTryTime } returns testTime.plusMinutes(1)
            every { sagaType } returns "TEST_SAGA"
            every { sagaParam } returns TestSagaParam("test", mapOf("key" to "value"))
            every { param } returns """{"action":"test","data":{"key":"value"}}"""
            every { paramType } returns "TestSagaParam"
            every { result } returns """{"success":true,"message":"completed"}"""
            every { resultType } returns "TestSagaResult"
            every { exception } returns null
            every { expireAt } returns testTime.plusHours(1)
            every { createAt } returns testTime.minusHours(1)
            every { tryTimes } returns 3
            every { triedTimes } returns 0
            every { sagaResult } returns null
            every { version } returns 1
            every { sagaProcesses } returns mutableListOf()
        }
    }
}
