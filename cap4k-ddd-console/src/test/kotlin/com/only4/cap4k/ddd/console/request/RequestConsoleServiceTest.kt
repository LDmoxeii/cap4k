package com.only4.cap4k.ddd.console.request

import com.only4.cap4k.ddd.core.application.RequestManager
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

@DisplayName("请求控制台服务测试")
class RequestConsoleServiceTest {

    private lateinit var requestConsoleService: RequestConsoleService
    private val mockJdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val mockRequestManager = mockk<RequestManager>(relaxed = true)
    private val mockNamedParameterJdbcTemplate = mockk<NamedParameterJdbcTemplate>(relaxed = true)

    @BeforeEach
    fun setUp() {
        requestConsoleService = RequestConsoleService(mockJdbcTemplate, mockRequestManager)
        every {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        } returns 8L

        // 通过反射设置private field
        val field = RequestConsoleService::class.java.getDeclaredField("namedParameterJdbcTemplate")
        field.isAccessible = true
        field.set(requestConsoleService, mockNamedParameterJdbcTemplate)
    }

    @Test
    @DisplayName("应该正确初始化NamedParameterJdbcTemplate")
    fun `should initialize NamedParameterJdbcTemplate correctly`() {
        val service = RequestConsoleService(mockJdbcTemplate, mockRequestManager)
        service.init()
        assertNotNull(service)
    }

    @Test
    @DisplayName("应该正确搜索请求 - 带UUID参数")
    fun `should search requests with uuid parameter`() {
        // Given
        val mockRequestInfo = RequestConsoleService.RequestInfo().apply {
            id = 1L
            uuid = "test-request-uuid"
            type = "TestCommand"
            service = "TestService"
            payload = "{\"test\": \"data\"}"
            result = "{\"result\": \"success\"}"
            state = 1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns listOf(mockRequestInfo)

        val searchParam = RequestConsoleService.SearchParam().apply {
            uuid = "test-request-uuid"
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result is PageData<*>)
        assertEquals(8L, result.totalCount)
        assertEquals(1, result.list.size)
        assertEquals("test-request-uuid", (result.list[0] as RequestConsoleService.RequestInfo).uuid)
        assertEquals("完成", (result.list[0] as RequestConsoleService.RequestInfo).stateName)

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
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        }
    }

