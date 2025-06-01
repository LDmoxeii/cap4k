package com.only4.ddd.distributed.locker.jdbc

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.sun.org.slf4j.internal.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * JDBC implementation of distributed locker
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

        synchronized(this) {
            // Check if key exists in the map
            lockerExpireMap[key]?.let { expireTime ->
                // If expired, remove from maps
                if (expireTime.isBefore(now)) {
                    lockerExpireMap.remove(key)
                    lockerPwdMap.remove(key)
                } else {
                    // If not expired and password doesn't match, return false
                    if (lockerPwdMap[key] != pwd) {
                        return false
                    } else return@let
                }
            }

            // Check if key exists in database
            val sql = "select count(*) from $table where $fieldName = ?"
            if (showSql) {
                logger.debug(sql)
                logger.debug("binding parameters: [$key]")
            }

            val exists = jdbcTemplate.queryForObject(sql, Int::class.java, key)

            if (exists == 0) {
                try {
                    // Insert new lock record
                    val unlockAt = now.plusSeconds(expireDuration.seconds)
                    val insertSql =
                        "insert into $table($fieldName, $fieldPwd, $fieldLockAt, $fieldUnlockAt) values(?, ?, ?, ?)"

                    if (showSql) {
                        logger.debug(insertSql)
                        logger.debug("binding parameters: [$key, $pwd, $now, $unlockAt]")
                    }

                    jdbcTemplate.update(insertSql, key, pwd, now, unlockAt)
                    lockerExpireMap[key] = unlockAt
                    lockerPwdMap[key] = pwd
                    return true
                } catch (e: Exception) {
                    return false
                }
            } else {
                try {
                    // Update existing lock record
                    val unlockAt = now.plusSeconds(expireDuration.seconds)
                    val updateSql =
                        "update $table set $fieldPwd = ?, $fieldLockAt = ?, $fieldUnlockAt = ? where $fieldName = ? and ($fieldUnlockAt < ? or $fieldPwd = ?)"

                    if (showSql) {
                        logger.debug(updateSql)
                        logger.debug("binding parameters: [$pwd, $now, $unlockAt, $key, $now, $pwd]")
                    }

                    val success = jdbcTemplate.update(updateSql, pwd, now, unlockAt, key, now, pwd)
                    return success > 0
                } catch (e: Exception) {
                    return false
                }
            }
        }
    }

    override fun release(key: String, pwd: String): Boolean {
        val now = LocalDateTime.now()

        // Check if key exists in the map
        lockerExpireMap[key]?.let {
            if (lockerPwdMap[key] == pwd) {
                lockerExpireMap.remove(key)
                lockerPwdMap.remove(key)
            } else {
                return false
            }
        }

        // Check if key and password match in database
        val selectSql = "select count(*) from $table where $fieldName = ? and $fieldPwd = ?"
        if (showSql) {
            logger.debug(selectSql)
            logger.debug("binding parameters: [$key, $pwd]")
        }

        val count = jdbcTemplate.queryForObject(selectSql, Int::class.java, key, pwd)
        if (count == 0) {
            return false
        }

        // Update unlock time
        val updateSql =
            "update $table set $fieldUnlockAt = ? where $fieldName = ? and $fieldPwd = ? and $fieldUnlockAt > ?"
        if (showSql) {
            logger.debug(updateSql)
            logger.debug("binding parameters: [$now, $key, $pwd, $now]")
        }

        jdbcTemplate.update(updateSql, now, key, pwd, now)
        return true
    }
}
