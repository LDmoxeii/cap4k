package com.only4.cap4k.ddd.domain.distributed.snowflake

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.net.InetAddress
import java.time.LocalDateTime

/**
 * WorkerId调度器
 */
interface SnowflakeWorkerIdDispatcher {
    /**
     * 获取WorkerId占用
     *
     * @param workerId 指定workerId
     * @param datacenterId 指定datacenterId
     * @return 分配的workerId
     */
    fun acquire(workerId: Long, datacenterId: Long): Long

    /**
     * 释放WorkerId占用
     */
    fun release()

    /**
     * 心跳上报
     *
     * @return 心跳是否成功
     */
    fun pong(): Boolean

    /**
     * 心跳上报如果长期失联，失败累计到一定次数，提醒运维或相关人员，以便介入处理
     */
    fun remind()
}

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
    private val expireMinutes: Long,
    private val localHostIdentify: String = "",
    private val showSql: Boolean = false
) : SnowflakeWorkerIdDispatcher {

    private val logger = LoggerFactory.getLogger(DefaultSnowflakeWorkerIdDispatcher::class.java)
    private var cachedWorkerId: Long? = null

    /**
     * 懒加载初始化workerId表
     */
    private val initializer by lazy {
        val total = jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)
        if (total != null && total >= 1024) {
            return@lazy
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
    override fun acquire(workerId: Long, datacenterId: Long): Long {
        cachedWorkerId?.let { return it }

        val now = LocalDateTime.now()
        var where = "where ($fieldDispatchTo=? or $fieldExpireAt<?)"

        if (workerId != 0L) {
            where += " and $fieldWorkerId=$workerId"
        }

        if (datacenterId != 0L) {
            where += " and $fieldDatacenterId=$datacenterId"
        }

        val sql =
            "SELECT (($fieldDatacenterId<<5) + $fieldWorkerId) as r from $table $where order by $fieldExpireAt asc limit 1"

        if (showSql) {
            logger.debug(sql)
            logger.debug("binding parameters: [${getHostIdentify()}, $now]")
        }

        val hostIdentify = getHostIdentify()
        val resultList = jdbcTemplate.queryForList(sql, Long::class.java, hostIdentify, now)
        val result = resultList.firstOrNull() ?: throw RuntimeException("WorkerId分发失败")

        val updateSql = "UPDATE $table set $fieldDispatchTo = ?, $fieldDispatchAt = ?, $fieldExpireAt = ? " +
                "WHERE ($fieldDispatchTo=? or $fieldExpireAt<?) and (($fieldDatacenterId<<5) + $fieldWorkerId) = ?"

        if (showSql) {
            logger.debug(updateSql)
            logger.debug("binding parameters: [$hostIdentify, $now, ${now.plusMinutes(expireMinutes)}, $hostIdentify, $now, $result]")
        }

        val success = jdbcTemplate.update(
            updateSql,
            hostIdentify,
            now,
            now.plusMinutes(expireMinutes),
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

        if (showSql) {
            logger.debug(sql)
            logger.debug("binding parameters: [$now, $hostIdentify, $cachedId]")
        }

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

        if (showSql) {
            logger.debug(sql)
            logger.debug("binding parameters: [${now.plusMinutes(expireMinutes)}, $hostIdentify, $cachedId]")
        }

        val success = jdbcTemplate.update(sql, now.plusMinutes(expireMinutes), hostIdentify, cachedId)
        return success > 0
    }

    /**
     * 心跳上报如果长期失联，失败累计到一定次数，提醒运维或相关人员，以便介入处理
     */
    override fun remind() {
        // 实现提醒逻辑
    }
}
