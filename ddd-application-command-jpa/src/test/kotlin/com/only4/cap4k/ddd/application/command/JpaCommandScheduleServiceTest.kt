package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.application.command.persistence.TestCommand
import com.only4.cap4k.ddd.core.application.command.CommandManager
import com.only4.cap4k.ddd.core.application.command.CommandRecord
import com.only4.cap4k.ddd.core.application.distributed.Locker
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("JpaCommandScheduleService调度服务测试")
class JpaCommandScheduleServiceTest {

    private lateinit var scheduleService: JpaCommandScheduleService
    private lateinit var commandManager: CommandManager
    private lateinit var locker: Locker
    private lateinit var jdbcTemplate: JdbcTemplate

    private val retryLockerKey = "retry-lock"
    private val archiveLockerKey = "archive-lock"
    private val enableAddPartition = true

    @BeforeEach
    fun setUp() {
        commandManager = mockk(relaxed = true)
        locker = mockk(relaxed = true)
        jdbcTemplate = mockk(relaxed = true)

        scheduleService = JpaCommandScheduleService(
            commandManager = commandManager,
            locker = locker,
            retryLockerKey = retryLockerKey,
            archiveLockerKey = archiveLockerKey,
            enableAddPartition = enableAddPartition,
            jdbcTemplate = jdbcTemplate
        )
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTest {

        @Test
        @DisplayName("应该在初始化时添加分区")
        fun `should add partitions when initialized`() {
            // When
            scheduleService.init()

            // Then
            verify(atLeast = 1) { jdbcTemplate.execute(any<String>()) }
        }

        @Test
        @DisplayName("当enableAddPartition为false时不应该添加分区")
        fun `should not add partitions when enableAddPartition is false`() {
            // Given
            val serviceWithoutPartition = JpaCommandScheduleService(
                commandManager = commandManager,
                locker = locker,
                retryLockerKey = retryLockerKey,
                archiveLockerKey = archiveLockerKey,
                enableAddPartition = false,
                jdbcTemplate = jdbcTemplate
            )

            // When
            serviceWithoutPartition.init()

            // Then
            verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
        }
    }

    @Nested
    @DisplayName("命令补偿测试")
    inner class RetryTest {

        @Test
        @DisplayName("应该成功执行命令补偿")
        fun `should execute command retry successfully`() {
            // Given
            val batchSize = 10
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            val mockCommandRecords = listOf(
                createMockCommandRecord("command1"),
                createMockCommandRecord("command2")
            )

            every { locker.acquire(retryLockerKey, any(), maxLockDuration) } returns true
            every { commandManager.getByNextTryTime(any(), batchSize) } returnsMany listOf(
                mockCommandRecords,
                emptyList()
            )

            // When
            scheduleService.retry(batchSize, interval, maxLockDuration)

            // Then
            verify { locker.acquire(retryLockerKey, any(), maxLockDuration) }
            verify { commandManager.getByNextTryTime(any(), batchSize) }
            verify(exactly = 2) { commandManager.resume(any(), any()) }
            verify { locker.release(retryLockerKey, any()) }
        }

        @Test
        @DisplayName("当获取锁失败时应该直接返回")
        fun `should return immediately when lock acquisition fails`() {
            // Given
            val batchSize = 10
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            every { locker.acquire(retryLockerKey, any(), maxLockDuration) } returns false

            // When
            scheduleService.retry(batchSize, interval, maxLockDuration)

            // Then
            verify { locker.acquire(retryLockerKey, any(), maxLockDuration) }
            verify(exactly = 0) { commandManager.getByNextTryTime(any(), any()) }
            verify(exactly = 0) { commandManager.resume(any(), any()) }
            verify(exactly = 0) { locker.release(any(), any()) }
        }

        @Test
        @DisplayName("应该处理补偿过程中的异常")
        fun `should handle exceptions during retry`() {
            // Given
            val batchSize = 10
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            val mockCommandRecord = createMockCommandRecord("command1")

            every { locker.acquire(retryLockerKey, any(), maxLockDuration) } returns true
            every { commandManager.getByNextTryTime(any(), batchSize) } returnsMany listOf(
                listOf(mockCommandRecord),
                emptyList()
            )
            every { commandManager.resume(any(), any()) } throws RuntimeException("Resume failed")

            // When
            assertDoesNotThrow {
                scheduleService.retry(batchSize, interval, maxLockDuration)
            }

            // Then
            verify { locker.acquire(retryLockerKey, any(), maxLockDuration) }
            verify { commandManager.resume(mockCommandRecord, any()) }
            verify { locker.release(retryLockerKey, any()) }
        }

        @Test
        @DisplayName("当没有命令需要补偿时应该正常结束")
        fun `should finish normally when no commands need retry`() {
            // Given
            val batchSize = 10
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            every { locker.acquire(retryLockerKey, any(), maxLockDuration) } returns true
            every { commandManager.getByNextTryTime(any(), batchSize) } returns emptyList()

            // When
            scheduleService.retry(batchSize, interval, maxLockDuration)

            // Then
            verify { locker.acquire(retryLockerKey, any(), maxLockDuration) }
            verify { commandManager.getByNextTryTime(any(), batchSize) }
            verify(exactly = 0) { commandManager.resume(any(), any()) }
            verify { locker.release(retryLockerKey, any()) }
        }

        @Test
        @DisplayName("当补偿正在运行时应该跳过")
        fun `should skip when retry is already running`() {
            // Given
            val batchSize = 10
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            // 模拟获取锁成功
            every { locker.acquire(retryLockerKey, any(), maxLockDuration) } returns true

            // 第一次调用返回一个命令，第二次调用返回空列表来结束循环
            // 使用 slot 来捕获调用参数，避免多次调用的混乱
            val callCount = mutableListOf<Int>()
            every { commandManager.getByNextTryTime(any(), batchSize) } answers {
                callCount.add(1)
                if (callCount.size == 1) {
                    // 第一次调用：延迟较长时间以模拟正在运行
                    Thread.sleep(200)
                    emptyList()
                } else {
                    emptyList()
                }
            }

            // When - 启动一个后台线程来运行补偿
            val thread1 = Thread {
                scheduleService.retry(batchSize, interval, maxLockDuration)
            }
            thread1.start()

            // 等待一点时间确保第一个线程开始并设置了 retryRunning = true
            Thread.sleep(50)

            // 现在在主线程中调用补偿，应该立即跳过
            val startTime = System.currentTimeMillis()
            scheduleService.retry(batchSize, interval, maxLockDuration)
            val executionTime = System.currentTimeMillis() - startTime

            // 等待第一个线程完成
            thread1.join(1000)

            // Then - 主线程的调用应该立即返回（被跳过）
            assertTrue(executionTime < 20, "第二次调用应该立即跳过，用时: ${executionTime}ms")

            // 验证只有第一个线程执行了实际逻辑
            assertEquals(1, callCount.size, "应该只有一次 getByNextTryTime 调用")
            verify(exactly = 1) { locker.acquire(retryLockerKey, any(), maxLockDuration) }
            verify(exactly = 1) { locker.release(retryLockerKey, any()) }
        }
    }

    @Nested
    @DisplayName("命令归档测试")
    inner class ArchiveTest {

        @Test
        @DisplayName("应该成功执行命令归档")
        fun `should execute command archiving successfully`() {
            // Given
            val expireDays = 30
            val batchSize = 100
            val maxLockDuration = Duration.ofMinutes(15)

            every { locker.acquire(archiveLockerKey, any(), maxLockDuration) } returns true
            every { commandManager.archiveByExpireAt(any(), batchSize) } returnsMany listOf(50, 30, 0)

            // When
            scheduleService.archive(expireDays, batchSize, maxLockDuration)

            // Then
            verify { locker.acquire(archiveLockerKey, any(), maxLockDuration) }
            verify(exactly = 3) { commandManager.archiveByExpireAt(any(), batchSize) }
            verify { locker.release(archiveLockerKey, any()) }
        }

        @Test
        @DisplayName("当获取锁失败时应该直接返回")
        fun `should return immediately when archive lock acquisition fails`() {
            // Given
            val expireDays = 30
            val batchSize = 100
            val maxLockDuration = Duration.ofMinutes(15)

            every { locker.acquire(archiveLockerKey, any(), maxLockDuration) } returns false

            // When
            scheduleService.archive(expireDays, batchSize, maxLockDuration)

            // Then
            verify { locker.acquire(archiveLockerKey, any(), maxLockDuration) }
            verify(exactly = 0) { commandManager.archiveByExpireAt(any(), any()) }
            verify(exactly = 0) { locker.release(any(), any()) }
        }

        @Test
        @DisplayName("应该处理归档过程中的异常")
        fun `should handle exceptions during archiving`() {
            // Given
            val expireDays = 30
            val batchSize = 100
            val maxLockDuration = Duration.ofMinutes(15)

            every { locker.acquire(archiveLockerKey, any(), maxLockDuration) } returns true
            // 模拟前3次调用都抛出异常
            var callCount = 0
            every {
                commandManager.archiveByExpireAt(any(), batchSize)
            } answers {
                callCount++
                if (callCount <= 3) {
                    throw RuntimeException("Archive failed $callCount")
                } else {
                    0
                }
            }

            // When
            assertDoesNotThrow {
                scheduleService.archive(expireDays, batchSize, maxLockDuration)
            }

            // Then - 应该在3次失败后退出
            verify { locker.acquire(archiveLockerKey, any(), maxLockDuration) }
            verify(exactly = 3) { commandManager.archiveByExpireAt(any(), batchSize) }
            verify { locker.release(archiveLockerKey, any()) }
        }

        @Test
        @DisplayName("当没有命令需要归档时应该正常结束")
        fun `should finish normally when no commands need archiving`() {
            // Given
            val expireDays = 30
            val batchSize = 100
            val maxLockDuration = Duration.ofMinutes(15)

            every { locker.acquire(archiveLockerKey, any(), maxLockDuration) } returns true
            every { commandManager.archiveByExpireAt(any(), batchSize) } returns 0

            // When
            scheduleService.archive(expireDays, batchSize, maxLockDuration)

            // Then
            verify { locker.acquire(archiveLockerKey, any(), maxLockDuration) }
            verify(exactly = 1) { commandManager.archiveByExpireAt(any(), batchSize) }
            verify { locker.release(archiveLockerKey, any()) }
        }

        @Test
        @DisplayName("应该使用正确的过期时间计算")
        fun `should use correct expire time calculation`() {
            // Given
            val expireDays = 7
            val batchSize = 100
            val maxLockDuration = Duration.ofMinutes(15)
            val expireTimeSlot = slot<LocalDateTime>()

            every { locker.acquire(archiveLockerKey, any(), maxLockDuration) } returns true
            every {
                commandManager.archiveByExpireAt(
                    capture(expireTimeSlot),
                    batchSize
                )
            } returns 0

            // When
            scheduleService.archive(expireDays, batchSize, maxLockDuration)

            // Then
            val capturedExpireTime = expireTimeSlot.captured
            val expectedExpireTime = LocalDateTime.now().minusDays(expireDays.toLong())

            // 允许几秒钟的误差
            assertTrue(
                capturedExpireTime.isAfter(expectedExpireTime.minusSeconds(5)) &&
                        capturedExpireTime.isBefore(expectedExpireTime.plusSeconds(5))
            )
        }
    }

    @Nested
    @DisplayName("分区管理测试")
    inner class PartitionManagementTest {

        @Test
        @DisplayName("应该添加命令表分区")
        fun `should add command table partitions`() {
            // When
            scheduleService.addPartition()

            // Then
            verify(atLeast = 2) { jdbcTemplate.execute(any<String>()) }
        }

        @Test
        @DisplayName("应该处理重复分区异常")
        fun `should handle duplicate partition exceptions`() {
            // Given
            every {
                jdbcTemplate.execute(any<String>())
            } throws RuntimeException("Duplicate partition name 'p202501'")

            // When
            assertDoesNotThrow {
                scheduleService.addPartition()
            }

            // Then
            verify(atLeast = 1) { jdbcTemplate.execute(any<String>()) }
        }

        @Test
        @DisplayName("应该处理其他数据库异常")
        fun `should handle other database exceptions`() {
            // Given
            every {
                jdbcTemplate.execute(any<String>())
            } throws RuntimeException("Table does not exist")

            // When
            assertDoesNotThrow {
                scheduleService.addPartition()
            }

            // Then
            verify(atLeast = 1) { jdbcTemplate.execute(any<String>()) }
        }

        @Test
        @DisplayName("应该生成正确的分区SQL")
        fun `should generate correct partition SQL`() {
            // Given
            val sqlCapture = mutableListOf<String>()
            every { jdbcTemplate.execute(capture(sqlCapture)) } returns Unit

            // When
            scheduleService.addPartition()

            // Then
            assertTrue(sqlCapture.isNotEmpty())
            sqlCapture.forEach { sql ->
                assertTrue(sql.contains("alter table"))
                assertTrue(sql.contains("add partition"))
                assertTrue(sql.contains("values less than"))
                assertTrue(sql.contains("to_days"))
            }
        }

        @Test
        @DisplayName("当enableAddPartition为false时不应该执行分区操作")
        fun `should not execute partition operations when enableAddPartition is false`() {
            // Given
            val serviceWithoutPartition = JpaCommandScheduleService(
                commandManager = commandManager,
                locker = locker,
                retryLockerKey = retryLockerKey,
                archiveLockerKey = archiveLockerKey,
                enableAddPartition = false,
                jdbcTemplate = jdbcTemplate
            )

            // When
            serviceWithoutPartition.addPartition()

            // Then
            verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
        }

        @Test
        @DisplayName("应该为正确的表名生成分区")
        fun `should generate partitions for correct table names`() {
            // Given
            val sqlCapture = mutableListOf<String>()
            every { jdbcTemplate.execute(capture(sqlCapture)) } returns Unit

            // When
            scheduleService.addPartition()

            // Then
            assertTrue(sqlCapture.any { it.contains("__command") })
            assertTrue(sqlCapture.any { it.contains("__archived_command") })
        }
    }

    @Nested
    @DisplayName("集成测试")
    inner class IntegrationTest {

        @Test
        @DisplayName("完整的调度服务生命周期测试")
        fun `should handle complete schedule service lifecycle`() {
            // Given
            every { locker.acquire(any(), any(), any()) } returns true
            every { commandManager.getByNextTryTime(any(), any()) } returns emptyList()
            every { commandManager.archiveByExpireAt(any(), any()) } returns 0

            // When - 初始化
            scheduleService.init()

            // Then - 执行补偿
            scheduleService.retry(10, Duration.ofMinutes(5), Duration.ofMinutes(10))

            // And - 执行归档
            scheduleService.archive(30, 100, Duration.ofMinutes(15))

            // Then - 验证所有操作都被执行
            verify(atLeast = 1) { jdbcTemplate.execute(any<String>()) }
            verify(exactly = 2) { locker.acquire(any(), any(), any()) }
            verify { commandManager.getByNextTryTime(any(), any()) }
            verify { commandManager.archiveByExpireAt(any(), any()) }
            verify(exactly = 2) { locker.release(any(), any()) }
        }

        @Test
        @DisplayName("复杂场景下的服务行为测试")
        fun `should handle service behavior in complex scenarios`() {
            // Given - 模拟复杂的执行场景
            val mockCommandRecords = listOf(
                createMockCommandRecord("urgent-command-1"),
                createMockCommandRecord("normal-command-2"),
                createMockCommandRecord("low-priority-command-3")
            )

            every { locker.acquire(retryLockerKey, any(), any()) } returns true
            every { locker.acquire(archiveLockerKey, any(), any()) } returns true
            every {
                commandManager.getByNextTryTime(any(), any())
            } returnsMany listOf(mockCommandRecords, emptyList())
            every { commandManager.archiveByExpireAt(any(), any()) } returnsMany listOf(100, 50, 0)

            // When - 执行完整的调度周期
            scheduleService.init()
            scheduleService.retry(10, Duration.ofMinutes(5), Duration.ofMinutes(10))
            scheduleService.archive(30, 100, Duration.ofMinutes(15))

            // Then - 验证所有命令都被正确处理
            verify(exactly = 3) { commandManager.resume(any(), any()) }
            verify(exactly = 3) { commandManager.archiveByExpireAt(any(), any()) }
        }
    }

    @Nested
    @DisplayName("性能测试")
    inner class PerformanceTest {

        @Test
        @DisplayName("大批量命令补偿性能测试")
        fun `should handle large batch retry efficiently`() {
            // Given
            val batchSize = 1000
            val largeCommandList = (1..batchSize).map { createMockCommandRecord("command$it") }

            every { locker.acquire(retryLockerKey, any(), any()) } returns true
            every {
                commandManager.getByNextTryTime(any(), batchSize)
            } returnsMany listOf(largeCommandList, emptyList())

            // When
            val startTime = System.currentTimeMillis()
            scheduleService.retry(batchSize, Duration.ofMinutes(5), Duration.ofMinutes(10))
            val duration = System.currentTimeMillis() - startTime

            // Then
            verify(exactly = batchSize) { commandManager.resume(any(), any()) }
            assertTrue(duration < 5000) // 应该在5秒内完成
        }

        @Test
        @DisplayName("大批量命令归档性能测试")
        fun `should handle large batch archiving efficiently`() {
            // Given
            val batchSize = 1000
            val archiveCount = 500

            every { locker.acquire(archiveLockerKey, any(), any()) } returns true
            every { commandManager.archiveByExpireAt(any(), batchSize) } returnsMany listOf(archiveCount, 0)

            // When
            val startTime = System.currentTimeMillis()
            scheduleService.archive(30, batchSize, Duration.ofMinutes(15))
            val duration = System.currentTimeMillis() - startTime

            // Then
            verify(exactly = 2) { commandManager.archiveByExpireAt(any(), batchSize) }
            assertTrue(duration < 3000) // 应该在3秒内完成
        }

        @Test
        @DisplayName("高并发分区创建性能测试")
        fun `should handle concurrent partition creation efficiently`() {
            // Given
            val threadCount = 10
            val sqlCapture = mutableListOf<String>()
            every { jdbcTemplate.execute(capture(sqlCapture)) } returns Unit

            // When - 多线程同时创建分区
            val threads = (1..threadCount).map {
                Thread {
                    scheduleService.addPartition()
                }
            }

            val startTime = System.currentTimeMillis()
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            val duration = System.currentTimeMillis() - startTime

            // Then
            assertTrue(sqlCapture.isNotEmpty())
            assertTrue(duration < 2000) // 应该在2秒内完成
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("应该处理零批次大小")
        fun `should handle zero batch size`() {
            // Given
            val batchSize = 0
            every { locker.acquire(retryLockerKey, any(), any()) } returns true
            every { commandManager.getByNextTryTime(any(), batchSize) } returns emptyList()

            // When & Then
            assertDoesNotThrow {
                scheduleService.retry(batchSize, Duration.ofMinutes(5), Duration.ofMinutes(10))
            }
        }

        @Test
        @DisplayName("应该处理负数过期天数")
        fun `should handle negative expire days`() {
            // Given
            val expireDays = -1
            every { locker.acquire(archiveLockerKey, any(), any()) } returns true
            every { commandManager.archiveByExpireAt(any(), any()) } returns 0

            // When & Then
            assertDoesNotThrow {
                scheduleService.archive(expireDays, 100, Duration.ofMinutes(15))
            }
        }

        @Test
        @DisplayName("应该处理极短的锁持有时间")
        fun `should handle very short lock duration`() {
            // Given
            val maxLockDuration = Duration.ofMillis(1)
            every { locker.acquire(any(), any(), maxLockDuration) } returns true
            every { commandManager.getByNextTryTime(any(), any()) } returns emptyList()

            // When & Then
            assertDoesNotThrow {
                scheduleService.retry(10, Duration.ofMinutes(5), maxLockDuration)
            }
        }

        @Test
        @DisplayName("应该处理数据库分区SQL的特殊字符")
        fun `should handle special characters in partition SQL`() {
            // Given
            val sqlCapture = mutableListOf<String>()
            every { jdbcTemplate.execute(capture(sqlCapture)) } returns Unit

            // When
            scheduleService.addPartition()

            // Then
            sqlCapture.forEach { sql ->
                assertFalse(sql.contains("';"), "SQL should not contain SQL injection characters")
                assertFalse(sql.contains("--"), "SQL should not contain comment characters")
            }
        }
    }

    private fun createMockCommandRecord(commandId: String): CommandRecord {
        return mockk<CommandRecord> {
            every { id } returns commandId
            every { type } returns "TEST_COMMAND"
            every { command } returns TestCommand("test", mapOf("key" to "value"))
            every { scheduleTime } returns LocalDateTime.now()
            every { nextTryTime } returns LocalDateTime.now().plusMinutes(1)
            every { isValid } returns true
            every { isInvalid } returns false
            every { isExecuting } returns false
            every { isExecuted } returns false
        }
    }
}
