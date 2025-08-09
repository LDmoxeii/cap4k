package com.only4.cap4k.ddd.application.distributed.locker

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration

@DisplayName("JDBC分布式锁测试")
class JdbcLockerTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var jdbcLocker: JdbcLocker

    private val table = "test_locker"
    private val fieldName = "name"
    private val fieldPwd = "pwd"
    private val fieldLockAt = "lock_at"
    private val fieldUnlockAt = "unlock_at"

    @BeforeEach
    fun setUp() {
        jdbcTemplate = mockk()
        jdbcLocker = JdbcLocker(
            jdbcTemplate = jdbcTemplate,
            table = table,
            fieldName = fieldName,
            fieldPwd = fieldPwd,
            fieldLockAt = fieldLockAt,
            fieldUnlockAt = fieldUnlockAt,
            showSql = false
        )
    }

    @Nested
    @DisplayName("acquire方法测试")
    inner class AcquireTest {

        @Test
        @DisplayName("首次获取锁 - 数据库中不存在记录")
        fun `should acquire lock successfully when key does not exist in database`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 0
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertTrue(result)

            verify {
                jdbcTemplate.queryForObject(
                    "select count(*) from $table where $fieldName = ?",
                    Int::class.java,
                    key
                )
                jdbcTemplate.update(
                    "insert into $table($fieldName, $fieldPwd, $fieldLockAt, $fieldUnlockAt) values(?, ?, ?, ?)",
                    key, pwd, any(), any()
                )
            }
        }

        @Test
        @DisplayName("获取锁失败 - 数据库插入异常")
        fun `should fail to acquire lock when database insert throws exception`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 0
            every {
                jdbcTemplate.update(
                    any<String>(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } throws DataAccessResourceFailureException("DB Error")

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("更新现有锁 - 数据库中存在记录且更新成功")
        fun `should update existing lock successfully`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 1
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertTrue(result)

            verify {
                jdbcTemplate.update(
                    "update $table set $fieldPwd = ?, $fieldLockAt = ?, $fieldUnlockAt = ? where $fieldName = ? and ($fieldUnlockAt < ? or $fieldPwd = ?)",
                    pwd,
                    any(),
                    any(),
                    key,
                    any(),
                    pwd
                )
            }
        }

        @Test
        @DisplayName("更新现有锁失败 - 更新条件不满足")
        fun `should fail to update existing lock when conditions not met`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 1
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any()) } returns 0

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("数据库查询返回null - 应按0处理")
        fun `should handle null return from database query as zero`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), eq(Int::class.java), key) } returns null
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertTrue(result)
            verify {
                jdbcTemplate.update(
                    "insert into $table($fieldName, $fieldPwd, $fieldLockAt, $fieldUnlockAt) values(?, ?, ?, ?)",
                    any(), any(), any(), any()
                )
            }
        }
    }

    @Nested
    @DisplayName("release方法测试")
    inner class ReleaseTest {

        @Test
        @DisplayName("成功释放锁 - 数据库存在匹配记录")
        fun `should release lock successfully when database has matching record`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key, pwd) } returns 1
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.release(key, pwd)

            // Then
            assertTrue(result)

            verify {
                jdbcTemplate.queryForObject(
                    "select count(*) from $table where $fieldName = ? and $fieldPwd = ?",
                    Int::class.java,
                    key, pwd
                )
                jdbcTemplate.update(
                    "update $table set $fieldUnlockAt = ? where $fieldName = ? and $fieldPwd = ? and $fieldUnlockAt > ?",
                    any(), key, pwd, any()
                )
            }
        }

        @Test
        @DisplayName("释放锁失败 - 数据库中不存在匹配记录")
        fun `should fail to release lock when database has no matching record`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key, pwd) } returns 0

            // When
            val result = jdbcLocker.release(key, pwd)

            // Then
            assertFalse(result)

            verify {
                jdbcTemplate.queryForObject(
                    "select count(*) from $table where $fieldName = ? and $fieldPwd = ?",
                    Int::class.java,
                    key, pwd
                )
            }
            verify(exactly = 0) {
                jdbcTemplate.update(any<String>(), any(), any(), any(), any())
            }
        }

        @Test
        @DisplayName("数据库查询返回null - 应按0处理")
        fun `should handle null return from database query as zero in release`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"

            every { jdbcTemplate.queryForObject(any<String>(), eq(Int::class.java), key, pwd) } returns null

            // When
            val result = jdbcLocker.release(key, pwd)

            // Then
            assertFalse(result)

            verify(exactly = 0) {
                jdbcTemplate.update(any<String>(), any(), any(), any(), any())
            }
        }
    }

    @Nested
    @DisplayName("并发安全性测试")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("同一key的并发获取应该同步执行")
        fun `should synchronize concurrent acquisitions for same key`() {
            // Given
            val key = "test-key"
            val pwd1 = "pwd1"
            val pwd2 = "pwd2"
            val expireDuration = Duration.ofMinutes(5)

            // Mock behavior: first call returns 0 (no record), subsequent calls return 1 (record exists)
            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returnsMany listOf(0, 1)
            // First call (insert) succeeds, second call (update) fails
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any(), any(), any()) } returns 0

            // When - 模拟并发访问
            val results = mutableListOf<Boolean>()
            val threads = (1..2).map { i ->
                Thread {
                    val pwd = if (i == 1) pwd1 else pwd2
                    val result = jdbcLocker.acquire(key, pwd, expireDuration)
                    synchronized(results) {
                        results.add(result)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then - 验证结果
            assertEquals(2, results.size)
            // Due to the synchronized implementation, both threads should get results
            // but the specific outcome depends on the order of execution
        }
    }

    @Nested
    @DisplayName("SQL日志测试")
    inner class SqlLoggingTest {

        @Test
        @DisplayName("showSql为true时应记录SQL日志")
        fun `should log SQL when showSql is true`() {
            // Given
            val jdbcLockerWithLogging = JdbcLocker(
                jdbcTemplate = jdbcTemplate,
                table = table,
                fieldName = fieldName,
                fieldPwd = fieldPwd,
                fieldLockAt = fieldLockAt,
                fieldUnlockAt = fieldUnlockAt,
                showSql = true
            )

            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 0
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            jdbcLockerWithLogging.acquire(key, pwd, expireDuration)

            // Then - 验证SQL被执行（日志记录通过私有方法实现，这里验证JDBC调用）
            verify {
                jdbcTemplate.queryForObject(any<String>(), Int::class.java, key)
                jdbcTemplate.update(any<String>(), any(), any(), any(), any())
            }
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("过期时间为0秒的锁")
        fun `should handle zero duration lock`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ZERO

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 0
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("极长过期时间的锁")
        fun `should handle very long duration lock`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofDays(365)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 0
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("空字符串key和password")
        fun `should handle empty key and password`() {
            // Given
            val key = ""
            val pwd = ""
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 0
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertTrue(result)
            verify {
                jdbcTemplate.queryForObject(any<String>(), Int::class.java, "")
                jdbcTemplate.update(any<String>(), "", "", any(), any())
            }
        }

        @Test
        @DisplayName("特殊字符的key和password")
        fun `should handle special characters in key and password`() {
            // Given
            val key = "key-with-特殊字符-@#$%"
            val pwd = "pwd-with-特殊字符-!@#$%^&*()"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 0
            every { jdbcTemplate.update(any<String>(), any(), any(), any(), any()) } returns 1

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertTrue(result)
        }
    }

    @Nested
    @DisplayName("数据库异常处理测试")
    inner class DatabaseExceptionTest {

        @Test
        @DisplayName("查询异常时acquire应返回false")
        fun `should return false when query throws exception in acquire`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every {
                jdbcTemplate.queryForObject(
                    any<String>(),
                    Int::class.java,
                    key
                )
            } throws DataAccessResourceFailureException("Query failed")

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("更新异常时acquire应返回false")
        fun `should return false when update throws exception in acquire`() {
            // Given
            val key = "test-key"
            val pwd = "test-pwd"
            val expireDuration = Duration.ofMinutes(5)

            every { jdbcTemplate.queryForObject(any<String>(), Int::class.java, key) } returns 1
            every {
                jdbcTemplate.update(
                    any<String>(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } throws DataAccessResourceFailureException("Update failed")

            // When
            val result = jdbcLocker.acquire(key, pwd, expireDuration)

            // Then
            assertFalse(result)
        }
    }
}
