package com.only4.cap4k.ddd.console.saga

import com.only4.cap4k.ddd.core.application.saga.SagaManager
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

@DisplayName("Saga控制台服务测试")
class SagaConsoleServiceTest {

    private lateinit var sagaConsoleService: SagaConsoleService
    private val mockJdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val mockSagaManager = mockk<SagaManager>(relaxed = true)
    private val mockNamedParameterJdbcTemplate = mockk<NamedParameterJdbcTemplate>(relaxed = true)

    @BeforeEach
    fun setUp() {
        sagaConsoleService = SagaConsoleService(mockJdbcTemplate, mockSagaManager)
        every {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        } returns 6L

        // 通过反射设置private field
        val field = SagaConsoleService::class.java.getDeclaredField("namedParameterJdbcTemplate")
        field.isAccessible = true
        field.set(sagaConsoleService, mockNamedParameterJdbcTemplate)
    }

    @Test
    @DisplayName("应该正确初始化NamedParameterJdbcTemplate")
    fun `should initialize NamedParameterJdbcTemplate correctly`() {
        val service = SagaConsoleService(mockJdbcTemplate, mockSagaManager)
        service.init()
        assertNotNull(service)
    }

    @Test
    @DisplayName("应该正确搜索Saga - 带UUID参数")
    fun `should search sagas with uuid parameter`() {
        // Given
        val mockSagaInfo = SagaConsoleService.SagaInfo().apply {
            id = 1L
            uuid = "test-saga-uuid"
            type = "OrderSaga"
            service = "OrderService"
            payload = "{\"orderId\": \"123\"}"
            result = "{\"status\": \"completed\"}"
            state = 1
        }

        val mockProcessInfo = SagaConsoleService.SagaProcessInfo().apply {
            code = "PROCESS_PAYMENT"
            payload = "{\"amount\": 100}"
            result = "{\"transactionId\": \"tx123\"}"
            retryCount = 0
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns listOf(mockSagaInfo)

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        } returns listOf(mockProcessInfo)

        val searchParam = SagaConsoleService.SearchParam().apply {
            uuid = "test-saga-uuid"
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result is PageData<*>)
        assertEquals(6L, result.totalCount)
        assertEquals(1, result.list.size)

        val sagaInfo = result.list[0] as SagaConsoleService.SagaInfo
        assertEquals("test-saga-uuid", sagaInfo.uuid)
        assertEquals("完成", sagaInfo.stateName)
        assertEquals(1, sagaInfo.processes?.size)
        assertEquals("PROCESS_PAYMENT", sagaInfo.processes?.get(0)?.code)

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
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        }
        verify {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        }
    }

    @Test
    @DisplayName("应该正确搜索Saga - 带类型参数")
    fun `should search sagas with type parameter`() {
        // Given
        val mockSagaInfo = SagaConsoleService.SagaInfo().apply {
            id = 1L
            type = "PaymentSaga"
            state = 0
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns listOf(mockSagaInfo)

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        } returns emptyList()

        val searchParam = SagaConsoleService.SearchParam().apply {
            type = "PaymentSaga"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("PaymentSaga", (result.list[0] as SagaConsoleService.SagaInfo).type)
        assertEquals("初始", (result.list[0] as SagaConsoleService.SagaInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索Saga - 带状态参数")
    fun `should search sagas with state parameter`() {
        // Given
        val mockSagaInfo = SagaConsoleService.SagaInfo().apply {
            id = 1L
            state = -1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns listOf(mockSagaInfo)

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        } returns emptyList()

        val searchParam = SagaConsoleService.SearchParam().apply {
            state = intArrayOf(-1, 1)
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("执行中", (result.list[0] as SagaConsoleService.SagaInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索Saga - 带时间参数")
    fun `should search sagas with schedule time parameter`() {
        // Given
        val mockSagaInfo = SagaConsoleService.SagaInfo().apply {
            id = 1L
            state = -2
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns listOf(mockSagaInfo)

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        } returns emptyList()

        val searchParam = SagaConsoleService.SearchParam().apply {
            scheduleAt = arrayOf(
                LocalDateTime.of(2023, 1, 1, 0, 0),
                LocalDateTime.of(2023, 12, 31, 23, 59)
            )
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("取消", (result.list[0] as SagaConsoleService.SagaInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索Saga - 只有开始时间")
    fun `should search sagas with only start time`() {
        // Given
        val mockSagaInfo = SagaConsoleService.SagaInfo().apply {
            id = 1L
            state = -3
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns listOf(mockSagaInfo)

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        } returns emptyList()

        val searchParam = SagaConsoleService.SearchParam().apply {
            scheduleAt = arrayOf(LocalDateTime.of(2023, 1, 1, 0, 0))
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("超时", (result.list[0] as SagaConsoleService.SagaInfo).stateName)
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

        val searchParam = SagaConsoleService.SearchParam().apply {
            uuid = "non-existent-saga"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(0L, result.totalCount)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确处理Saga进程为空的情况")
    fun `should handle empty saga processes`() {
        // Given
        val mockSagaInfo = SagaConsoleService.SagaInfo().apply {
            id = null  // null id 应该返回空的进程列表
            uuid = "test-saga-uuid"
            state = 1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns listOf(mockSagaInfo)

        val searchParam = SagaConsoleService.SearchParam().apply {
            uuid = "test-saga-uuid"
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val sagaInfo = result.list[0] as SagaConsoleService.SagaInfo
        assertTrue(sagaInfo.processes?.isEmpty() == true)
    }

    @Test
    @DisplayName("应该正确重试Saga")
    fun `should retry saga correctly`() {
        // Given
        val uuid = "test-saga-uuid"

        // When
        sagaConsoleService.retry(uuid)

        // Then
        verify { mockSagaManager.retry(uuid) }
    }

    @Test
    @DisplayName("应该正确返回状态名称")
    fun `should return correct state names`() {
        // Given
        val mockSagaInfos = listOf(
            SagaConsoleService.SagaInfo().apply { id = 1L; state = 0 },
            SagaConsoleService.SagaInfo().apply { id = 2L; state = 1 },
            SagaConsoleService.SagaInfo().apply { id = 3L; state = -1 },
            SagaConsoleService.SagaInfo().apply { id = 4L; state = -2 },
            SagaConsoleService.SagaInfo().apply { id = 5L; state = -3 },
            SagaConsoleService.SagaInfo().apply { id = 6L; state = -4 },
            SagaConsoleService.SagaInfo().apply { id = 7L; state = -9 },
            SagaConsoleService.SagaInfo().apply { id = 8L; state = 999 }
        )

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns mockSagaInfos

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        } returns emptyList()

        val searchParam = SagaConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        val sagaInfos = result.list as List<SagaConsoleService.SagaInfo>
        assertEquals("初始", sagaInfos[0].stateName)
        assertEquals("完成", sagaInfos[1].stateName)
        assertEquals("执行中", sagaInfos[2].stateName)
        assertEquals("取消", sagaInfos[3].stateName)
        assertEquals("超时", sagaInfos[4].stateName)
        assertEquals("超限", sagaInfos[5].stateName)
        assertEquals("异常", sagaInfos[6].stateName)
        assertEquals("未知", sagaInfos[7].stateName)
    }

    @Test
    @DisplayName("应该正确处理多个Saga进程")
    fun `should handle multiple saga processes`() {
        // Given
        val mockSagaInfo = SagaConsoleService.SagaInfo().apply {
            id = 1L
            uuid = "test-saga-uuid"
            state = 1
        }

        val mockProcessInfos = listOf(
            SagaConsoleService.SagaProcessInfo().apply {
                code = "PROCESS_PAYMENT"
                payload = "{\"amount\": 100}"
                result = "{\"transactionId\": \"tx123\"}"
                retryCount = 0
            },
            SagaConsoleService.SagaProcessInfo().apply {
                code = "SEND_NOTIFICATION"
                payload = "{\"userId\": \"user123\"}"
                result = "{\"notificationId\": \"notif456\"}"
                retryCount = 1
            }
        )

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaInfo>>()
            )
        } returns listOf(mockSagaInfo)

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<BeanPropertyRowMapper<SagaConsoleService.SagaProcessInfo>>()
            )
        } returns mockProcessInfos

        val searchParam = SagaConsoleService.SearchParam().apply {
            uuid = "test-saga-uuid"
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = sagaConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val sagaInfo = result.list[0] as SagaConsoleService.SagaInfo
        assertEquals(2, sagaInfo.processes?.size)
        assertEquals("PROCESS_PAYMENT", sagaInfo.processes?.get(0)?.code)
        assertEquals("SEND_NOTIFICATION", sagaInfo.processes?.get(1)?.code)
    }

    @Test
    @DisplayName("搜索参数应该正确设置默认值")
    fun `SearchParam should set default values correctly`() {
        val searchParam = SagaConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }
        assertEquals(1, searchParam.pageNum)
        assertEquals(20, searchParam.pageSize)
    }

    @Test
    @DisplayName("SagaInfo应该正确设置默认值")
    fun `SagaInfo should set default values correctly`() {
        val sagaInfo = SagaConsoleService.SagaInfo()
        assertEquals(0, sagaInfo.state)
        assertEquals(0, sagaInfo.retryLimit)
        assertEquals(0, sagaInfo.retryCount)
    }

    @Test
    @DisplayName("SagaProcessInfo应该正确设置默认值")
    fun `SagaProcessInfo should set default values correctly`() {
        val processInfo = SagaConsoleService.SagaProcessInfo()
        assertEquals(0, processInfo.retryCount)
    }
}
