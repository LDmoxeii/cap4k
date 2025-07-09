package com.only4.cap4k.ddd.core.application.distributed.impl

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.distributed.annotation.Reentrant
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 可重入锁切面
 *
 * @author binking338
 * @date 2025/5/14
 */
@Aspect
class ReentrantAspect(
    private val distributedLocker: Locker
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val localLocker = MemoryLocker()

    companion object {
        private val DEFAULT_EXPIRE = Duration.ofHours(6)
    }

    @Around("@annotation(reentrant)")
    fun around(
        joinPoint: ProceedingJoinPoint,
        reentrant: Reentrant
    ): Any? = when {
        reentrant.value -> joinPoint.proceedSafely()
        else -> handleWithLock(joinPoint, reentrant)
    }

    private fun handleWithLock(joinPoint: ProceedingJoinPoint, reentrant: Reentrant): Any? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method

        val locker = if (reentrant.distributed) distributedLocker else localLocker

        // 生成唯一锁键
        val lockKey = generateLockKey(method, reentrant.key)
        val lockPwd = UUID.randomUUID().toString()

        val expire = parseDuration(reentrant.expire)

        return if (locker.acquire(lockKey, lockPwd, expire)) {
            logger.debug("获取锁成功: {}", lockKey)
            try {
                joinPoint.proceedSafely()
            } finally {
                locker.release(lockKey, lockPwd)
                logger.debug("释放锁成功: {}", lockKey)
            }
        } else {
            logger.debug("获取锁失败: {}", lockKey)
            null
        }
    }

    private fun ProceedingJoinPoint.proceedSafely(): Any? = try {
        proceed()
    } catch (e: Throwable) {
        throw RuntimeException(e)
    }

    private fun generateLockKey(method: Method, key: String): String =
        key.takeIf { it.isNotEmpty() } ?: "${method.declaringClass.name}:${method.name}"

    private fun parseDuration(expireStr: String): Duration {
        if (expireStr.isEmpty()) return DEFAULT_EXPIRE

        val lowerExpireStr = expireStr.lowercase(Locale.getDefault())
        return when {
            lowerExpireStr.matches(Regex("\\d+")) ->
                Duration.ofSeconds(lowerExpireStr.toLong())

            lowerExpireStr.matches(Regex("\\d+([smhd]|ms)")) -> {
                val numericPart = lowerExpireStr.replace(Regex("\\D"), "")
                val unit = lowerExpireStr.replace(Regex("\\d"), "")
                when (unit) {
                    "ms" -> Duration.ofMillis(numericPart.toLong())
                    "s" -> Duration.ofSeconds(numericPart.toLong())
                    "m" -> Duration.ofMinutes(numericPart.toLong())
                    "h" -> Duration.ofHours(numericPart.toLong())
                    "d" -> Duration.ofDays(numericPart.toLong())
                    else -> throw IllegalArgumentException("Invalid expire string: $lowerExpireStr")
                }
            }

            else -> Duration.parse(lowerExpireStr)
        }
    }

    private inner class MemoryLocker : Locker {
        private val expireMap = ConcurrentHashMap<String, Pair<String, Long>>()

        override fun acquire(key: String, pwd: String, expireDuration: Duration): Boolean {
            val now = System.currentTimeMillis()
            val control = expireMap[key]

            synchronized(this) {
                if (control != null && control.second > now) {
                    return pwd == control.first
                }
                expireMap[key] = Pair(pwd, now + expireDuration.toMillis())
                return true
            }
        }

        override fun release(key: String, pwd: String): Boolean {
            synchronized(this) {
                val control = expireMap[key] ?: return true

                if (pwd == control.first) {
                    expireMap.remove(key)
                    return true
                }
                return false
            }
        }
    }
}
