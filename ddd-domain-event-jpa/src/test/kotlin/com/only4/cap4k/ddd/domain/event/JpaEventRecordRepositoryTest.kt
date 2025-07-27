package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.domain.event.persistence.*
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

@DisplayName("JpaEventRecordRepository仓储实现测试")
class JpaEventRecordRepositoryTest {

    private lateinit var repository: JpaEventRecordRepository
    private lateinit var eventJpaRepository: EventJpaRepository
    private lateinit var archivedEventJpaRepository: ArchivedEventJpaRepository
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        eventJpaRepository = mockk()
        archivedEventJpaRepository = mockk()
        repository = JpaEventRecordRepository(eventJpaRepository, archivedEventJpaRepository)
    }

    @Nested
    @DisplayName("创建EventRecord测试")
    inner class CreateEventRecordTest {

        @Test
        @DisplayName("应该创建新的EventRecordImpl实例")
        fun `should create new EventRecordImpl instance`() {
            // When
            val eventRecord = repository.create()

            // Then
            assertNotNull(eventRecord)
            assertTrue(eventRecord is EventRecordImpl)
        }

        @Test
        @DisplayName("每次调用create应该返回新的实例")
        fun `should return new instance on each create call`() {
            // When
            val eventRecord1 = repository.create()
            val eventRecord2 = repository.create()

            // Then
            assertNotSame(eventRecord1, eventRecord2)
        }
    }

    @Nested
    @DisplayName("保存EventRecord测试")
    inner class SaveEventRecordTest {

        @Test
        @DisplayName("应该保存EventRecord并更新实例")
        fun `should save event record and update instance`() {
            // Given
            val eventRecord = EventRecordImpl()
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            val savedEvent = mockk<Event> {
                every { eventUuid } returns "saved-uuid"
                every { svcName } returns "test-service"
            }

            every { eventJpaRepository.saveAndFlush(any()) } returns savedEvent

            // When
            repository.save(eventRecord)

            // Then
            verify { eventJpaRepository.saveAndFlush(any()) }
            assertEquals(savedEvent, eventRecord.event)
        }

        @Test
        @DisplayName("应该能够保存复杂的EventRecord")
        fun `should save complex event record`() {
            // Given
            val eventRecord = EventRecordImpl()
            val location = InventoryUpdatedEvent.Location("warehouse-a", "zone-1", "shelf-100")
            val details = mapOf("operator" to "admin", "reason" to "stock adjustment")
            val payload = InventoryUpdatedEvent("product123", 50, location, details)
            eventRecord.init(payload, "inventory-service", testTime, Duration.ofMinutes(15), 3)
            eventRecord.markPersist(true)

            val savedEvent = mockk<Event>()
            every { eventJpaRepository.saveAndFlush(any()) } returns savedEvent

            // When
            repository.save(eventRecord)

            // Then
            verify { eventJpaRepository.saveAndFlush(any()) }
        }
    }

    @Nested
    @DisplayName("根据ID获取EventRecord测试")
    inner class GetByIdTest {

        @Test
        @DisplayName("应该根据ID成功获取EventRecord")
        fun `should get event record by id successfully`() {
            // Given
            val eventId = "test-event-id"
            val mockEvent = mockk<Event> {
                every { eventUuid } returns eventId
                every { eventType } returns "test.event"
                every { svcName } returns "test-service"
                every { createAt } returns testTime
                every { payload } returns TestEvent("test", 12345)
            }

            every {
                eventJpaRepository.findOne(any<Specification<Event>>())
            } returns Optional.of(mockEvent)

            // When
            val eventRecord = repository.getById(eventId)

            // Then
            assertNotNull(eventRecord)
            assertTrue(eventRecord is EventRecordImpl)
            val impl = eventRecord as EventRecordImpl
            assertEquals(mockEvent, impl.event)

            verify {
                eventJpaRepository.findOne(any<Specification<Event>>())
            }
        }

        @Test
        @DisplayName("当事件不存在时应该抛出DomainException")
        fun `should throw DomainException when event not found`() {
            // Given
            val eventId = "non-existent-id"
            every {
                eventJpaRepository.findOne(any<Specification<Event>>())
            } returns Optional.empty()

            // When & Then
            val exception = assertThrows<DomainException> {
                repository.getById(eventId)
            }
            assertEquals("EventRecord not found", exception.message)
        }

        @Test
        @DisplayName("应该使用正确的查询条件")
        fun `should use correct query specification`() {
            // Given
            val eventId = "test-event-id"
            val mockEvent = mockk<Event>()
            val specificationSlot = slot<Specification<Event>>()

            every {
                eventJpaRepository.findOne(capture(specificationSlot))
            } returns Optional.of(mockEvent)

            // When
            repository.getById(eventId)

            // Then
            verify {
                eventJpaRepository.findOne(any<Specification<Event>>())
            }
        }
    }

    @Nested
    @DisplayName("根据下次尝试时间获取EventRecord测试")
    inner class GetByNextTryTimeTest {

        @Test
        @DisplayName("应该获取需要重试的事件记录")
        fun `should get event records for retry`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10

            val mockEvents = listOf(
                createMockEvent("event1", Event.EventState.INIT),
                createMockEvent("event2", Event.EventState.DELIVERING),
                createMockEvent("event3", Event.EventState.EXCEPTION)
            )
            val mockPage = PageImpl(mockEvents)

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val eventRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, eventRecords.size)
            eventRecords.forEach { eventRecord ->
                assertTrue(eventRecord is EventRecordImpl)
            }

            verify {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Event.F_NEXT_TRY_TIME))
                )
            }
        }

        @Test
        @DisplayName("应该返回空列表当没有符合条件的事件时")
        fun `should return empty list when no events match criteria`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 10
            val emptyPage = PageImpl<Event>(emptyList())

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            val eventRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertTrue(eventRecords.isEmpty())
        }

        @Test
        @DisplayName("应该使用正确的分页参数")
        fun `should use correct pagination parameters`() {
            // Given
            val svcName = "test-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5
            val emptyPage = PageImpl<Event>(emptyList())

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            verify {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Event.F_NEXT_TRY_TIME))
                )
            }
        }
    }

    @Nested
    @DisplayName("归档过期事件测试")
    inner class ArchiveByExpireAtTest {

        @Test
        @DisplayName("应该成功归档过期的事件")
        fun `should archive expired events successfully`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10

            val mockEvents = listOf(
                createMockEvent("event1", Event.EventState.DELIVERED),
                createMockEvent("event2", Event.EventState.CANCEL),
                createMockEvent("event3", Event.EventState.EXPIRED)
            )
            val mockPage = PageImpl(mockEvents)

            val mockArchivedEvents = listOf(
                mockk<ArchivedEvent>(),
                mockk<ArchivedEvent>(),
                mockk<ArchivedEvent>()
            )

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            every { archivedEventJpaRepository.saveAll(any<List<ArchivedEvent>>()) } returns mockArchivedEvents
            every { eventJpaRepository.deleteAllInBatch(any<List<Event>>()) } just Runs

            // When
            val archivedCount = repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            assertEquals(3, archivedCount)

            verify { archivedEventJpaRepository.saveAll(any<List<ArchivedEvent>>()) }
            verify { eventJpaRepository.deleteAllInBatch(mockEvents) }
        }

        @Test
        @DisplayName("当没有事件需要归档时应该返回0")
        fun `should return 0 when no events to archive`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10
            val emptyPage = PageImpl<Event>(emptyList())

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            val archivedCount = repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            assertEquals(0, archivedCount)

            verify(exactly = 0) { archivedEventJpaRepository.saveAll(any<List<ArchivedEvent>>()) }
            verify(exactly = 0) { eventJpaRepository.deleteAllInBatch(any<List<Event>>()) }
        }

        @Test
        @DisplayName("应该使用正确的查询条件查找需要归档的事件")
        fun `should use correct criteria to find events for archiving`() {
            // Given
            val svcName = "test-service"
            val maxExpireAt = testTime.minusDays(1)
            val limit = 10
            val emptyPage = PageImpl<Event>(emptyList())

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns emptyPage

            // When
            repository.archiveByExpireAt(svcName, maxExpireAt, limit)

            // Then
            verify {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Event.F_NEXT_TRY_TIME))
                )
            }
        }
    }

    @Nested
    @DisplayName("迁移方法测试")
    inner class MigrateTest {

        @Test
        @DisplayName("应该成功迁移事件到归档表")
        fun `should migrate events to archive table successfully`() {
            // Given
            val events = listOf(
                createMockEvent("event1", Event.EventState.DELIVERED),
                createMockEvent("event2", Event.EventState.CANCEL)
            )
            val archivedEvents = listOf(mockk<ArchivedEvent>(), mockk<ArchivedEvent>())

            every { archivedEventJpaRepository.saveAll(archivedEvents) } returns archivedEvents
            every { eventJpaRepository.deleteAllInBatch(events) } returns Unit

            // When
            repository.migrate(events, archivedEvents)

            // Then
            verify { archivedEventJpaRepository.saveAll(archivedEvents) }
            verify { eventJpaRepository.deleteAllInBatch(events) }
        }

        @Test
        @DisplayName("应该能够处理空列表")
        fun `should handle empty lists`() {
            // Given
            val emptyEvents = emptyList<Event>()
            val emptyArchivedEvents = emptyList<ArchivedEvent>()

            every { archivedEventJpaRepository.saveAll(emptyArchivedEvents) } returns emptyArchivedEvents
            every { eventJpaRepository.deleteAllInBatch(emptyEvents) } returns Unit

            // When & Then
            assertDoesNotThrow {
                repository.migrate(emptyEvents, emptyArchivedEvents)
            }

            verify { archivedEventJpaRepository.saveAll(emptyArchivedEvents) }
            verify { eventJpaRepository.deleteAllInBatch(emptyEvents) }
        }

        @Test
        @DisplayName("当保存归档事件失败时应该抛出异常")
        fun `should throw exception when saving archived events fails`() {
            // Given
            val events = listOf(createMockEvent("event1", Event.EventState.DELIVERED))
            val archivedEvents = listOf(mockk<ArchivedEvent>())

            every {
                archivedEventJpaRepository.saveAll(archivedEvents)
            } throws RuntimeException("Database error")

            // When & Then
            assertThrows<RuntimeException> {
                repository.migrate(events, archivedEvents)
            }

            verify { archivedEventJpaRepository.saveAll(archivedEvents) }
            verify(exactly = 0) { eventJpaRepository.deleteAllInBatch(any()) }
        }
    }

    @Nested
    @DisplayName("集成测试")
    inner class IntegrationTest {

        @Test
        @DisplayName("完整的事件生命周期测试")
        fun `should handle complete event lifecycle`() {
            // Given - 创建事件
            val eventRecord = repository.create()
            val eventPayload = UserCreatedEvent("user123", "john", "john@test.com")
            eventRecord.init(eventPayload, "user-service", testTime, Duration.ofHours(1), 3)

            val savedEvent = mockk<Event> {
                every { id } returns 1L
                every { eventUuid } returns "saved-event-id"
                every { svcName } returns "user-service"
                every { eventType } returns "user.created"
                every { createAt } returns testTime
                every { payload } returns eventPayload
                every { nextTryTime } returns testTime.plusMinutes(1)
                every { eventState } returns Event.EventState.INIT
                every { data } returns """{"userId":"user123","name":"john","email":"john@test.com"}"""
                every { dataType } returns "UserCreatedEvent"
                every { exception } returns null
                every { expireAt } returns testTime.plusHours(1)
                every { tryTimes } returns 3
                every { triedTimes } returns 0
                every { lastTryTime } returns null
                every { version } returns 1
            }

            every { eventJpaRepository.saveAndFlush(any()) } returns savedEvent
            every {
                eventJpaRepository.findOne(any<Specification<Event>>())
            } returns Optional.of(savedEvent)

            // When - 保存事件
            repository.save(eventRecord)

            // Then - 验证能够重新获取
            val retrievedRecord = repository.getById("saved-event-id")
            assertNotNull(retrievedRecord)
            assertEquals("user.created", retrievedRecord.eventTopic)
        }

        @Test
        @DisplayName("批量处理事件测试")
        fun `should handle batch processing of events`() {
            // Given
            val svcName = "batch-service"
            val maxNextTryTime = testTime.plusMinutes(30)
            val limit = 5

            val mockEvents = (1..3).map { i ->
                createMockEvent("batch-event-$i", Event.EventState.INIT)
            }
            val mockPage = PageImpl(mockEvents)

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            // When
            val eventRecords = repository.getByNextTryTime(svcName, maxNextTryTime, limit)

            // Then
            assertEquals(3, eventRecords.size)
            eventRecords.forEachIndexed { index, eventRecord ->
                assertTrue(eventRecord is EventRecordImpl)
                val impl = eventRecord as EventRecordImpl
                assertEquals(mockEvents[index], impl.event)
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
                eventJpaRepository.saveAndFlush(any())
            } throws RuntimeException("Database connection failed")

            val eventRecord = EventRecordImpl()
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            // When & Then
            assertThrows<RuntimeException> {
                repository.save(eventRecord)
            }
        }

        @Test
        @DisplayName("应该处理查询超时")
        fun `should handle query timeout`() {
            // Given
            every {
                eventJpaRepository.findOne(any<Specification<Event>>())
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
            val mockEvents = listOf(createMockEvent("event1", Event.EventState.DELIVERED))
            val mockPage = PageImpl(mockEvents)

            every {
                eventJpaRepository.findAll(
                    any<Specification<Event>>(),
                    any<PageRequest>()
                )
            } returns mockPage

            every {
                archivedEventJpaRepository.saveAll(any<List<ArchivedEvent>>())
            } throws RuntimeException("Archive table full")

            // When & Then
            assertThrows<RuntimeException> {
                repository.archiveByExpireAt("test-service", testTime.minusDays(1), 10)
            }
        }
    }

    private fun createMockEvent(eventId: String, state: Event.EventState): Event {
        return mockk<Event> {
            every { id } returns 1L
            every { eventUuid } returns eventId
            every { eventState } returns state
            every { svcName } returns "test-service"
            every { createAt } returns testTime
            every { nextTryTime } returns testTime.plusMinutes(1)
            every { eventType } returns "test.event"
            every { payload } returns TestEvent("test", 12345)
            every { data } returns """{"value":"test","number":12345}"""
            every { dataType } returns "TestEvent"
            every { exception } returns null
            every { expireAt } returns testTime.plusHours(1)
            every { tryTimes } returns 3
            every { triedTimes } returns 0
            every { lastTryTime } returns null
            every { version } returns 1
        }
    }
}
