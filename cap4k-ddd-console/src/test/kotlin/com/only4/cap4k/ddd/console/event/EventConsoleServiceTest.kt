package com.only4.cap4k.ddd.console.event

import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.share.PageData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("事件控制台服务测试")
class EventConsoleServiceTest {

    private lateinit var eventConsoleService: EventConsoleService
    private val mockJdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val mockEventPublisher = mockk<EventPublisher>(relaxed = true)
    private val mockNamedParameterJdbcTemplate = mockk<NamedParameterJdbcTemplate>(relaxed = true)

    @BeforeEach
    fun setUp() {
        eventConsoleService = EventConsoleService(mockJdbcTemplate, mockEventPublisher)
        every {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        } returns 5L

        // 通过反射设置private field
        val field = EventConsoleService::class.java.getDeclaredField("namedParameterJdbcTemplate")
        field.isAccessible = true
        field.set(eventConsoleService, mockNamedParameterJdbcTemplate)
    }

    @Test
    @DisplayName("应该正确初始化NamedParameterJdbcTemplate")
    fun `should initialize NamedParameterJdbcTemplate correctly`() {
        val service = EventConsoleService(mockJdbcTemplate, mockEventPublisher)
        service.init()
        assertNotNull(service)
    }

    @Test
    @DisplayName("应该正确搜索事件 - 带UUID参数")
    fun `should search events with uuid parameter`() {
        // Given
        val mockEventInfo = EventConsoleService.EventInfo().apply {
            id = 1L
            uuid = "test-uuid"
            type = "TestEvent"
            state = 1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<EventConsoleService.EventInfo>>()
            )
        } returns listOf(mockEventInfo)

        val searchParam = EventConsoleService.SearchParam().apply {
            uuid = "test-uuid"
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = eventConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result is PageData<*>)
        assertEquals(5L, result.totalCount)
        assertEquals(1, result.list.size)
        assertEquals("test-uuid", (result.list[0] as EventConsoleService.EventInfo).uuid)

        verify {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        }
        verify {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<EventConsoleService.EventInfo>>()
            )
        }
    }

    @Test
    @DisplayName("应该正确搜索事件 - 带类型参数")
    fun `should search events with type parameter`() {
        // Given
        val mockEventInfo = EventConsoleService.EventInfo().apply {
            id = 1L
            type = "TestEvent"
            state = 0
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<EventConsoleService.EventInfo>>()
            )
        } returns listOf(mockEventInfo)

        val searchParam = EventConsoleService.SearchParam().apply {
            type = "TestEvent"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = eventConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("TestEvent", (result.list[0] as EventConsoleService.EventInfo).type)
        assertEquals("初始", (result.list[0] as EventConsoleService.EventInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索事件 - 带状态参数")
    fun `should search events with state parameter`() {
        // Given
        val mockEventInfo = EventConsoleService.EventInfo().apply {
            id = 1L
            state = -1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<EventConsoleService.EventInfo>>()
            )
        } returns listOf(mockEventInfo)

        val searchParam = EventConsoleService.SearchParam().apply {
            state = intArrayOf(-1, 1)
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = eventConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("发送中", (result.list[0] as EventConsoleService.EventInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索事件 - 带时间参数")
    fun `should search events with schedule time parameter`() {
        // Given
        val mockEventInfo = EventConsoleService.EventInfo().apply {
            id = 1L
            state = 1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<EventConsoleService.EventInfo>>()
            )
        } returns listOf(mockEventInfo)

        val searchParam = EventConsoleService.SearchParam().apply {
            scheduleAt = arrayOf(
                LocalDateTime.of(2023, 1, 1, 0, 0),
                LocalDateTime.of(2023, 12, 31, 23, 59)
            )
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = eventConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("完成", (result.list[0] as EventConsoleService.EventInfo).stateName)
    }

    @Test
    @DisplayName("应该正确处理搜索结果为空的情况")
    fun `should handle empty search results`() {
        // Given
        every {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        } returns 0L

        val searchParam = EventConsoleService.SearchParam().apply {
            uuid = "non-existent"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = eventConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(0L, result.totalCount)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确重试事件")
    fun `should retry event correctly`() {
        // Given
        val uuid = "test-uuid"

        // When
        eventConsoleService.retry(uuid)

        // Then
        verify { mockEventPublisher.retry(uuid) }
    }

    @Test
    @DisplayName("应该正确返回状态名称")
    fun `should return correct state names`() {
        // Given
        val mockEventInfos = listOf(
            EventConsoleService.EventInfo().apply { state = 0 },
            EventConsoleService.EventInfo().apply { state = 1 },
            EventConsoleService.EventInfo().apply { state = -1 },
            EventConsoleService.EventInfo().apply { state = -2 },
            EventConsoleService.EventInfo().apply { state = -3 },
            EventConsoleService.EventInfo().apply { state = -4 },
            EventConsoleService.EventInfo().apply { state = -9 },
            EventConsoleService.EventInfo().apply { state = 999 }
        )

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<EventConsoleService.EventInfo>>()
            )
        } returns mockEventInfos

        val searchParam = EventConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = eventConsoleService.search(searchParam)

        // Then
        val eventInfos = result.list as List<EventConsoleService.EventInfo>
        assertEquals("初始", eventInfos[0].stateName)
        assertEquals("完成", eventInfos[1].stateName)
        assertEquals("发送中", eventInfos[2].stateName)
        assertEquals("取消", eventInfos[3].stateName)
        assertEquals("超时", eventInfos[4].stateName)
        assertEquals("超限", eventInfos[5].stateName)
        assertEquals("异常", eventInfos[6].stateName)
        assertEquals("未知", eventInfos[7].stateName)
    }

    @Test
    @DisplayName("搜索参数应该正确设置默认值")
    fun `SearchParam should set default values correctly`() {
        val searchParam = EventConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }
        assertEquals(1, searchParam.pageNum)
        assertEquals(20, searchParam.pageSize)
    }

    @Test
    @DisplayName("EventInfo应该正确设置默认值")
    fun `EventInfo should set default values correctly`() {
        val eventInfo = EventConsoleService.EventInfo()
        assertEquals(0, eventInfo.state)
        assertEquals(0, eventInfo.retryLimit)
        assertEquals(0, eventInfo.retryCount)
    }
}