    @Test
    @DisplayName("应该正确搜索请求 - 带类型参数")
    fun `should search requests with type parameter`() {
        // Given
        val mockRequestInfo = RequestConsoleService.RequestInfo().apply {
            id = 1L
            type = "CreateUserCommand"
            state = 0
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns listOf(mockRequestInfo)

        val searchParam = RequestConsoleService.SearchParam().apply {
            type = "CreateUserCommand"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("CreateUserCommand", (result.list[0] as RequestConsoleService.RequestInfo).type)
        assertEquals("初始", (result.list[0] as RequestConsoleService.RequestInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索请求 - 带状态参数")
    fun `should search requests with state parameter`() {
        // Given
        val mockRequestInfo = RequestConsoleService.RequestInfo().apply {
            id = 1L
            state = -1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns listOf(mockRequestInfo)

        val searchParam = RequestConsoleService.SearchParam().apply {
            state = intArrayOf(-1, 1)
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("执行中", (result.list[0] as RequestConsoleService.RequestInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索请求 - 带时间参数")
    fun `should search requests with schedule time parameter`() {
        // Given
        val mockRequestInfo = RequestConsoleService.RequestInfo().apply {
            id = 1L
            state = -2
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns listOf(mockRequestInfo)

        val searchParam = RequestConsoleService.SearchParam().apply {
            scheduleAt = arrayOf(
                LocalDateTime.of(2023, 1, 1, 0, 0),
                LocalDateTime.of(2023, 12, 31, 23, 59)
            )
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("取消", (result.list[0] as RequestConsoleService.RequestInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索请求 - 只有开始时间")
    fun `should search requests with only start time`() {
        // Given
        val mockRequestInfo = RequestConsoleService.RequestInfo().apply {
            id = 1L
            state = -3
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns listOf(mockRequestInfo)

        val searchParam = RequestConsoleService.SearchParam().apply {
            scheduleAt = arrayOf(LocalDateTime.of(2023, 1, 1, 0, 0))
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("超时", (result.list[0] as RequestConsoleService.RequestInfo).stateName)
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

        val searchParam = RequestConsoleService.SearchParam().apply {
            uuid = "non-existent-request"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(0L, result.totalCount)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确重试请求")
    fun `should retry request correctly`() {
        // Given
        val uuid = "test-request-uuid"

        // When
        requestConsoleService.retry(uuid)

        // Then
        verify { mockRequestManager.retry(uuid) }
    }

    @Test
    @DisplayName("应该正确返回状态名称")
    fun `should return correct state names`() {
        // Given
        val mockRequestInfos = listOf(
            RequestConsoleService.RequestInfo().apply { state = 0 },
            RequestConsoleService.RequestInfo().apply { state = 1 },
            RequestConsoleService.RequestInfo().apply { state = -1 },
            RequestConsoleService.RequestInfo().apply { state = -2 },
            RequestConsoleService.RequestInfo().apply { state = -3 },
            RequestConsoleService.RequestInfo().apply { state = -4 },
            RequestConsoleService.RequestInfo().apply { state = -9 },
            RequestConsoleService.RequestInfo().apply { state = 999 }
        )

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns mockRequestInfos

        val searchParam = RequestConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        val requestInfos = result.list as List<RequestConsoleService.RequestInfo>
        assertEquals("初始", requestInfos[0].stateName)
        assertEquals("完成", requestInfos[1].stateName)
        assertEquals("执行中", requestInfos[2].stateName)
        assertEquals("取消", requestInfos[3].stateName)
        assertEquals("超时", requestInfos[4].stateName)
        assertEquals("超限", requestInfos[5].stateName)
        assertEquals("异常", requestInfos[6].stateName)
        assertEquals("未知", requestInfos[7].stateName)
    }

    @Test
    @DisplayName("应该正确处理组合查询条件")
    fun `should handle combined search criteria`() {
        // Given
        val mockRequestInfo = RequestConsoleService.RequestInfo().apply {
            id = 1L
            uuid = "combined-test-uuid"
            type = "TestCommand"
            state = 1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns listOf(mockRequestInfo)

        val searchParam = RequestConsoleService.SearchParam().apply {
            uuid = "combined-test-uuid"
            type = "TestCommand"
            state = intArrayOf(1)
            scheduleAt = arrayOf(
                LocalDateTime.of(2023, 1, 1, 0, 0),
                LocalDateTime.of(2023, 12, 31, 23, 59)
            )
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val requestInfo = result.list[0] as RequestConsoleService.RequestInfo
        assertEquals("combined-test-uuid", requestInfo.uuid)
        assertEquals("TestCommand", requestInfo.type)
        assertEquals("完成", requestInfo.stateName)
    }

    @Test
    @DisplayName("搜索参数应该正确设置默认值")
    fun `SearchParam should set default values correctly`() {
        val searchParam = RequestConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }
        assertEquals(1, searchParam.pageNum)
        assertEquals(20, searchParam.pageSize)
    }

    @Test
    @DisplayName("RequestInfo应该正确设置默认值")
    fun `RequestInfo should set default values correctly`() {
        val requestInfo = RequestConsoleService.RequestInfo()
        assertEquals(0, requestInfo.state)
        assertEquals(0, requestInfo.retryLimit)
        assertEquals(0, requestInfo.retryCount)
    }

    @Test
    @DisplayName("应该正确处理空的查询参数")
    fun `should handle empty search parameters`() {
        // Given
        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<RequestConsoleService.RequestInfo>>()
            )
        } returns emptyList()

        val searchParam = RequestConsoleService.SearchParam().apply {
            uuid = ""  // 空字符串
            type = ""  // 空字符串
            state = intArrayOf()  // 空数组
            scheduleAt = arrayOf()  // 空数组
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = requestConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result.list.isEmpty())
    }
}
