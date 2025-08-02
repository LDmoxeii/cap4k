package com.only4.cap4k.ddd.domain.distributed.snowflake

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("默认雪花WorkerId分配器测试")
class DefaultSnowflakeWorkerIdDispatcherTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var dispatcher: DefaultSnowflakeWorkerIdDispatcher

    private val table = "worker_id"
    private val fieldDatacenterId = "datacenter_id"
    private val fieldWorkerId = "worker_id"
    private val fieldDispatchTo = "dispatch_to"
    private val fieldDispatchAt = "dispatch_at"
    private val fieldExpireAt = "expire_at"
    private val expireMinutes = 30
    private val localHostIdentify = "test-host"
    private val showSql = true

    @BeforeEach
    fun setUp() {
        jdbcTemplate = mockk()
        dispatcher = DefaultSnowflakeWorkerIdDispatcher(
            jdbcTemplate = jdbcTemplate,
            table = table,
            fieldDatacenterId = fieldDatacenterId,
            fieldWorkerId = fieldWorkerId,
            fieldDispatchTo = fieldDispatchTo,
            fieldDispatchAt = fieldDispatchAt,
            fieldExpireAt = fieldExpireAt,
            expireMinutes = expireMinutes,
            localHostIdentify = localHostIdentify,
            showSql = showSql
        )
    }

    @Test
    @DisplayName("测试初始化方法 - 表已满")
    fun testInitWhenTableIsFull() {
        // 模拟表已经有1024条记录
        every { jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java) } returns 1024L

        dispatcher.init()

        // 验证只查询了count，没有执行插入操作
        verify(exactly = 1) { jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java) }
        verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
    }

    @Test
    @DisplayName("测试初始化方法 - 需要插入记录")
    fun testInitWhenTableIsEmpty() {
        // 模拟表为空
        every { jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java) } returns 0L
        every { jdbcTemplate.queryForObject(any<String>(), Long::class.java) } returns 0L
        every { jdbcTemplate.execute(any<String>()) } returns Unit

        dispatcher.init()

        // 验证查询和插入操作
        verify { jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java) }
        verify(exactly = 1024) { jdbcTemplate.execute(any<String>()) } // 32*32 = 1024
    }

    @Test
    @DisplayName("测试初始化方法 - 部分记录存在")
    fun testInitWithPartialRecords() {
        every { jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java) } returns 500L
        every { jdbcTemplate.queryForObject(match<String> { it.contains("WHERE") }, Long::class.java) } returnsMany
                (1..1024).map { if (it <= 500) 1L else 0L }
        every { jdbcTemplate.execute(any<String>()) } returns Unit

        dispatcher.init()

        // 验证插入了缺失的记录
        verify { jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java) }
        verify(exactly = 524) { jdbcTemplate.execute(any<String>()) } // 1024 - 500 = 524
    }

    @Test
    @DisplayName("测试获取WorkerId成功")
    fun testAcquireSuccess() {
        val expectedWorkerId = 65L // (2 << 5) + 1 = 65
        val mockNow = LocalDateTime.now()

        // 模拟查询返回可用的worker
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(expectedWorkerId)

        // 模拟更新成功
        every {
            jdbcTemplate.update(
                any<String>(), localHostIdentify, any<LocalDateTime>(), any<LocalDateTime>(),
                localHostIdentify, any<LocalDateTime>(), expectedWorkerId
            )
        } returns 1

        val result = dispatcher.acquire(null, null)

        assertEquals(expectedWorkerId, result)
        verify { jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>()) }
        verify { jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("测试获取指定WorkerId")
    fun testAcquireSpecificWorkerId() {
        val specificWorkerId = 5L
        val specificDatacenterId = 2L
        val expectedResult = 69L // (2 << 5) + 5 = 69

        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(expectedResult)

        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        val result = dispatcher.acquire(specificWorkerId, specificDatacenterId)

        assertEquals(expectedResult, result)

        // 验证查询条件包含了指定的workerId和datacenterId
        verify {
            jdbcTemplate.queryForList(
                match<String> { sql ->
                    sql.contains("and $fieldWorkerId=$specificWorkerId") &&
                            sql.contains("and $fieldDatacenterId=$specificDatacenterId")
                },
                Long::class.java, localHostIdentify, any<LocalDateTime>()
            )
        }
    }

    @Test
    @DisplayName("测试获取WorkerId失败 - 无可用记录")
    fun testAcquireFailureNoAvailableRecords() {
        // 模拟查询返回空列表
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns emptyList()

        val exception = assertThrows<RuntimeException> {
            dispatcher.acquire(null, null)
        }

        assertEquals("WorkerId分发失败", exception.message)
    }

    @Test
    @DisplayName("测试获取WorkerId失败 - 更新失败")
    fun testAcquireFailureUpdateFailed() {
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(65L)

        // 模拟更新失败
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 0

        val exception = assertThrows<RuntimeException> {
            dispatcher.acquire(null, null)
        }

        assertEquals("WorkerId分发失败", exception.message)
    }

    @Test
    @DisplayName("测试缓存WorkerId - 重复获取返回缓存值")
    fun testCachedWorkerId() {
        val expectedWorkerId = 65L

        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(expectedWorkerId)

        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        // 第一次获取
        val result1 = dispatcher.acquire(null, null)
        assertEquals(expectedWorkerId, result1)

        // 第二次获取应该返回缓存值，不再执行数据库操作
        val result2 = dispatcher.acquire(null, null)
        assertEquals(expectedWorkerId, result2)

        // 验证数据库操作只执行了一次
        verify(exactly = 1) { jdbcTemplate.queryForList(any<String>(), Long::class.java, any(), any()) }
        verify(exactly = 1) { jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("测试释放WorkerId成功")
    fun testReleaseSuccess() {
        // 先获取一个WorkerId
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(65L)
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        dispatcher.acquire(null, null)

        // 模拟释放成功
        every {
            jdbcTemplate.update(any<String>(), any<LocalDateTime>(), localHostIdentify, 65L)
        } returns 1

        dispatcher.release()

        verify { jdbcTemplate.update(any<String>(), any<LocalDateTime>(), localHostIdentify, 65L) }
    }

    @Test
    @DisplayName("测试释放WorkerId失败")
    fun testReleaseFailure() {
        // 先获取一个WorkerId
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(65L)
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        dispatcher.acquire(null, null)

        // 模拟释放失败
        every {
            jdbcTemplate.update(any<String>(), any<LocalDateTime>(), localHostIdentify, 65L)
        } returns 0

        val exception = assertThrows<RuntimeException> {
            dispatcher.release()
        }

        assertEquals("WorkerId释放失败", exception.message)
    }

    @Test
    @DisplayName("测试释放WorkerId - 未获取时不执行操作")
    fun testReleaseWithoutAcquire() {
        // 未获取WorkerId时释放应该不执行任何操作
        dispatcher.release()

        verify(exactly = 0) { jdbcTemplate.update(any<String>(), any(), any(), any()) }
    }

    @Test
    @DisplayName("测试心跳上报成功")
    fun testPongSuccess() {
        // 先获取一个WorkerId
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(65L)
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        dispatcher.acquire(null, null)

        // 模拟心跳成功
        every {
            jdbcTemplate.update(any<String>(), any<LocalDateTime>(), localHostIdentify, 65L)
        } returns 1

        val result = dispatcher.pong()

        assertTrue(result)
        verify { jdbcTemplate.update(any<String>(), any<LocalDateTime>(), localHostIdentify, 65L) }
    }

    @Test
    @DisplayName("测试心跳上报失败")
    fun testPongFailure() {
        // 先获取一个WorkerId
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(65L)
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        dispatcher.acquire(null, null)

        // 模拟心跳失败
        every {
            jdbcTemplate.update(any<String>(), any<LocalDateTime>(), localHostIdentify, 65L)
        } returns 0

        val result = dispatcher.pong()

        assertFalse(result)
    }

    @Test
    @DisplayName("测试心跳上报 - 未获取WorkerId")
    fun testPongWithoutAcquire() {
        val result = dispatcher.pong()

        assertFalse(result)
        verify(exactly = 0) { jdbcTemplate.update(any<String>(), any(), any(), any()) }
    }

    @Test
    @DisplayName("测试提醒方法")
    fun testRemind() {
        // 当前实现为空，确保方法可以正常调用
        dispatcher.remind()
        // 空实现，无需验证
    }

    @Test
    @DisplayName("测试使用空主机标识时使用本地IP")
    fun testEmptyHostIdentify() {
        val dispatcherWithEmptyHost = DefaultSnowflakeWorkerIdDispatcher(
            jdbcTemplate = jdbcTemplate,
            table = table,
            fieldDatacenterId = fieldDatacenterId,
            fieldWorkerId = fieldWorkerId,
            fieldDispatchTo = fieldDispatchTo,
            fieldDispatchAt = fieldDispatchAt,
            fieldExpireAt = fieldExpireAt,
            expireMinutes = expireMinutes,
            localHostIdentify = "", // 空字符串
            showSql = false
        )

        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, any<String>(), any<LocalDateTime>())
        } returns listOf(65L)
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        val result = dispatcherWithEmptyHost.acquire(null, null)

        assertEquals(65L, result)
        // 验证使用了非空的主机标识（应该是本地IP）
        verify {
            jdbcTemplate.queryForList(
                any<String>(), Long::class.java,
                match<String> { it.isNotBlank() }, any<LocalDateTime>()
            )
        }
    }

    @Test
    @DisplayName("测试数据库异常处理")
    fun testDatabaseException() {
        // 模拟数据库异常
        val exception = object : DataAccessException("数据库连接失败") {}
        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } throws exception

        assertThrows<DataAccessException> {
            dispatcher.acquire(null, null)
        }
    }

    @Test
    @DisplayName("测试日志输出开关")
    fun testSqlLogging() {
        // 使用showSql=false的实例
        val quietDispatcher = DefaultSnowflakeWorkerIdDispatcher(
            jdbcTemplate = jdbcTemplate,
            table = table,
            fieldDatacenterId = fieldDatacenterId,
            fieldWorkerId = fieldWorkerId,
            fieldDispatchTo = fieldDispatchTo,
            fieldDispatchAt = fieldDispatchAt,
            fieldExpireAt = fieldExpireAt,
            expireMinutes = expireMinutes,
            localHostIdentify = localHostIdentify,
            showSql = false // 关闭SQL日志
        )

        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(65L)
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        val result = quietDispatcher.acquire(null, null)

        assertEquals(65L, result)
        // 验证正常执行，日志相关功能通过代码覆盖率工具验证
    }

    @Test
    @DisplayName("测试边界值处理")
    fun testBoundaryValues() {
        // 测试最大WorkerId和DatacenterId
        val maxWorkerId = 31L
        val maxDatacenterId = 31L
        val expectedResult = (maxDatacenterId shl 5) + maxWorkerId // 31*32 + 31 = 1023

        every {
            jdbcTemplate.queryForList(any<String>(), Long::class.java, localHostIdentify, any<LocalDateTime>())
        } returns listOf(expectedResult)
        every {
            jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any())
        } returns 1

        val result = dispatcher.acquire(maxWorkerId, maxDatacenterId)

        assertEquals(expectedResult, result)
    }
}
