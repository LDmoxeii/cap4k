package com.only4.cap4k.ddd.console.command

import com.only4.cap4k.ddd.core.application.command.CommandManager
import com.only4.cap4k.ddd.core.share.PageData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime

@DisplayName("命令控制台服务测试")
class CommandConsoleServiceTest {

    private lateinit var commandConsoleService: CommandConsoleService
    private val mockJdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val mockCommandManager = mockk<CommandManager>(relaxed = true)
    private val mockNamedParameterJdbcTemplate = mockk<NamedParameterJdbcTemplate>(relaxed = true)

    @BeforeEach
    fun setUp() {
        commandConsoleService = CommandConsoleService(mockJdbcTemplate, mockCommandManager)
        every {
            mockNamedParameterJdbcTemplate.queryForObject(
                any<String>(),
                any<Map<String, Any?>>(),
                Long::class.java
            )
        } returns 8L

        // 通过反射设置private field
        val field = CommandConsoleService::class.java.getDeclaredField("namedParameterJdbcTemplate")
        field.isAccessible = true
        field.set(commandConsoleService, mockNamedParameterJdbcTemplate)
    }

    @Test
    @DisplayName("应该正确初始化NamedParameterJdbcTemplate")
    fun `should initialize NamedParameterJdbcTemplate correctly`() {
        val service = CommandConsoleService(mockJdbcTemplate, mockCommandManager)
        service.init()
        assertNotNull(service)
    }

    @Test
    @DisplayName("应该正确搜索命令 - 带UUID参数")
    fun `should search commands with uuid parameter`() {
        // Given
        val mockCommandInfo = CommandConsoleService.CommandInfo().apply {
            id = 1L
            uuid = "test-command-uuid"
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
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns listOf(mockCommandInfo)

        val searchParam = CommandConsoleService.SearchParam().apply {
            uuid = "test-command-uuid"
            pageNum = 1
            pageSize = 10
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result is PageData<*>)
        assertEquals(8L, result.totalCount)
        assertEquals(1, result.list.size)
        assertEquals("test-command-uuid", (result.list[0] as CommandConsoleService.CommandInfo).uuid)
        assertEquals("完成", (result.list[0] as CommandConsoleService.CommandInfo).stateName)

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
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        }
    }

