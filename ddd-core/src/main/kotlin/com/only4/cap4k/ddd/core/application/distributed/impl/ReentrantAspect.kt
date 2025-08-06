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
 * @author LD_moxeii
 * @date 2025/07/26
 */
@Aspect
class ReentrantAspect(
    private val distributedLocker: Locker
) {
    private val log = LoggerFactory.getLogger(ReentrantAspect::class.java)
    private val localLocker: Locker = MemoryLocker()

    companion object {
        val DEFAULT_EXPIRE: Duration = Duration.ofHours(6)
    }

    @Around("@annotation(reentrant)")
    fun around(joinPoint: ProceedingJoinPoint, reentrant: Reentrant): Any? {
        if (reentrant.value) {
            return try {
                joinPoint.proceed()
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }

        val signature = joinPoint.signature as MethodSignature
        val method = signature.method

        val locker = if (reentrant.distributed) distributedLocker else localLocker

        // 生成唯一锁键
        val lockKey = generateLockKey(method, reentrant.key)
        val lockPwd = UUID.randomUUID().toString()

        val expire = parseDuration(reentrant.expire)

        return if (locker.acquire(lockKey, lockPwd, expire)) {
            log.debug("获取锁成功:{}", lockKey)
            try {
                joinPoint.proceed()
            } catch (e: Throwable) {
                throw RuntimeException(e)
            } finally {
                locker.release(lockKey, lockPwd)
                log.debug("释放锁成功:{}", lockKey)
            }
        } else {
            log.debug("获取锁失败:{}", lockKey)
            null
        }
    }

    private fun generateLockKey(method: Method, key: String): String {
        return key.ifBlank {
            "${method.declaringClass.name}:${method.name}"
        }
    }

    private fun parseDuration(expireStr: String): Duration {
        if (expireStr.isBlank()) {
            return DEFAULT_EXPIRE
        }

        val expire = expireStr.lowercase()

        return when {
            expire.matches(Regex("\\d+")) -> {
                Duration.ofSeconds(expire.toLong())
            }

            expire.matches(Regex("\\d+([smhd]|ms)")) -> {
                val numericPart = expire.replace(Regex("\\D"), "").toLong()
                val unit = expire.replace(Regex("\\d"), "")
                when (unit) {
                    "ms" -> Duration.ofMillis(numericPart)
                    "s" -> Duration.ofSeconds(numericPart)
                    "m" -> Duration.ofMinutes(numericPart)
                    "h" -> Duration.ofHours(numericPart)
                    "d" -> Duration.ofDays(numericPart)
                    else -> throw IllegalArgumentException("Invalid expire string: $expireStr")
                }
            }

            else -> Duration.parse(expireStr)
        }
    }

    class MemoryLocker : Locker {
        private val defaultControl = arrayOf("", 0L)
        private val expireMap = ConcurrentHashMap<String, Array<Any>>()

        @Synchronized
        override fun acquire(key: String, pwd: String, expireDuration: Duration): Boolean {
            val now = System.currentTimeMillis()
            val control = expireMap.getOrDefault(key, defaultControl)
            val timestamp = control[1] as Long

            return if (timestamp > now) {
                pwd == control[0]
            } else {
                expireMap[key] = arrayOf(pwd, now + expireDuration.toMillis())
                true
            }
        }

        @Synchronized
        override fun release(key: String, pwd: String): Boolean {
            if (!expireMap.containsKey(key)) {
                return true
            }

            val control = expireMap[key] ?: return true
            return if (pwd == control[0]) {
                expireMap.remove(key)
                true
            } else {
                false
            }
        }
    }
}
