package com.only4.cap4k.ddd.console

import com.only4.cap4k.ddd.console.event.EventConsoleService
import com.only4.cap4k.ddd.console.locker.LockerConsoleService
import com.only4.cap4k.ddd.console.request.RequestConsoleService
import com.only4.cap4k.ddd.console.saga.SagaConsoleService
import com.only4.cap4k.ddd.console.snowflake.SnowflakeConsoleService
import com.only4.cap4k.ddd.core.application.RequestManager
import com.only4.cap4k.ddd.core.application.saga.SagaManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.HttpRequestHandler

@DisplayName("DDD控制台自动配置测试")
class DDDConsoleAutoConfigurationTest {

    private val configuration = DDDConsoleAutoConfiguration()
    private val mockJdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val mockEventPublisher = mockk<EventPublisher>(relaxed = true)
    private val mockRequestManager = mockk<RequestManager>(relaxed = true)
    private val mockSagaManager = mockk<SagaManager>(relaxed = true)

    @Test
    @DisplayName("应该正确创建EventConsoleService Bean")
    fun `should create EventConsoleService bean`() {
        val service = configuration.eventConsoleService(mockJdbcTemplate, mockEventPublisher)
        assertNotNull(service)
        assertTrue(service is EventConsoleService)
    }

    @Test
    @DisplayName("应该正确创建RequestConsoleService Bean")
    fun `should create RequestConsoleService bean`() {
        val service = configuration.requestConsoleService(mockJdbcTemplate, mockRequestManager)
        assertNotNull(service)
        assertTrue(service is RequestConsoleService)
    }

    @Test
    @DisplayName("应该正确创建SagaConsoleService Bean")
    fun `should create SagaConsoleService bean`() {
        val service = configuration.sagaConsoleService(mockJdbcTemplate, mockSagaManager)
        assertNotNull(service)
        assertTrue(service is SagaConsoleService)
    }

    @Test
    @DisplayName("应该正确创建LockerConsoleService Bean")
    fun `should create LockerConsoleService bean`() {
        val service = configuration.lockerConsoleService(mockJdbcTemplate)
        assertNotNull(service)
        assertTrue(service is LockerConsoleService)
    }

    @Test
    @DisplayName("应该正确创建SnowflakeConsoleService Bean")
    fun `should create SnowflakeConsoleService bean`() {
        val service = configuration.snowflakeConsoleService(mockJdbcTemplate)
        assertNotNull(service)
        assertTrue(service is SnowflakeConsoleService)
    }

    @Test
    @DisplayName("应该正确创建事件搜索HTTP处理器")
    fun `should create event search HttpRequestHandler`() {
        val mockEventConsoleService = mockk<EventConsoleService>(relaxed = true)
        val handler = configuration.eventSearch(mockEventConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建事件重试HTTP处理器")
    fun `should create event retry HttpRequestHandler`() {
        val mockEventConsoleService = mockk<EventConsoleService>(relaxed = true)
        val handler = configuration.eventRetry(mockEventConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建请求搜索HTTP处理器")
    fun `should create request search HttpRequestHandler`() {
        val mockRequestConsoleService = mockk<RequestConsoleService>(relaxed = true)
        val handler = configuration.requestSearch(mockRequestConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建请求重试HTTP处理器")
    fun `should create request retry HttpRequestHandler`() {
        val mockRequestConsoleService = mockk<RequestConsoleService>(relaxed = true)
        val handler = configuration.requestRetry(mockRequestConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建Saga搜索HTTP处理器")
    fun `should create saga search HttpRequestHandler`() {
        val mockSagaConsoleService = mockk<SagaConsoleService>(relaxed = true)
        val handler = configuration.sagaSearch(mockSagaConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建Saga重试HTTP处理器")
    fun `should create saga retry HttpRequestHandler`() {
        val mockSagaConsoleService = mockk<SagaConsoleService>(relaxed = true)
        val handler = configuration.sagaRetry(mockSagaConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建锁搜索HTTP处理器")
    fun `should create locker search HttpRequestHandler`() {
        val mockLockerConsoleService = mockk<LockerConsoleService>(relaxed = true)
        val handler = configuration.lockerSearch(mockLockerConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建锁解锁HTTP处理器")
    fun `should create locker unlock HttpRequestHandler`() {
        val mockLockerConsoleService = mockk<LockerConsoleService>(relaxed = true)
        val handler = configuration.lockerUnlock(mockLockerConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("应该正确创建雪花算法搜索HTTP处理器")
    fun `should create snowflake search HttpRequestHandler`() {
        val mockSnowflakeConsoleService = mockk<SnowflakeConsoleService>(relaxed = true)
        val handler = configuration.snowflakeSearch(mockSnowflakeConsoleService, "8080", "/app")
        assertNotNull(handler)
        assertTrue(handler is HttpRequestHandler)
    }

    @Test
    @DisplayName("操作响应类应该正确设置属性")
    fun `OperationResponse should set properties correctly`() {
        val response = DDDConsoleAutoConfiguration.OperationResponse(
            success = true,
            message = "成功",
            data = "测试数据"
        )
        assertTrue(response.success)
        assertEquals("成功", response.message)
        assertEquals("测试数据", response.data)
    }
}
