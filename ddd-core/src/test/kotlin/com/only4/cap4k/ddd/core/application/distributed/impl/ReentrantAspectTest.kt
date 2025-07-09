package com.only4.cap4k.ddd.core.application.distributed.impl

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.distributed.annotation.Reentrant
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.Method
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * ReentrantAspect单元测试
 */
class ReentrantAspectTest {

    @MockK
    private lateinit var distributedLocker: Locker

    @MockK
    private lateinit var joinPoint: ProceedingJoinPoint

    @MockK
    private lateinit var signature: MethodSignature

    @MockK
    private lateinit var method: Method

    @MockK
    private lateinit var reentrant: Reentrant

    private lateinit var aspect: ReentrantAspect

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        aspect = ReentrantAspect(distributedLocker)

        // 设置通用的模拟行为
        every { joinPoint.signature } returns signature
        every { signature.method } returns method
        every { method.declaringClass } returns ReentrantAspectTest::class.java
        every { method.name } returns "testMethod"
    }

    @Test
    fun `当reentrant的value为true时，直接执行方法`() {
        // 准备
        val expectedResult = "result"
        every { reentrant.value } returns true
        every { joinPoint.proceed() } returns expectedResult

        // 执行
        val result = aspect.around(joinPoint, reentrant)

        // 验证
        assertEquals(expectedResult, result)
        verify { joinPoint.proceed() }
        verify(exactly = 0) {
            distributedLocker.acquire(any(), any(), any())
            distributedLocker.release(any(), any())
        }
    }

    @Test
    fun `当使用分布式锁且获取锁成功时，执行方法并释放锁`() {
        // 准备
        val expectedResult = "result"
        val lockKey = "com.only4.cap4k.ddd.core.application.distributed.impl.ReentrantAspectTest:testMethod"

        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns ""
        every { reentrant.expire } returns ""
        every { joinPoint.proceed() } returns expectedResult
        every { distributedLocker.acquire(any(), any(), any()) } returns true
        every { distributedLocker.release(any(), any()) } returns true

        // 执行
        val result = aspect.around(joinPoint, reentrant)

        // 验证
        assertEquals(expectedResult, result)
        verify {
            distributedLocker.acquire(lockKey, any(), any())
            joinPoint.proceed()
            distributedLocker.release(lockKey, any())
        }
    }

    @Test
    fun `当使用本地锁且获取锁成功时，执行方法并释放锁`() {
        // 准备
        val expectedResult = "result"
        val lockKey = "com.only4.cap4k.ddd.core.application.distributed.impl.ReentrantAspectTest:testMethod"

        every { reentrant.value } returns false
        every { reentrant.distributed } returns false
        every { reentrant.key } returns ""
        every { reentrant.expire } returns ""
        every { joinPoint.proceed() } returns expectedResult

        // 执行
        val result = aspect.around(joinPoint, reentrant)

        // 验证
        assertEquals(expectedResult, result)
        verify(exactly = 0) {
            distributedLocker.acquire(any(), any(), any())
            distributedLocker.release(any(), any())
        }
    }

    @Test
    fun `当获取锁失败时，返回null`() {
        // 准备
        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns ""
        every { reentrant.expire } returns ""
        every { distributedLocker.acquire(any(), any(), any()) } returns false

        // 执行
        val result = aspect.around(joinPoint, reentrant)

        // 验证
        assertNull(result)
        verify(exactly = 0) { joinPoint.proceed() }
    }

    @Test
    fun `当使用自定义key时，应该使用该key作为锁键`() {
        // 准备
        val customKey = "customLockKey"

        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns customKey
        every { reentrant.expire } returns ""
        every { distributedLocker.acquire(customKey, any(), any()) } returns true
        every { distributedLocker.release(customKey, any()) } returns true
        every { joinPoint.proceed() } returns "result"

        // 执行
        aspect.around(joinPoint, reentrant)

        // 验证
        verify {
            distributedLocker.acquire(customKey, any(), any())
            distributedLocker.release(customKey, any())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["30s", "5m", "2h", "1d", "500ms"])
    fun `当指定过期时间时，应该正确解析带单位的时间`(expireStr: String) {
        // 准备
        val expectedDuration = when {
            expireStr == "30s" -> Duration.ofSeconds(30)
            expireStr == "5m" -> Duration.ofMinutes(5)
            expireStr == "2h" -> Duration.ofHours(2)
            expireStr == "1d" -> Duration.ofDays(1)
            expireStr == "500ms" -> Duration.ofMillis(500)
            else -> throw IllegalArgumentException("Unexpected expire string: $expireStr")
        }

        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns ""
        every { reentrant.expire } returns expireStr
        every { distributedLocker.acquire(any(), any(), expectedDuration) } returns true
        every { distributedLocker.release(any(), any()) } returns true
        every { joinPoint.proceed() } returns "result"

        // 执行
        aspect.around(joinPoint, reentrant)

        // 验证
        verify { distributedLocker.acquire(any(), any(), expectedDuration) }
    }

    @Test
    fun `当指定纯数字过期时间时，应该解析为秒数`() {
        // 准备
        val expireStr = "45"
        val expectedDuration = Duration.ofSeconds(45)

        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns ""
        every { reentrant.expire } returns expireStr
        every { distributedLocker.acquire(any(), any(), expectedDuration) } returns true
        every { distributedLocker.release(any(), any()) } returns true
        every { joinPoint.proceed() } returns "result"

        // 执行
        aspect.around(joinPoint, reentrant)

        // 验证
        verify { distributedLocker.acquire(any(), any(), expectedDuration) }
    }

    @Test
    fun `当指定ISO-8601格式过期时间时，应该正确解析`() {
        // 准备
        val expireStr = "PT1H30M"
        val expectedDuration = Duration.parse(expireStr) // 1小时30分钟

        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns ""
        every { reentrant.expire } returns expireStr
        every { distributedLocker.acquire(any(), any(), expectedDuration) } returns true
        every { distributedLocker.release(any(), any()) } returns true
        every { joinPoint.proceed() } returns "result"

        // 执行
        aspect.around(joinPoint, reentrant)

        // 验证
        verify { distributedLocker.acquire(any(), any(), expectedDuration) }
    }

    @Test
    fun `当未指定过期时间时，应该使用默认过期时间`() {
        // 准备
        val expireStr = ""
        val expectedDuration = Duration.ofHours(6) // 默认过期时间

        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns ""
        every { reentrant.expire } returns expireStr
        every { distributedLocker.acquire(any(), any(), expectedDuration) } returns true
        every { distributedLocker.release(any(), any()) } returns true
        every { joinPoint.proceed() } returns "result"

        // 执行
        aspect.around(joinPoint, reentrant)

        // 验证
        verify { distributedLocker.acquire(any(), any(), expectedDuration) }
    }

    @Test
    fun `当方法执行抛出异常时，应该释放锁并重新抛出异常`() {
        // 准备
        val exception = RuntimeException("Test exception")

        every { reentrant.value } returns false
        every { reentrant.distributed } returns true
        every { reentrant.key } returns ""
        every { reentrant.expire } returns ""
        every { distributedLocker.acquire(any(), any(), any()) } returns true
        every { distributedLocker.release(any(), any()) } returns true
        every { joinPoint.proceed() } throws exception

        // 执行和验证
        val thrownException = kotlin.runCatching { aspect.around(joinPoint, reentrant) }
            .exceptionOrNull() as? RuntimeException

        assertEquals(exception, thrownException?.cause)
        verify { distributedLocker.release(any(), any()) } // 确保锁被释放
    }
}
