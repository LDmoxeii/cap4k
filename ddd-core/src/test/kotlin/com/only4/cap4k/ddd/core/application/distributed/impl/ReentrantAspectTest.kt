package com.only4.cap4k.ddd.core.application.distributed.impl

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.distributed.annotation.Reentrant
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Method
import java.time.Duration
import java.time.format.DateTimeParseException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ReentrantAspect 详尽测试用例
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
@DisplayName("ReentrantAspect Tests")
class ReentrantAspectTest {

    private lateinit var distributedLocker: Locker
    private lateinit var aspect: ReentrantAspect
    private lateinit var joinPoint: ProceedingJoinPoint
    private lateinit var signature: MethodSignature
    private lateinit var method: Method

    @BeforeEach
    fun setUp() {
        distributedLocker = mockk()
        aspect = ReentrantAspect(distributedLocker)
        joinPoint = mockk()
        signature = mockk()
        method = mockk()

        every { joinPoint.signature } returns signature
        every { signature.method } returns method
        every { method.declaringClass } returns TestClass::class.java
        every { method.name } returns "testMethod"
    }

    // 测试用类
    class TestClass {
        fun testMethod() {}
    }

    @Nested
    @DisplayName("Around Method with Reentrant.value=true Tests")
    inner class ReentrantValueTrueTests {

        @Test
        @DisplayName("当reentrant.value为true时，应该直接执行方法不加锁")
        fun `when reentrant value is true, should execute method directly without locking`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns true
            every { joinPoint.proceed() } returns "test result"

            val result = aspect.around(joinPoint, reentrant)

            assertEquals("test result", result)
            verify { joinPoint.proceed() }
            verify(exactly = 0) { distributedLocker.acquire(any(), any(), any()) }
            verify(exactly = 0) { distributedLocker.release(any(), any()) }
        }

