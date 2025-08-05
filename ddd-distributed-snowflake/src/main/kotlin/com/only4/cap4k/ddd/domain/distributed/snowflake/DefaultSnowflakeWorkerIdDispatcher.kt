package com.only4.cap4k.ddd.domain.distributed.snowflake

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.net.InetAddress
import java.time.LocalDateTime

/**
 * 默认WorkerId分配器
 */
class DefaultSnowflakeWorkerIdDispatcher(
    private val jdbcTemplate: JdbcTemplate,
    private val table: String,
    private val fieldDatacenterId: String,
    private val fieldWorkerId: String,
    private val fieldDispatchTo: String,
    private val fieldDispatchAt: String,
    private val fieldExpireAt: String,
    private val expireMinutes: Int,
    private val localHostIdentify: String = "",
    private val showSql: Boolean = false
) : SnowflakeWorkerIdDispatcher {

    private val logger = LoggerFactory.getLogger(DefaultSnowflakeWorkerIdDispatcher::class.java)
    private var cachedWorkerId: Long? = null

    /**
     * 初始化workerId表
     */
    fun init() {
        val total = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)
        if (total != null && total >= 1024) {
            return
        }

        for (datacenterId in 0 until 32) {
            for (workerId in 0 until 32) {
                val count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM $table WHERE $fieldDatacenterId=$datacenterId and $fieldWorkerId=$workerId",
                    Long::class.java
                )

                if (count != null && count == 0L) {
                    jdbcTemplate.execute(
                        "INSERT INTO $table ($fieldDatacenterId, $fieldWorkerId) VALUES ($datacenterId, $workerId)"
                    )
                }
            }
        }
    }

    /**
     * 获取主机标识
     */
    private fun getHostIdentify(): String {
        return localHostIdentify.ifBlank {
            InetAddress.getLocalHost().hostAddress
        }
    }

    /**
     * 获取WorkerId占用
     */
    override fun acquire(workerId: Long?, datacenterId: Long?): Long {
        cachedWorkerId?.let { return it }

        val now = LocalDateTime.now()
        var where = "where ($fieldDispatchTo=? or $fieldExpireAt<?)"

        workerId?.let {
            where += " and $fieldWorkerId=$it"
        }

        datacenterId?.let {
            where += " and $fieldDatacenterId=$it"
        }

        val sql =
            "SELECT (($fieldDatacenterId<<5) + $fieldWorkerId) as r from $table $where order by $fieldExpireAt asc limit 1"

        logSqlIfEnabled(sql, listOf(getHostIdentify(), now))

        val hostIdentify = getHostIdentify()
        val resultList = jdbcTemplate.queryForList(sql, Long::class.java, hostIdentify, now)
        val result = resultList.firstOrNull() ?: throw RuntimeException("WorkerId分发失败")

        val updateSql = "UPDATE $table set $fieldDispatchTo = ?, $fieldDispatchAt = ?, $fieldExpireAt = ? " +
                "WHERE ($fieldDispatchTo=? or $fieldExpireAt<?) and (($fieldDatacenterId<<5) + $fieldWorkerId) = ?"

        logSqlIfEnabled(
            updateSql, listOf(
                hostIdentify, now, now.plusMinutes(expireMinutes.toLong()),
                hostIdentify, now, result
            )
        )

        val success = jdbcTemplate.update(
            updateSql,
            hostIdentify,
            now,
            now.plusMinutes(expireMinutes.toLong()),
            hostIdentify,
            now,
            result
        )

        if (success <= 0) {
            throw RuntimeException("WorkerId分发失败")
        }

        cachedWorkerId = result
        return result
    }

    /**
     * 释放WorkerId占用
     */
    override fun release() {
        val cachedId = cachedWorkerId ?: return
        val now = LocalDateTime.now()
        val hostIdentify = getHostIdentify()

        val sql =
            "UPDATE $table set $fieldExpireAt = ? WHERE $fieldDispatchTo = ? and (($fieldDatacenterId<<5) + $fieldWorkerId) = ?"

        logSqlIfEnabled(sql, listOf(now, hostIdentify, cachedId))

        val success = jdbcTemplate.update(sql, now, hostIdentify, cachedId)

        if (success <= 0) {
            throw RuntimeException("WorkerId释放失败")
        }
    }

    /**
     * 心跳上报
     */
    override fun pong(): Boolean {
        val cachedId = cachedWorkerId ?: return false
        val now = LocalDateTime.now()
        val hostIdentify = getHostIdentify()

        val sql =
            "UPDATE $table set $fieldExpireAt = ? WHERE $fieldDispatchTo = ? and (($fieldDatacenterId<<5) + $fieldWorkerId) = ?"

        logSqlIfEnabled(sql, listOf(now.plusMinutes(expireMinutes.toLong()), hostIdentify, cachedId))

        val success = jdbcTemplate.update(sql, now.plusMinutes(expireMinutes.toLong()), hostIdentify, cachedId)
        return success > 0
    }

    /**
     * 心跳上报如果长期失联，失败累计到一定次数，提醒运维或相关人员，以便介入处理
     */
    override fun remind() {
        // 实现提醒逻辑
    }

    private fun logSqlIfEnabled(sql: String, params: List<Any>) {
        if (showSql) {
            logger.debug(sql)
            logger.debug("binding parameters: {}", params)
        }
    }
}
