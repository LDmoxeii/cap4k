package com.only4.cap4k.ddd.console.snowflake

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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("雪花算法控制台服务测试")
class SnowflakeConsoleServiceTest {

    private lateinit var snowflakeConsoleService: SnowflakeConsoleService
    private val mockJdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val mockNamedParameterJdbcTemplate = mockk<NamedParameterJdbcTemplate>(relaxed = true)

    @BeforeEach
    fun setUp() {
        snowflakeConsoleService = SnowflakeConsoleService(mockJdbcTemplate)
        every {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        } returns 4L

        // 通过反射设置private field
        val field = SnowflakeConsoleService::class.java.getDeclaredField("namedParameterJdbcTemplate")
        field.isAccessible = true
        field.set(snowflakeConsoleService, mockNamedParameterJdbcTemplate)
    }

    @Test
    @DisplayName("应该正确初始化NamedParameterJdbcTemplate")
    fun `should initialize NamedParameterJdbcTemplate correctly`() {
        val service = SnowflakeConsoleService(mockJdbcTemplate)
        service.init()
        assertNotNull(service)
    }

    @Test
    @DisplayName("应该正确搜索WorkerId - 带free参数为true")
    fun `should search worker ids with free parameter true`() {
        // Given
        val mockWorkerIdInfo = SnowflakeConsoleService.WorkerIdInfo().apply {
            datacenterId = 1
            workerId = 10
            free = true
            dispatchTo = null
            dispatchAt = null
            expireAt = "2023-01-01 10:00:00"
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns listOf(mockWorkerIdInfo)

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            free = true
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result is PageData<*>)
        assertEquals(4L, result.totalCount)
        assertEquals(1, result.list.size)

        val workerIdInfo = result.list[0] as SnowflakeConsoleService.WorkerIdInfo
        assertEquals(1, workerIdInfo.datacenterId)
        assertEquals(10, workerIdInfo.workerId)
        assertTrue(workerIdInfo.free)

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
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        }
    }

    @Test
    @DisplayName("应该正确搜索WorkerId - 带free参数为false")
    fun `should search worker ids with free parameter false`() {
        // Given
        val mockWorkerIdInfo = SnowflakeConsoleService.WorkerIdInfo().apply {
            datacenterId = 2
            workerId = 5
            free = false
            dispatchTo = "service-instance-1"
            dispatchAt = "2023-01-01 09:00:00"
            expireAt = "2023-01-01 12:00:00"
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns listOf(mockWorkerIdInfo)

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            free = false
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val workerIdInfo = result.list[0] as SnowflakeConsoleService.WorkerIdInfo
        assertEquals(2, workerIdInfo.datacenterId)
        assertEquals(5, workerIdInfo.workerId)
        assertFalse(workerIdInfo.free)
        assertEquals("service-instance-1", workerIdInfo.dispatchTo)
    }

    @Test
    @DisplayName("应该正确搜索WorkerId - 带dispatchTo参数")
    fun `should search worker ids with dispatchTo parameter`() {
        // Given
        val mockWorkerIdInfo = SnowflakeConsoleService.WorkerIdInfo().apply {
            datacenterId = 3
            workerId = 15
            free = false
            dispatchTo = "my-service-instance"
            dispatchAt = "2023-01-01 08:00:00"
            expireAt = "2023-01-01 14:00:00"
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns listOf(mockWorkerIdInfo)

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            dispatchTo = "service"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val workerIdInfo = result.list[0] as SnowflakeConsoleService.WorkerIdInfo
        assertEquals("my-service-instance", workerIdInfo.dispatchTo)
    }

    @Test
    @DisplayName("应该正确搜索WorkerId - 组合参数")
    fun `should search worker ids with combined parameters`() {
        // Given
        val mockWorkerIdInfo = SnowflakeConsoleService.WorkerIdInfo().apply {
            datacenterId = 1
            workerId = 20
            free = false
            dispatchTo = "test-service-instance"
            dispatchAt = "2023-01-01 07:00:00"
            expireAt = "2023-01-01 15:00:00"
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns listOf(mockWorkerIdInfo)

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            free = false
            dispatchTo = "test"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val workerIdInfo = result.list[0] as SnowflakeConsoleService.WorkerIdInfo
        assertEquals(1, workerIdInfo.datacenterId)
        assertEquals(20, workerIdInfo.workerId)
        assertFalse(workerIdInfo.free)
        assertEquals("test-service-instance", workerIdInfo.dispatchTo)
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

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            dispatchTo = "non-existent-service"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(0L, result.totalCount)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确处理所有参数为默认值的搜索")
    fun `should handle search with all default parameters`() {
        // Given
        val mockWorkerIdInfos = listOf(
            SnowflakeConsoleService.WorkerIdInfo().apply {
                datacenterId = 1
                workerId = 1
                free = true
            },
            SnowflakeConsoleService.WorkerIdInfo().apply {
                datacenterId = 1
                workerId = 2
                free = false
                dispatchTo = "service-a"
            }
        )

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns mockWorkerIdInfos

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(2, result.list.size)

        val workerIds = result.list as List<SnowflakeConsoleService.WorkerIdInfo>
        assertEquals(1, workerIds[0].datacenterId)
        assertEquals(1, workerIds[0].workerId)
        assertTrue(workerIds[0].free)

        assertEquals(1, workerIds[1].datacenterId)
        assertEquals(2, workerIds[1].workerId)
        assertFalse(workerIds[1].free)
        assertEquals("service-a", workerIds[1].dispatchTo)
    }

    @Test
    @DisplayName("应该正确处理空的dispatchTo参数")
    fun `should handle empty dispatchTo parameter`() {
        // Given
        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns emptyList()

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            dispatchTo = ""  // 空字符串应该被忽略
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确处理null的dispatchTo参数")
    fun `should handle null dispatchTo parameter`() {
        // Given
        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns emptyList()

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            dispatchTo = null  // null应该被忽略
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("搜索参数应该正确设置默认值")
    fun `SearchParam should set default values correctly`() {
        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }
        assertEquals(1, searchParam.pageNum)
        assertEquals(20, searchParam.pageSize)
    }

    @Test
    @DisplayName("WorkerIdInfo应该正确设置默认值")
    fun `WorkerIdInfo should set default values correctly`() {
        val workerIdInfo = SnowflakeConsoleService.WorkerIdInfo()
        assertEquals(0, workerIdInfo.datacenterId)
        assertEquals(0, workerIdInfo.workerId)
        assertFalse(workerIdInfo.free)
    }

    @Test
    @DisplayName("应该正确处理大量WorkerId数据")
    fun `should handle large amount of worker id data`() {
        // Given
        val mockWorkerIdInfos = (1..50).map { i ->
            SnowflakeConsoleService.WorkerIdInfo().apply {
                datacenterId = i % 5
                workerId = i
                free = i % 2 == 0
                dispatchTo = if (i % 2 == 0) null else "service-$i"
            }
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SnowflakeConsoleService.WorkerIdInfo>>()
            )
        } returns mockWorkerIdInfos.take(20)  // 模拟分页，只返回前20条

        val searchParam = SnowflakeConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = snowflakeConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(20, result.list.size)

        val workerIds = result.list as List<SnowflakeConsoleService.WorkerIdInfo>
        // 验证前几条数据
        assertEquals(1, workerIds[0].datacenterId)
        assertEquals(1, workerIds[0].workerId)
        assertFalse(workerIds[0].free)
        assertEquals("service-1", workerIds[0].dispatchTo)

        assertEquals(2, workerIds[1].datacenterId)
        assertEquals(2, workerIds[1].workerId)
        assertTrue(workerIds[1].free)
    }
}
