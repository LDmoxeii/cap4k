package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.domain.event.persistence.TestEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("JpaEventScheduleService调度服务测试")
class JpaEventScheduleServiceTest {

    private lateinit var scheduleService: JpaEventScheduleService
    private lateinit var eventPublisher: EventPublisher
    private lateinit var eventRecordRepository: EventRecordRepository
    private lateinit var locker: Locker
    private lateinit var jdbcTemplate: JdbcTemplate

    private val svcName = "test-service"
    private val compensationLockerKey = "compensation-lock"
    private val archiveLockerKey = "archive-lock"
    private val enableAddPartition = true

    @BeforeEach
    fun setUp() {
        eventPublisher = mockk(relaxed = true)
        eventRecordRepository = mockk(relaxed = true)
        locker = mockk(relaxed = true)
        jdbcTemplate = mockk(relaxed = true)

        scheduleService = JpaEventScheduleService(
            eventPublisher = eventPublisher,
            eventRecordRepository = eventRecordRepository,
            locker = locker,
            svcName = svcName,
            compensationLockerKey = compensationLockerKey,
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
            val serviceWithoutPartition = JpaEventScheduleService(
                eventPublisher = eventPublisher,
                eventRecordRepository = eventRecordRepository,
                locker = locker,
                svcName = svcName,
                compensationLockerKey = compensationLockerKey,
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
    @DisplayName("事件补偿测试")
    inner class CompensationTest {

        @Test
        @DisplayName("应该成功执行事件补偿")
        fun `should execute event compensation successfully`() {
            // Given
            val batchSize = 10
            val maxConcurrency = 5
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            val mockEventRecords = listOf(
                createMockEventRecord("event1"),
                createMockEventRecord("event2")
            )

            every { locker.acquire(compensationLockerKey, any(), maxLockDuration) } returns true
            every { eventRecordRepository.getByNextTryTime(any(), any(), batchSize) } returnsMany listOf(
                mockEventRecords,
                emptyList()
            )

            // When
            scheduleService.compense(batchSize, maxConcurrency, interval, maxLockDuration)

            // Then
            verify { locker.acquire(compensationLockerKey, any(), maxLockDuration) }
            verify { eventRecordRepository.getByNextTryTime(svcName, any(), batchSize) }
            verify(exactly = 2) { eventPublisher.resume(any(), any()) }
            verify { locker.release(compensationLockerKey, any()) }
        }

        @Test
        @DisplayName("当获取锁失败时应该直接返回")
        fun `should return immediately when lock acquisition fails`() {
            // Given
            val batchSize = 10
            val maxConcurrency = 5
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            every { locker.acquire(compensationLockerKey, any(), maxLockDuration) } returns false

            // When
            scheduleService.compense(batchSize, maxConcurrency, interval, maxLockDuration)

            // Then
            verify { locker.acquire(compensationLockerKey, any(), maxLockDuration) }
            verify(exactly = 0) { eventRecordRepository.getByNextTryTime(any(), any(), any()) }
            verify(exactly = 0) { eventPublisher.resume(any(), any()) }
            verify(exactly = 0) { locker.release(any(), any()) }
        }

        @Test
        @DisplayName("应该处理补偿过程中的异常")
        fun `should handle exceptions during compensation`() {
            // Given
            val batchSize = 10
            val maxConcurrency = 5
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            val mockEventRecord = createMockEventRecord("event1")

            every { locker.acquire(compensationLockerKey, any(), maxLockDuration) } returns true
            every { eventRecordRepository.getByNextTryTime(any(), any(), batchSize) } returnsMany listOf(
                listOf(
                    mockEventRecord
                ), emptyList()
            )
            every { eventPublisher.resume(any(), any()) } throws RuntimeException("Publishing failed")

            // When
            assertDoesNotThrow {
                scheduleService.compense(batchSize, maxConcurrency, interval, maxLockDuration)
            }

            // Then
            verify { locker.acquire(compensationLockerKey, any(), maxLockDuration) }
            verify { eventPublisher.resume(mockEventRecord, any()) }
            verify { locker.release(compensationLockerKey, any()) }
        }

        @Test
        @DisplayName("当没有事件需要补偿时应该正常结束")
        fun `should finish normally when no events need compensation`() {
            // Given
            val batchSize = 10
            val maxConcurrency = 5
            val interval = Duration.ofMinutes(5)
            val maxLockDuration = Duration.ofMinutes(10)

            every { locker.acquire(compensationLockerKey, any(), maxLockDuration) } returns true
            every { eventRecordRepository.getByNextTryTime(any(), any(), batchSize) } returns emptyList()

            // When
            scheduleService.compense(batchSize, maxConcurrency, interval, maxLockDuration)

            // Then
            verify { locker.acquire(compensationLockerKey, any(), maxLockDuration) }
            verify { eventRecordRepository.getByNextTryTime(svcName, any(), batchSize) }
            verify(exactly = 0) { eventPublisher.resume(any(), any()) }
            verify { locker.release(compensationLockerKey, any()) }
        }
    }

    @Nested
    @DisplayName("事件归档测试")
    inner class ArchiveTest {

        @Test
        @DisplayName("应该成功执行事件归档")
        fun `should execute event archiving successfully`() {
            // Given
            val expireDays = 30
            val batchSize = 100
            val maxLockDuration = Duration.ofMinutes(15)

            every { locker.acquire(archiveLockerKey, any(), maxLockDuration) } returns true
            every { eventRecordRepository.archiveByExpireAt(any(), any(), batchSize) } returnsMany listOf(50, 30, 0)

            // When
            scheduleService.archive(expireDays, batchSize, maxLockDuration)

            // Then
            verify { locker.acquire(archiveLockerKey, any(), maxLockDuration) }
            verify(exactly = 3) { eventRecordRepository.archiveByExpireAt(svcName, any(), batchSize) }
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
            verify(exactly = 0) { eventRecordRepository.archiveByExpireAt(any(), any(), any()) }
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
                eventRecordRepository.archiveByExpireAt(any(), any(), batchSize)
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
            verify(exactly = 3) { eventRecordRepository.archiveByExpireAt(svcName, any(), batchSize) }
            verify { locker.release(archiveLockerKey, any()) }
        }

        @Test
        @DisplayName("当没有事件需要归档时应该正常结束")
        fun `should finish normally when no events need archiving`() {
            // Given
            val expireDays = 30
            val batchSize = 100
            val maxLockDuration = Duration.ofMinutes(15)

            every { locker.acquire(archiveLockerKey, any(), maxLockDuration) } returns true
            every { eventRecordRepository.archiveByExpireAt(any(), any(), batchSize) } returns 0

            // When
            scheduleService.archive(expireDays, batchSize, maxLockDuration)

            // Then
            verify { locker.acquire(archiveLockerKey, any(), maxLockDuration) }
            verify(exactly = 1) { eventRecordRepository.archiveByExpireAt(svcName, any(), batchSize) }
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
                eventRecordRepository.archiveByExpireAt(
                    svcName,
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
        @DisplayName("应该添加事件表分区")
        fun `should add event table partitions`() {
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
            val serviceWithoutPartition = JpaEventScheduleService(
                eventPublisher = eventPublisher,
                eventRecordRepository = eventRecordRepository,
                locker = locker,
                svcName = svcName,
                compensationLockerKey = compensationLockerKey,
                archiveLockerKey = archiveLockerKey,
                enableAddPartition = false,
                jdbcTemplate = jdbcTemplate
            )

            // When
            serviceWithoutPartition.addPartition()

            // Then
            verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
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
            every { eventRecordRepository.getByNextTryTime(any(), any(), any()) } returns emptyList()
            every { eventRecordRepository.archiveByExpireAt(any(), any(), any()) } returns 0

            // When - 初始化
            scheduleService.init()

            // Then - 执行补偿
            scheduleService.compense(10, 5, Duration.ofMinutes(5), Duration.ofMinutes(10))

            // And - 执行归档
            scheduleService.archive(30, 100, Duration.ofMinutes(15))

            // Then - 验证所有操作都被执行
            verify(atLeast = 1) { jdbcTemplate.execute(any<String>()) }
            verify(exactly = 2) { locker.acquire(any(), any(), any()) }
            verify { eventRecordRepository.getByNextTryTime(any(), any(), any()) }
            verify { eventRecordRepository.archiveByExpireAt(any(), any(), any()) }
            verify(exactly = 2) { locker.release(any(), any()) }
        }
    }

    @Nested
    @DisplayName("性能测试")
    inner class PerformanceTest {

        @Test
        @DisplayName("大批量事件补偿性能测试")
        fun `should handle large batch compensation efficiently`() {
            // Given
            val batchSize = 1000
            val largeEventList = (1..batchSize).map { createMockEventRecord("event$it") }

            every { locker.acquire(compensationLockerKey, any(), any()) } returns true
            every {
                eventRecordRepository.getByNextTryTime(any(), any(), batchSize)
            } returnsMany listOf(largeEventList, emptyList())

            // When
            val startTime = System.currentTimeMillis()
            scheduleService.compense(batchSize, 10, Duration.ofMinutes(5), Duration.ofMinutes(10))
            val duration = System.currentTimeMillis() - startTime

            // Then
            verify(exactly = batchSize) { eventPublisher.resume(any(), any()) }
            assertTrue(duration < 5000) // 应该在5秒内完成
        }

        @Test
        @DisplayName("大批量事件归档性能测试")
        fun `should handle large batch archiving efficiently`() {
            // Given
            val batchSize = 1000
            val archiveCount = 500

            every { locker.acquire(archiveLockerKey, any(), any()) } returns true
            every {
                eventRecordRepository.archiveByExpireAt(any(), any(), batchSize)
            } returnsMany listOf(archiveCount, 0)

            // When
            val startTime = System.currentTimeMillis()
            scheduleService.archive(30, batchSize, Duration.ofMinutes(15))
            val duration = System.currentTimeMillis() - startTime

            // Then
            verify(exactly = 2) { eventRecordRepository.archiveByExpireAt(any(), any(), batchSize) }
            assertTrue(duration < 3000) // 应该在3秒内完成
        }
    }

    private fun createMockEventRecord(eventId: String): EventRecord {
        return mockk<EventRecord> {
            every { id } returns eventId
            every { type } returns "test.event"
            every { payload } returns TestEvent("test", 12345)
            every { scheduleTime } returns LocalDateTime.now()
            every { nextTryTime } returns LocalDateTime.now().plusMinutes(1)
            every { isPersist } returns false
            every { isValid } returns true
            every { isInvalid } returns false
            every { isDelivered } returns false
        }
    }
}