        @Test
        @DisplayName("当reentrant.value为true且方法抛出异常时，应该包装为RuntimeException")
        fun `when reentrant value is true and method throws exception, should wrap as RuntimeException`() {
            val reentrant = mockk<Reentrant>()
            val originalException = IllegalArgumentException("Test exception")

            every { reentrant.value } returns true
            every { joinPoint.proceed() } throws originalException

            val exception = assertThrows<RuntimeException> {
                aspect.around(joinPoint, reentrant)
            }

            assertEquals(originalException, exception.cause)
            verify { joinPoint.proceed() }
        }
    }

    @Nested
    @DisplayName("Distributed Locking Tests")
    inner class DistributedLockingTests {

        @Test
        @DisplayName("当使用分布式锁且获取成功时，应该执行方法并释放锁")
        fun `when using distributed lock and acquire succeeds, should execute method and release lock`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns true
            every { distributedLocker.release(any(), any()) } returns true
            every { joinPoint.proceed() } returns "test result"

            val result = aspect.around(joinPoint, reentrant)

            assertEquals("test result", result)
            verify { distributedLocker.acquire("test-key", any(), Duration.ofSeconds(30)) }
            verify { distributedLocker.release("test-key", any()) }
            verify { joinPoint.proceed() }
        }

        @Test
        @DisplayName("当使用分布式锁且获取失败时，应该返回null不执行方法")
        fun `when using distributed lock and acquire fails, should return null without executing method`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            val result = aspect.around(joinPoint, reentrant)

            assertNull(result)
            verify { distributedLocker.acquire("test-key", any(), Duration.ofSeconds(30)) }
            verify(exactly = 0) { distributedLocker.release(any(), any()) }
            verify(exactly = 0) { joinPoint.proceed() }
        }

        @Test
        @DisplayName("当分布式锁执行方法抛出异常时，应该释放锁并包装异常")
        fun `when distributed lock method execution throws exception, should release lock and wrap exception`() {
            val reentrant = mockk<Reentrant>()
            val originalException = IllegalStateException("Test exception")

            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns true
            every { distributedLocker.release(any(), any()) } returns true
            every { joinPoint.proceed() } throws originalException

            val exception = assertThrows<RuntimeException> {
                aspect.around(joinPoint, reentrant)
            }

            assertEquals(originalException, exception.cause)
            verify { distributedLocker.acquire("test-key", any(), Duration.ofSeconds(30)) }
            verify { distributedLocker.release("test-key", any()) }
        }
    }

    @Nested
    @DisplayName("Local Locking Tests")
    inner class LocalLockingTests {

        @Test
        @DisplayName("当使用本地锁且获取成功时，应该执行方法")
        fun `when using local lock and acquire succeeds, should execute method`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns false
            every { reentrant.key } returns ""
            every { reentrant.expire } returns ""

            every { joinPoint.proceed() } returns "test result"

            val result = aspect.around(joinPoint, reentrant)

            assertEquals("test result", result)
            verify { joinPoint.proceed() }
        }

        @Test
        @DisplayName("当使用本地锁且方法抛出异常时，应该释放锁并包装异常")
        fun `when using local lock and method throws exception, should release lock and wrap exception`() {
            val reentrant = mockk<Reentrant>()
            val originalException = RuntimeException("Test exception")

            every { reentrant.value } returns false
            every { reentrant.distributed } returns false
            every { reentrant.key } returns ""
            every { reentrant.expire } returns ""

            every { joinPoint.proceed() } throws originalException

            val exception = assertThrows<RuntimeException> {
                aspect.around(joinPoint, reentrant)
            }

            assertEquals(originalException, exception.cause)
        }
    }

    @Nested
    @DisplayName("Lock Key Generation Tests")
    inner class LockKeyGenerationTests {

        @Test
        @DisplayName("当key为空时，应该使用方法签名生成锁键")
        fun `when key is empty, should generate lock key from method signature`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns ""
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify {
                distributedLocker.acquire(
                    "com.only4.cap4k.ddd.core.application.distributed.impl.ReentrantAspectTest\$TestClass:testMethod",
                    any(),
                    any()
                )
            }
        }

        @Test
        @DisplayName("当key为null时，应该使用方法签名生成锁键")
        fun `when key is null, should generate lock key from method signature`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns ""
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify {
                distributedLocker.acquire(
                    "com.only4.cap4k.ddd.core.application.distributed.impl.ReentrantAspectTest\$TestClass:testMethod",
                    any(),
                    any()
                )
            }
        }

        @Test
        @DisplayName("当key有值时，应该直接使用指定的key")
        fun `when key has value, should use specified key directly`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "custom-lock-key"
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("custom-lock-key", any(), any()) }
        }
    }

    @Nested
    @DisplayName("Duration Parsing Tests")
    inner class DurationParsingTests {

        @Test
        @DisplayName("当expire为空时，应该使用默认过期时间")
        fun `when expire is empty, should use default expire duration`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns ""

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), ReentrantAspect.DEFAULT_EXPIRE) }
        }

        @Test
        @DisplayName("应该正确解析数字格式的秒数")
        fun `should correctly parse numeric seconds format`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "120"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofSeconds(120)) }
        }

        @Test
        @DisplayName("应该正确解析毫秒格式")
        fun `should correctly parse milliseconds format`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "500ms"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofMillis(500)) }
        }

        @Test
        @DisplayName("应该正确解析秒格式")
        fun `should correctly parse seconds format`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofSeconds(30)) }
        }

        @Test
        @DisplayName("应该正确解析分钟格式")
        fun `should correctly parse minutes format`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "15m"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofMinutes(15)) }
        }

        @Test
        @DisplayName("应该正确解析小时格式")
        fun `should correctly parse hours format`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "2h"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofHours(2)) }
        }

        @Test
        @DisplayName("应该正确解析天格式")
        fun `should correctly parse days format`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "3d"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofDays(3)) }
        }

        @Test
        @DisplayName("应该正确处理大小写混合的时间格式")
        fun `should correctly handle mixed case time formats`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30S"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofSeconds(30)) }
        }

        @Test
        @DisplayName("应该正确解析Duration.parse格式")
        fun `should correctly parse Duration parse format`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "PT30M"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.parse("PT30M")) }
        }

        @Test
        @DisplayName("当expire格式无效时，应该抛出DateTimeParseException")
        fun `when expire format is invalid, should throw DateTimeParseException`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30x"

            assertThrows<DateTimeParseException> {
                aspect.around(joinPoint, reentrant)
            }
        }
    }

    @Nested
    @DisplayName("MemoryLocker Tests")
    inner class MemoryLockerTests {

        private lateinit var memoryLocker: ReentrantAspect.MemoryLocker

        @BeforeEach
        fun setUp() {
            // 通过反射获取MemoryLocker实例
            val aspect = ReentrantAspect(mockk())
            val field = ReentrantAspect::class.java.getDeclaredField("localLocker")
            field.isAccessible = true
            memoryLocker = field.get(aspect) as ReentrantAspect.MemoryLocker
        }

        @Test
        @DisplayName("首次获取锁应该成功")
        fun `first lock acquisition should succeed`() {
            val result = memoryLocker.acquire("test-key", "test-pwd", Duration.ofMinutes(5))
            assertTrue(result)
        }

        @Test
        @DisplayName("相同密码的重复获取应该成功")
        fun `repeated acquisition with same password should succeed`() {
            memoryLocker.acquire("test-key", "test-pwd", Duration.ofMinutes(5))
            val result = memoryLocker.acquire("test-key", "test-pwd", Duration.ofMinutes(5))
            assertTrue(result)
        }

        @Test
        @DisplayName("不同密码的获取应该失败")
        fun `acquisition with different password should fail`() {
            memoryLocker.acquire("test-key", "test-pwd1", Duration.ofMinutes(5))
            val result = memoryLocker.acquire("test-key", "test-pwd2", Duration.ofMinutes(5))
            assertFalse(result)
        }

        @Test
        @DisplayName("锁过期后应该能重新获取")
        fun `should be able to reacquire lock after expiration`() {
            memoryLocker.acquire("test-key", "test-pwd1", Duration.ofMillis(10))
            Thread.sleep(50) // 等待锁过期
            val result = memoryLocker.acquire("test-key", "test-pwd2", Duration.ofMinutes(5))
            assertTrue(result)
        }

        @Test
        @DisplayName("正确密码应该能释放锁")
        fun `correct password should be able to release lock`() {
            memoryLocker.acquire("test-key", "test-pwd", Duration.ofMinutes(5))
            val result = memoryLocker.release("test-key", "test-pwd")
            assertTrue(result)
        }

        @Test
        @DisplayName("错误密码不应该能释放锁")
        fun `incorrect password should not be able to release lock`() {
            memoryLocker.acquire("test-key", "test-pwd1", Duration.ofMinutes(5))
            val result = memoryLocker.release("test-key", "test-pwd2")
            assertFalse(result)
        }

        @Test
        @DisplayName("释放不存在的锁应该返回true")
        fun `releasing non-existent lock should return true`() {
            val result = memoryLocker.release("non-existent-key", "any-pwd")
            assertTrue(result)
        }

        @Test
        @DisplayName("释放锁后应该能重新获取")
        fun `should be able to reacquire lock after release`() {
            memoryLocker.acquire("test-key", "test-pwd1", Duration.ofMinutes(5))
            memoryLocker.release("test-key", "test-pwd1")
            val result = memoryLocker.acquire("test-key", "test-pwd2", Duration.ofMinutes(5))
            assertTrue(result)
        }

        @Test
        @DisplayName("并发获取锁应该是线程安全的")
        fun `concurrent lock acquisition should be thread safe`() {
            val threadCount = 10
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val executor = Executors.newFixedThreadPool(threadCount)

            repeat(threadCount) { i ->
                executor.submit {
                    try {
                        if (memoryLocker.acquire("test-key", "pwd-$i", Duration.ofMinutes(5))) {
                            successCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals(1, successCount.get()) // 只有一个线程应该成功获取锁

            executor.shutdown()
        }

        @Test
        @DisplayName("并发释放锁应该是线程安全的")
        fun `concurrent lock release should be thread safe`() {
            val password = "test-pwd"
            memoryLocker.acquire("test-key", password, Duration.ofMinutes(5))

            val threadCount = 5
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val executor = Executors.newFixedThreadPool(threadCount)

            repeat(threadCount) {
                executor.submit {
                    try {
                        if (memoryLocker.release("test-key", password)) {
                            successCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            // 第一次释放应该成功，后续释放也应该返回true（因为锁已经不存在）
            assertEquals(threadCount, successCount.get())

            executor.shutdown()
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("分布式锁获取成功但方法执行异常时，应该确保锁被释放")
        fun `when distributed lock acquired but method throws exception, should ensure lock is released`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns true
            every { distributedLocker.release(any(), any()) } returns true
            every { joinPoint.proceed() } throws RuntimeException("Test exception")

            assertThrows<RuntimeException> {
                aspect.around(joinPoint, reentrant)
            }

            verify { distributedLocker.acquire(any(), any(), any()) }
            verify { distributedLocker.release(any(), any()) }
        }

        @Test
        @DisplayName("方法执行正常但释放锁失败时，不应该影响结果")
        fun `when method executes normally but lock release fails, should not affect result`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns true
            every { distributedLocker.release(any(), any()) } returns false
            every { joinPoint.proceed() } returns "test result"

            val result = aspect.around(joinPoint, reentrant)

            assertEquals("test result", result)
            verify { distributedLocker.acquire(any(), any(), any()) }
            verify { distributedLocker.release(any(), any()) }
        }

        @Test
        @DisplayName("Throwable类型的异常应该被正确包装")
        fun `Throwable type exceptions should be correctly wrapped`() {
            val reentrant = mockk<Reentrant>()
            val error = OutOfMemoryError("Test error")

            every { reentrant.value } returns true
            every { joinPoint.proceed() } throws error

            val exception = assertThrows<RuntimeException> {
                aspect.around(joinPoint, reentrant)
            }

            assertEquals(error, exception.cause)
        }

        @Test
        @DisplayName("RuntimeException应该被重新包装而不是直接抛出")
        fun `RuntimeException should be rewrapped instead of thrown directly`() {
            val reentrant = mockk<Reentrant>()
            val originalException = RuntimeException("Original exception")

            every { reentrant.value } returns true
            every { joinPoint.proceed() } throws originalException

            val exception = assertThrows<RuntimeException> {
                aspect.around(joinPoint, reentrant)
            }

            assertEquals(originalException, exception.cause)
            assertNotSame(originalException, exception)
        }
    }

    @Nested
    @DisplayName("Performance and Edge Cases Tests")
    inner class PerformanceAndEdgeCasesTests {

        @Test
        @DisplayName("大量并发锁操作的性能测试")
        fun `performance test with massive concurrent lock operations`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns false
            every { reentrant.key } returns ""
            every { reentrant.expire } returns ""
            every { joinPoint.proceed() } returns "result"

            val threadCount = 100
            val operationsPerThread = 10
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)
            val startTime = System.currentTimeMillis()

            repeat(threadCount) {
                executor.submit {
                    try {
                        repeat(operationsPerThread) {
                            aspect.around(joinPoint, reentrant)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS))
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime

            // 应该在合理时间内完成（小于5秒）
            assertTrue(executionTime < 5000, "Execution time was ${executionTime}ms")

            executor.shutdown()
        }

        @Test
        @DisplayName("空字符串key应该使用默认key生成逻辑")
        fun `empty string key should use default key generation logic`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns ""
            every { reentrant.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify {
                distributedLocker.acquire(
                    "com.only4.cap4k.ddd.core.application.distributed.impl.ReentrantAspectTest\$TestClass:testMethod",
                    any(),
                    any()
                )
            }
        }

        @Test
        @DisplayName("极短的过期时间应该正常工作")
        fun `very short expiration time should work correctly`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "1ms"

            every { distributedLocker.acquire(any(), any(), any()) } returns true
            every { distributedLocker.release(any(), any()) } returns true
            every { joinPoint.proceed() } returns "result"

            val result = aspect.around(joinPoint, reentrant)

            assertEquals("result", result)
            verify { distributedLocker.acquire("test-key", any(), Duration.ofMillis(1)) }
        }

        @Test
        @DisplayName("极长的过期时间应该正常工作")
        fun `very long expiration time should work correctly`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "test-key"
            every { reentrant.expire } returns "365d"

            every { distributedLocker.acquire(any(), any(), any()) } returns false

            aspect.around(joinPoint, reentrant)

            verify { distributedLocker.acquire("test-key", any(), Duration.ofDays(365)) }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {

        @Test
        @DisplayName("完整的锁获取-执行-释放流程测试")
        fun `complete lock acquisition execution release flow test`() {
            val reentrant = mockk<Reentrant>()
            every { reentrant.value } returns false
            every { reentrant.distributed } returns true
            every { reentrant.key } returns "integration-test-key"
            every { reentrant.expire } returns "1m"

            val lockKeySlot = slot<String>()
            val lockPwdSlot = slot<String>()
            val expireSlot = slot<Duration>()

            every {
                distributedLocker.acquire(
                    capture(lockKeySlot),
                    capture(lockPwdSlot),
                    capture(expireSlot)
                )
            } returns true
            every { distributedLocker.release(capture(lockKeySlot), capture(lockPwdSlot)) } returns true
            every { joinPoint.proceed() } returns "integration result"

            val result = aspect.around(joinPoint, reentrant)

            assertEquals("integration result", result)
            assertEquals("integration-test-key", lockKeySlot.captured)
            assertEquals(Duration.ofMinutes(1), expireSlot.captured)
            assertTrue(lockPwdSlot.captured.isNotEmpty())

            verify(exactly = 1) { distributedLocker.acquire(any(), any(), any()) }
            verify(exactly = 1) { distributedLocker.release(any(), any()) }
            verify(exactly = 1) { joinPoint.proceed() }
        }

        @Test
        @DisplayName("本地锁和分布式锁的行为一致性测试")
        fun `local lock and distributed lock behavior consistency test`() {
            val reentrantLocal = mockk<Reentrant>()
            every { reentrantLocal.value } returns false
            every { reentrantLocal.distributed } returns false
            every { reentrantLocal.key } returns "test-key"
            every { reentrantLocal.expire } returns "30s"

            val reentrantDistributed = mockk<Reentrant>()
            every { reentrantDistributed.value } returns false
            every { reentrantDistributed.distributed } returns true
            every { reentrantDistributed.key } returns "test-key"
            every { reentrantDistributed.expire } returns "30s"

            every { distributedLocker.acquire(any(), any(), any()) } returns true
            every { distributedLocker.release(any(), any()) } returns true
            every { joinPoint.proceed() } returns "test result"

            val localResult = aspect.around(joinPoint, reentrantLocal)
            val distributedResult = aspect.around(joinPoint, reentrantDistributed)

            assertEquals(localResult, distributedResult)
            assertEquals("test result", localResult)
            assertEquals("test result", distributedResult)
        }
    }
}
