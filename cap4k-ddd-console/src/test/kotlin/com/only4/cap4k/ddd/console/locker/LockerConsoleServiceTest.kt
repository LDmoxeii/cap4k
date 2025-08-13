package com.only4.cap4k.ddd.console.locker

import com.only4.cap4k.ddd.core.share.PageData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

@DisplayName("锁控制台服务测试")
class LockerConsoleServiceTest {

    private lateinit var lockerConsoleService: LockerConsoleService
    private val mockJdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val mockNamedParameterJdbcTemplate = mockk<NamedParameterJdbcTemplate>(relaxed = true)

    @BeforeEach
    fun setUp() {
        lockerConsoleService = LockerConsoleService(mockJdbcTemplate)
        every {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        } returns 3L

        // 通过反射设置private field
        val field = LockerConsoleService::class.java.getDeclaredField("namedParameterJdbcTemplate")
        field.isAccessible = true
        field.set(lockerConsoleService, mockNamedParameterJdbcTemplate)
    }

    @Test
    @DisplayName("应该正确初始化NamedParameterJdbcTemplate")
    fun `should initialize NamedParameterJdbcTemplate correctly`() {
        val service = LockerConsoleService(mockJdbcTemplate)
        service.init()
        assertNotNull(service)
    }

    @Test
    @DisplayName("应该正确搜索锁信息 - 带名称参数")
    fun `should search lockers with name parameter`() {
        // Given
        val mockLockerInfo = LockerConsoleService.LockerInfo().apply {
            name = "test-lock"
            lock = true
            lockAt = "2023-01-01 10:00:00"
            unlockAt = "2023-01-01 11:00:00"
            pwd = "secret"
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<LockerConsoleService.LockerInfo>>()
            )
        } returns listOf(mockLockerInfo)

        val searchParam = LockerConsoleService.SearchParam().apply {
            name = "test"
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = lockerConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result is PageData<*>)
        assertEquals(3L, result.totalCount)
        assertEquals(1, result.list.size)
        assertEquals("test-lock", (result.list[0] as LockerConsoleService.LockerInfo).name)

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
                any<BeanPropertyRowMapper<LockerConsoleService.LockerInfo>>()
            )
        }
    }

    @Test
    @DisplayName("应该正确搜索锁信息 - 带锁定状态参数为true")
    fun `should search lockers with lock status true`() {
        // Given
        val mockLockerInfo = LockerConsoleService.LockerInfo().apply {
            name = "locked-resource"
            lock = true
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<LockerConsoleService.LockerInfo>>()
            )
        } returns listOf(mockLockerInfo)

        val searchParam = LockerConsoleService.SearchParam().apply {
            lock = true
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = lockerConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue((result.list[0] as LockerConsoleService.LockerInfo).lock)
    }

    @Test
    @DisplayName("应该正确搜索锁信息 - 带锁定状态参数为false")
    fun `should search lockers with lock status false`() {
        // Given
        val mockLockerInfo = LockerConsoleService.LockerInfo().apply {
            name = "unlocked-resource"
            lock = false
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<LockerConsoleService.LockerInfo>>()
            )
        } returns listOf(mockLockerInfo)

        val searchParam = LockerConsoleService.SearchParam().apply {
            lock = false
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = lockerConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertFalse((result.list[0] as LockerConsoleService.LockerInfo).lock)
    }

    @Test
    @DisplayName("应该正确搜索锁信息 - 组合参数")
    fun `should search lockers with combined parameters`() {
        // Given
        val mockLockerInfo = LockerConsoleService.LockerInfo().apply {
            name = "test-locked-resource"
            lock = true
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<LockerConsoleService.LockerInfo>>()
            )
        } returns listOf(mockLockerInfo)

        val searchParam = LockerConsoleService.SearchParam().apply {
            name = "test"
            lock = true
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = lockerConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val lockerInfo = result.list[0] as LockerConsoleService.LockerInfo
        assertEquals("test-locked-resource", lockerInfo.name)
        assertTrue(lockerInfo.lock)
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

        val searchParam = LockerConsoleService.SearchParam().apply {
            name = "non-existent"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = lockerConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(0L, result.totalCount)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确解锁成功")
    fun `should unlock successfully`() {
        // Given
        val name = "test-lock"
        val pwd = "correct-password"
        every {
            mockJdbcTemplate.update(
                "UPDATE __locker SET unlock_at=now() WHERE name=? AND pwd=? AND unlock_at>now()",
                name, pwd
            )
        } returns 1

        // When
        val result = lockerConsoleService.unlock(name, pwd)

        // Then
        assertTrue(result)
        verify {
            mockJdbcTemplate.update(
                "UPDATE __locker SET unlock_at=now() WHERE name=? AND pwd=? AND unlock_at>now()",
                name, pwd
            )
        }
    }

    @Test
    @DisplayName("应该正确处理解锁失败")
    fun `should handle unlock failure`() {
        // Given
        val name = "test-lock"
        val pwd = "wrong-password"
        every {
            mockJdbcTemplate.update(
                "UPDATE __locker SET unlock_at=now() WHERE name=? AND pwd=? AND unlock_at>now()",
                name, pwd
            )
        } returns 0

        // When
        val result = lockerConsoleService.unlock(name, pwd)

        // Then
        assertFalse(result)
        verify {
            mockJdbcTemplate.update(
                "UPDATE __locker SET unlock_at=now() WHERE name=? AND pwd=? AND unlock_at>now()",
                name, pwd
            )
        }
    }

    @Test
    @DisplayName("搜索参数应该正确设置默认值")
    fun `SearchParam should set default values correctly`() {
        val searchParam = LockerConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }
        assertEquals(1, searchParam.pageNum)
        assertEquals(20, searchParam.pageSize)
    }

    @Test
    @DisplayName("LockerInfo应该正确设置默认值")
    fun `LockerInfo should set default values correctly`() {
        val lockerInfo = LockerConsoleService.LockerInfo()
        assertFalse(lockerInfo.lock)
    }

    @Test
    @DisplayName("应该正确处理空名称参数")
    fun `should handle empty name parameter`() {
        // Given
        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<LockerConsoleService.LockerInfo>>()
            )
        } returns emptyList()

        val searchParam = LockerConsoleService.SearchParam().apply {
            name = ""  // 空字符串应该被忽略
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = lockerConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确处理null名称参数")
    fun `should handle null name parameter`() {
        // Given
        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<LockerConsoleService.LockerInfo>>()
            )
        } returns emptyList()

        val searchParam = LockerConsoleService.SearchParam().apply {
            name = null  // null应该被忽略
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = lockerConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result.list.isEmpty())
    }
}
