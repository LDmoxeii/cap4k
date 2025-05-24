package com.only4.core.application.distributed

import com.only4.core.application.distributed.annotation.Reentrant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
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
    private val logger = KotlinLogging.logger {}
    private val localLocker = MemoryLocker()

    @Around("@annotation(reentrant)")
    fun around(
        joinPoint: ProceedingJoinPoint,
        reentrant: Reentrant
    ): Any {
        if (reentrant.value) return joinPoint.proceed()

        val signature = joinPoint.signature as MethodSignature
        val method = signature.method

        val locker = if (reentrant.distributed)
            distributedLocker
        else localLocker

        // 生成唯一锁键
        val lockKey: String = generateLockKey(method, reentrant.key)
        val lockPwd = UUID.randomUUID().toString()

        val expire: Duration = parseDuration(reentrant.expire)
        return if (locker.acquire(lockKey, lockPwd, expire)) {
            logger.debug { "获取锁成功:$lockKey" }
            joinPoint.proceed()
                .apply { locker.release(lockKey, lockPwd).apply { logger.debug { "释放锁成功:$lockKey" } } }
        } else logger.debug { "获取锁失败:$lockKey" }
    }

    private fun generateLockKey(method: Method, key: String): String {
        return key.ifEmpty { "${method.declaringClass.name}${method.name}" }
    }

    private fun parseDuration(expireStr: String): Duration {
        if (expireStr.isEmpty()) return DEFAULT_EXPIRE

        val lowerExpireStr = expireStr.lowercase(Locale.getDefault())
        return when {
            lowerExpireStr.matches("\\d+".toRegex()) -> Duration.ofSeconds(lowerExpireStr.toLong())
            lowerExpireStr.matches("\\d+([smhd]|ms)".toRegex()) -> {
                val numericPart = lowerExpireStr.replace("\\D".toRegex(), "")
                val unit = lowerExpireStr.replace("\\d".toRegex(), "")
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

    inner class MemoryLocker : Locker {

        private val DEFAULT_CONTROL = Pair("", 0L)
        private val expireMap = ConcurrentHashMap<String, Pair<String, Long>>()
        override fun acquire(key: String, pwd: String, expireDuration: Duration): Boolean {
            val now = System.currentTimeMillis()
            val control = expireMap.getOrDefault(key, DEFAULT_CONTROL)
            val timestamp = control.second
            synchronized(this) {
                if (timestamp > now) {
                    return control.first == pwd
                }
            }
            expireMap.put(key, pwd to (now + expireDuration.toMillis()))
                .apply { return true }
        }

        override fun release(key: String, pwd: String): Boolean {
            synchronized(this) {
                if (!expireMap.containsKey(key)) return true
                val control = expireMap[key]!!
                if (control.first != pwd) return false
                return expireMap.remove(key) != null
            }
        }
    }

    companion object {
        val DEFAULT_EXPIRE = Duration.ofHours(6)
    }
}
