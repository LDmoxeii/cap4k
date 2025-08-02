package com.only4.cap4k.ddd.application.distributed.locker

import com.only4.cap4k.ddd.core.application.distributed.Locker
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于Jdbc实现的锁
 *
 * @author binking338
 * @date 2023/8/17
 */
class JdbcLocker(
    private val jdbcTemplate: JdbcTemplate,
    private val table: String,
    private val fieldName: String,
    private val fieldPwd: String,
    private val fieldLockAt: String,
    private val fieldUnlockAt: String,
    private val showSql: Boolean = false
) : Locker {

    private val logger = LoggerFactory.getLogger(JdbcLocker::class.java)
    private val lockerExpireMap = ConcurrentHashMap<String, LocalDateTime>()
    private val lockerPwdMap = ConcurrentHashMap<String, String>()

    override fun acquire(key: String, pwd: String, expireDuration: Duration): Boolean {
        val now = LocalDateTime.now()

        return synchronized(this) {
            // Check and clean expired entries from in-memory cache
            lockerExpireMap[key]?.let { expireTime ->
                when {
                    expireTime.isBefore(now) -> {
                        // Expired, remove from cache
                        lockerExpireMap.remove(key)
                        lockerPwdMap.remove(key)
                    }

                    lockerPwdMap[key] != pwd -> {
                        // Not expired but password doesn't match
                        return@synchronized false
                    }
                }
            }

            runCatching {
                // Check if key exists in database
                val countSql = "select count(*) from $table where $fieldName = ?"
                logSqlIfEnabled(countSql, listOf(key))

                val exists = jdbcTemplate.queryForObject(countSql, Int::class.java, key) ?: 0
                val unlockAt = now.plusSeconds(expireDuration.seconds)

                if (exists == 0) {
                    // Insert new lock record
                    val insertSql =
                        "insert into $table($fieldName, $fieldPwd, $fieldLockAt, $fieldUnlockAt) values(?, ?, ?, ?)"
                    logSqlIfEnabled(insertSql, listOf(key, pwd, now, unlockAt))

                    jdbcTemplate.update(insertSql, key, pwd, now, unlockAt)
                    lockerExpireMap[key] = unlockAt
                    lockerPwdMap[key] = pwd
                    true
                } else {
                    // Update existing lock record
                    val updateSql =
                        "update $table set $fieldPwd = ?, $fieldLockAt = ?, $fieldUnlockAt = ? where $fieldName = ? and ($fieldUnlockAt < ? or $fieldPwd = ?)"
                    logSqlIfEnabled(updateSql, listOf(pwd, now, unlockAt, key, now, pwd))

                    val rowsUpdated = jdbcTemplate.update(updateSql, pwd, now, unlockAt, key, now, pwd)
                    if (rowsUpdated > 0) {
                        lockerExpireMap[key] = unlockAt
                        lockerPwdMap[key] = pwd
                        true
                    } else {
                        false
                    }
                }
            }.getOrElse { false }
        }
    }

    override fun release(key: String, pwd: String): Boolean {
        val now = LocalDateTime.now()

        // Check and clean from in-memory cache
        lockerExpireMap[key]?.let {
            when {
                lockerPwdMap[key] == pwd -> {
                    lockerExpireMap.remove(key)
                    lockerPwdMap.remove(key)
                }

                else -> return false
            }
        }

        // Verify lock exists in database with correct password
        val selectSql = "select count(*) from $table where $fieldName = ? and $fieldPwd = ?"
        logSqlIfEnabled(selectSql, listOf(key, pwd))

        val count = jdbcTemplate.queryForObject(selectSql, Int::class.java, key, pwd) ?: 0
        if (count == 0) {
            return false
        }

        // Release the lock by updating unlock time
        val updateSql =
            "update $table set $fieldUnlockAt = ? where $fieldName = ? and $fieldPwd = ? and $fieldUnlockAt > ?"
        logSqlIfEnabled(updateSql, listOf(now, key, pwd, now))

        jdbcTemplate.update(updateSql, now, key, pwd, now)
        return true
    }

    private fun logSqlIfEnabled(sql: String, params: List<Any>) {
        if (showSql) {
            logger.debug(sql)
            logger.debug("binding parameters: $params")
        }
    }
}
