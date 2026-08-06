package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.application.command.persistence.*
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

@DisplayName("JpaCommandRecordRepository仓储实现测试")
class JpaCommandRecordRepositoryTest {

    private lateinit var repository: JpaCommandRecordRepository
    private lateinit var commandJpaRepository: CommandRecordJpaRepository
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        commandJpaRepository = mockk()
        repository = JpaCommandRecordRepository(commandJpaRepository)
    }

    @Nested
    @DisplayName("创建CommandRecord测试")
    inner class CreateCommandRecordTest {

        @Test
        @DisplayName("应该创建新的CommandRecordImpl实例")
        fun `should create new CommandRecordImpl instance`() {
            // When
            val commandRecord = repository.create()

            // Then
            assertNotNull(commandRecord)
            assertTrue(commandRecord is CommandRecordImpl)
        }

        @Test
        @DisplayName("每次调用create应该返回新的实例")
        fun `should return new instance on each create call`() {
            // When
            val commandRecord1 = repository.create()
            val commandRecord2 = repository.create()

            // Then
            assertNotSame(commandRecord1, commandRecord2)
        }
    }

    @Nested
    @DisplayName("保存CommandRecord测试")
    inner class SaveCommandRecordTest {

        @Test
        @DisplayName("应该保存CommandRecord并更新实例")
        fun `should save command record and update instance`() {
            // Given
            val commandRecord = CommandRecordImpl()
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_COMMAND", testTime, Duration.ofMinutes(10), 3)

            val savedCommand = mockk<CommandRecordEntity> {
                every { commandUuid } returns "saved-uuid"
                every { svcName } returns "test-service"
                every { commandType } returns "TEST_COMMAND"
            }

            every { commandJpaRepository.save(any()) } returns savedCommand

            // When
            repository.save(commandRecord)

            // Then
            verify { commandJpaRepository.save(any()) }
            assertEquals(savedCommand, commandRecord.entity)
        }

        @Test
        @DisplayName("应该能够保存复杂的CommandRecord")
        fun `should save complex command record`() {
            // Given
            val commandRecord = CommandRecordImpl()
            val items = listOf(
                ProcessOrderCommand.OrderItem("product1", 2, 99.99),
                ProcessOrderCommand.OrderItem("product2", 1, 49.99)
            )
            val commandParam = ProcessOrderCommand("order123", "customer456", 249.97, items)
            commandRecord.init(commandParam, "order-service", "PROCESS_ORDER", testTime, Duration.ofMinutes(15), 3)

            val savedCommand = mockk<CommandRecordEntity>()
            every { commandJpaRepository.save(any()) } returns savedCommand

            // When
            repository.save(commandRecord)

            // Then
            verify { commandJpaRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("根据ID获取CommandRecord测试")
    inner class GetByIdTest {

        @Test
        @DisplayName("应该根据ID成功获取CommandRecord")
        fun `should get command record by id successfully`() {
            // Given
            val commandId = "test-command-id"
            val mockCommand = mockk<CommandRecordEntity> {
                every { commandUuid } returns commandId
                every { commandType } returns "TEST_COMMAND"
                every { svcName } returns "test-service"
                every { lastTryTime } returns testTime
                every { commandParam } returns TestCommand("test", mapOf("key" to "value"))
            }

            every {
                commandJpaRepository.findOne(any<Specification<CommandRecordEntity>>())
            } returns Optional.of(mockCommand)

            // When
            val commandRecord = repository.getById(commandId)

            // Then
            assertNotNull(commandRecord)
            assertTrue(commandRecord is CommandRecordImpl)
            val impl = commandRecord as CommandRecordImpl
            assertEquals(mockCommand, impl.entity)

            verify {
                commandJpaRepository.findOne(any<Specification<CommandRecordEntity>>())
            }
        }

        @Test
        @DisplayName("当命令不存在时应该抛出DomainException")
        fun `should throw DomainException when command not found`() {
            // Given
            val commandId = "non-existent-id"
            every {
                commandJpaRepository.findOne(any<Specification<CommandRecordEntity>>())
            } returns Optional.empty()

            // When & Then
            val exception = assertThrows<DomainException> {
                repository.getById(commandId)
            }
            assertEquals("CommandRecord not found", exception.message)
        }

        @Test
        @DisplayName("应该使用正确的查询条件")
        fun `should use correct query specification`() {
            // Given
            val commandId = "test-command-id"
            val mockCommand = mockk<CommandRecordEntity>()
            val specificationSlot = slot<Specification<CommandRecordEntity>>()

            every {
                commandJpaRepository.findOne(capture(specificationSlot))
            } returns Optional.of(mockCommand)

            // When
            repository.getById(commandId)

            // Then
            verify {
                commandJpaRepository.findOne(any<Specification<CommandRecordEntity>>())
            }
        }
    }

    @Nested
    @DisplayName("根据下次尝试时间获取CommandRecord测试")
    inner class GetByNextTryTimeTest {

        @Test
        @DisplayName("应该获取需要重试的命令记录")
        fun `should get command records for retry`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10

            val mockCommands = listOf(
                createMockCommand("command1", CommandRecordEntity.CommandState.INIT),
                createMockCommand("command2", CommandRecordEntity.CommandState.EXECUTING),
                createMockCommand("command3", CommandRecordEntity.CommandState.EXCEPTION)
            )
            val mockPage = PageImpl(mockCommands)

            every {
                commandJpaRepository.findAll(
                    any<Specification<CommandRecordEntity>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val commandRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, commandRecords.size)
            commandRecords.forEach { commandRecord ->
                assertTrue(commandRecord is CommandRecordImpl)
            }

            verify {
                commandJpaRepository.findAll(
                    any<Specification<CommandRecordEntity>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, CommandRecordEntity.F_NEXT_TRY_TIME))
                )
            }
        }

        @Test
        @DisplayName("应该返回空列表当没有符合条件的命令时")
        fun `should return empty list when no commands match criteria`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10
            val emptyPage = PageImpl<CommandRecordEntity>(emptyList())

            every {
                commandJpaRepository.findAll(
                    any<Specification<CommandRecordEntity>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            val commandRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertTrue(commandRecords.isEmpty())
        }

        @Test
        @DisplayName("应该使用正确的分页参数")
        fun `should use correct pagination parameters`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5
            val emptyPage = PageImpl<CommandRecordEntity>(emptyList())

            every {
                commandJpaRepository.findAll(
                    any<Specification<CommandRecordEntity>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            verify {
                commandJpaRepository.findAll(
                    any<Specification<CommandRecordEntity>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, CommandRecordEntity.F_NEXT_TRY_TIME))
                )
            }
        }
    }

    @Nested
    @DisplayName("集成测试")
    inner class IntegrationTest {

        @Test
        @DisplayName("完整的命令生命周期测试")
        fun `should handle complete command lifecycle`() {
            // Given - 创建命令
            val commandRecord = repository.create()
            val commandParam = CreateUserCommand("john", "john@test.com", "ADMIN")
            commandRecord.init(commandParam, "user-service", "CREATE_USER", testTime, Duration.ofHours(1), 3)

            val savedCommand = mockk<CommandRecordEntity>(relaxed = true) {
                every { id } returns 1L
                every { commandUuid } returns "saved-command-id"
                every { svcName } returns "user-service"
                every { commandType } returns "CREATE_USER"
                every { lastTryTime } returns testTime
                every { nextTryTime } returns testTime.plusMinutes(1)
                every { commandState } returns CommandRecordEntity.CommandState.INIT
                every { param } returns """{"username":"john","email":"john@test.com","role":"ADMIN"}"""
                every { paramType } returns "CreateUserCommand"
                every { expireAt } returns testTime.plusHours(1)
                every { createAt } returns testTime.minusHours(1)
                every { tryTimes } returns 3
                every { triedTimes } returns 0
                every { version } returns 1
            }

            every { commandJpaRepository.save(any()) } returns savedCommand
            every {
                commandJpaRepository.findOne(any<Specification<CommandRecordEntity>>())
            } returns Optional.of(savedCommand)

            // When - 保存命令
            repository.save(commandRecord)

            // Then - 验证能够重新获取
            val retrievedRecord = repository.getById("saved-command-id")
            assertNotNull(retrievedRecord)
            assertEquals("CREATE_USER", retrievedRecord.type)
        }

        @Test
        @DisplayName("批量处理命令测试")
        fun `should handle batch processing of commands`() {
            // Given
            val svcName = "batch-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5

            val mockCommands = (1..3).map { i ->
                createMockCommand("batch-command-$i", CommandRecordEntity.CommandState.INIT)
            }
            val mockPage = PageImpl(mockCommands)

            every {
                commandJpaRepository.findAll(
                    any<Specification<CommandRecordEntity>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val commandRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, commandRecords.size)
            commandRecords.forEachIndexed { index, commandRecord ->
                assertTrue(commandRecord is CommandRecordImpl)
                val impl = commandRecord as CommandRecordImpl
                assertEquals(mockCommands[index], impl.entity)
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
                commandJpaRepository.save(any())
            } throws RuntimeException("Database connection failed")

            val commandRecord = CommandRecordImpl()
            val commandParam = TestCommand("test", mapOf("key" to "value"))
            commandRecord.init(commandParam, "test-service", "TEST_COMMAND", testTime, Duration.ofMinutes(10), 3)

            // When & Then
            assertThrows<RuntimeException> {
                repository.save(commandRecord)
            }
        }

        @Test
        @DisplayName("应该处理查询超时")
        fun `should handle query timeout`() {
            // Given
            every {
                commandJpaRepository.findOne(any<Specification<CommandRecordEntity>>())
            } throws RuntimeException("Query timeout")

            // When & Then
            assertThrows<RuntimeException> {
                repository.getById("any-id")
            }
        }

    }

    @Nested
    @DisplayName("性能测试")
    inner class PerformanceTest {

        @Test
        @DisplayName("大批量命令查询性能测试")
        fun `should handle large batch command query efficiently`() {
            // Given
            val batchSize = 1000
            val largeCommandList = (1..batchSize).map { createMockCommand("command$it", CommandRecordEntity.CommandState.INIT) }
            val mockPage = PageImpl(largeCommandList)

            every {
                commandJpaRepository.findAll(
                    any<Specification<CommandRecordEntity>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val startTime = System.currentTimeMillis()
            val commandRecords = repository.getByNextTryTime("test-service", testTime.plusHours(1), batchSize)
            val duration = System.currentTimeMillis() - startTime

            // Then
            assertEquals(batchSize, commandRecords.size)
            assertTrue(duration < 5000) // 应该在5秒内完成
        }

    }

    private fun createMockCommand(commandId: String, state: CommandRecordEntity.CommandState): CommandRecordEntity {
        return mockk<CommandRecordEntity> {
            every { id } returns 1L
            every { commandUuid } returns commandId
            every { commandState } returns state
            every { svcName } returns "test-service"
            every { lastTryTime } returns testTime
            every { nextTryTime } returns testTime.plusMinutes(1)
            every { commandType } returns "TEST_COMMAND"
            every { commandParam } returns TestCommand("test", mapOf("key" to "value"))
            every { param } returns """{"action":"test","data":{"key":"value"},"timestamp":123456789}"""
            every { paramType } returns "TestCommand"
            every { executionContext } returns null
            every { expireAt } returns testTime.plusHours(1)
            every { createAt } returns testTime.minusHours(1)
            every { tryTimes } returns 3
            every { retryPolicy } returns "{}"
            every { triedTimes } returns 0
            every { version } returns 1
        }
    }
}