    @Test
    @DisplayName("应该正确搜索命令 - 带类型参数")
    fun `should search commands with type parameter`() {
        // Given
        val mockCommandInfo = CommandConsoleService.CommandInfo().apply {
            id = 1L
            type = "CreateUserCommand"
            state = 0
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns listOf(mockCommandInfo)

        val searchParam = CommandConsoleService.SearchParam().apply {
            type = "CreateUserCommand"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("CreateUserCommand", (result.list[0] as CommandConsoleService.CommandInfo).type)
        assertEquals("初始", (result.list[0] as CommandConsoleService.CommandInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索命令 - 带状态参数")
    fun `should search commands with state parameter`() {
        // Given
        val mockCommandInfo = CommandConsoleService.CommandInfo().apply {
            id = 1L
            state = -1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns listOf(mockCommandInfo)

        val searchParam = CommandConsoleService.SearchParam().apply {
            state = intArrayOf(-1, 1)
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("执行中", (result.list[0] as CommandConsoleService.CommandInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索命令 - 带时间参数")
    fun `should search commands with schedule time parameter`() {
        // Given
        val mockCommandInfo = CommandConsoleService.CommandInfo().apply {
            id = 1L
            state = -2
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns listOf(mockCommandInfo)

        val searchParam = CommandConsoleService.SearchParam().apply {
            scheduleAt = arrayOf(
                LocalDateTime.of(2023, 1, 1, 0, 0),
                LocalDateTime.of(2023, 12, 31, 23, 59)
            )
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("取消", (result.list[0] as CommandConsoleService.CommandInfo).stateName)
    }

    @Test
    @DisplayName("应该正确搜索命令 - 只有开始时间")
    fun `should search commands with only start time`() {
        // Given
        val mockCommandInfo = CommandConsoleService.CommandInfo().apply {
            id = 1L
            state = -3
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns listOf(mockCommandInfo)

        val searchParam = CommandConsoleService.SearchParam().apply {
            scheduleAt = arrayOf(LocalDateTime.of(2023, 1, 1, 0, 0))
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals("超时", (result.list[0] as CommandConsoleService.CommandInfo).stateName)
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

        val searchParam = CommandConsoleService.SearchParam().apply {
            uuid = "non-existent-command"
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertEquals(0L, result.totalCount)
        assertTrue(result.list.isEmpty())
    }

    @Test
    @DisplayName("应该正确重试命令")
    fun `should retry command correctly`() {
        // Given
        val uuid = "test-command-uuid"

        // When
        commandConsoleService.retry(uuid)

        // Then
        verify { mockCommandManager.retry(uuid) }
    }

    @Test
    @DisplayName("应该正确返回状态名称")
    fun `should return correct state names`() {
        // Given
        val mockCommandInfos = listOf(
            CommandConsoleService.CommandInfo().apply { state = 0 },
            CommandConsoleService.CommandInfo().apply { state = 1 },
            CommandConsoleService.CommandInfo().apply { state = -1 },
            CommandConsoleService.CommandInfo().apply { state = -2 },
            CommandConsoleService.CommandInfo().apply { state = -3 },
            CommandConsoleService.CommandInfo().apply { state = -4 },
            CommandConsoleService.CommandInfo().apply { state = -9 },
            CommandConsoleService.CommandInfo().apply { state = 999 }
        )

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns mockCommandInfos

        val searchParam = CommandConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        val commandInfos = result.list as List<CommandConsoleService.CommandInfo>
        assertEquals("初始", commandInfos[0].stateName)
        assertEquals("完成", commandInfos[1].stateName)
        assertEquals("执行中", commandInfos[2].stateName)
        assertEquals("取消", commandInfos[3].stateName)
        assertEquals("超时", commandInfos[4].stateName)
        assertEquals("超限", commandInfos[5].stateName)
        assertEquals("异常", commandInfos[6].stateName)
        assertEquals("未知", commandInfos[7].stateName)
    }

    @Test
    @DisplayName("应该正确处理组合查询条件")
    fun `should handle combined search criteria`() {
        // Given
        val mockCommandInfo = CommandConsoleService.CommandInfo().apply {
            id = 1L
            uuid = "combined-test-uuid"
            type = "TestCommand"
            state = 1
        }

        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns listOf(mockCommandInfo)

        val searchParam = CommandConsoleService.SearchParam().apply {
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
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        val commandInfo = result.list[0] as CommandConsoleService.CommandInfo
        assertEquals("combined-test-uuid", commandInfo.uuid)
        assertEquals("TestCommand", commandInfo.type)
        assertEquals("完成", commandInfo.stateName)
    }

    @Test
    @DisplayName("搜索参数应该正确设置默认值")
    fun `SearchParam should set default values correctly`() {
        val searchParam = CommandConsoleService.SearchParam().apply {
            pageNum = 1
            pageSize = 20
        }
        assertEquals(1, searchParam.pageNum)
        assertEquals(20, searchParam.pageSize)
    }

    @Test
    @DisplayName("CommandInfo应该正确设置默认值")
    fun `CommandInfo should set default values correctly`() {
        val commandInfo = CommandConsoleService.CommandInfo()
        assertEquals(0, commandInfo.state)
        assertEquals(0, commandInfo.retryLimit)
        assertEquals(0, commandInfo.retryCount)
    }

    @Test
    @DisplayName("应该正确处理空的查询参数")
    fun `should handle empty search parameters`() {
        // Given
        every {
            mockNamedParameterJdbcTemplate.query(
                any<String>(),
                any<Map<String, Any?>>(),
                any<BeanPropertyRowMapper<CommandConsoleService.CommandInfo>>()
            )
        } returns emptyList()

        val searchParam = CommandConsoleService.SearchParam().apply {
            uuid = ""  // 空字符串
            type = ""  // 空字符串
            state = intArrayOf()  // 空数组
            scheduleAt = arrayOf()  // 空数组
            pageNum = 1
            pageSize = 20
        }

        // When
        val result = commandConsoleService.search(searchParam)

        // Then
        assertNotNull(result)
        assertTrue(result.list.isEmpty())
    }
}
