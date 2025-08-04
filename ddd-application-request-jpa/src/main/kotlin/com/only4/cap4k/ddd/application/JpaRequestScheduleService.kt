package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.RequestManager
import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.share.misc.randomString
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 请求调度服务
 * 失败定时重试
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
class JpaRequestScheduleService(
    private val requestManager: RequestManager,
    private val locker: Locker,
    private val compensationLockerKey: String,
    private val archiveLockerKey: String,
    private val enableAddPartition: Boolean,
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(JpaRequestScheduleService::class.java)
    private var compensationRunning = false

    fun init() {
        addPartition()
    }

    /**
     * 请求执行补偿
     */
    fun compense(batchSize: Int, maxConcurrency: Int, interval: Duration, maxLockDuration: Duration) {
        if (compensationRunning) {
            logger.info("请求执行补偿:上次请求执行补偿仍未结束，跳过")
            return
        }

        compensationRunning = true
        try {
            val now = LocalDateTime.now()
            val nextTryTime = now.plus(interval)

            while (true) {
                val processed = processRequestBatch(batchSize, nextTryTime, maxLockDuration)
                if (!processed) break
            }
        } finally {
            compensationRunning = false
        }
    }

    private fun processRequestBatch(batchSize: Int, nextTryTime: LocalDateTime, maxLockDuration: Duration): Boolean {
        val pwd = randomString(8, hasDigital = true, hasLetter = true)

        if (!locker.acquire(compensationLockerKey, pwd, maxLockDuration)) {
            return false
        }

        return try {
            val requestRecords = requestManager.getByNextTryTime(nextTryTime, batchSize)

            if (requestRecords.isEmpty()) {
                return false
            }

            requestRecords.forEach { requestRecord ->
                logger.info("请求执行补偿: {}", requestRecord)
                requestManager.resume(requestRecord, nextTryTime)
            }

            true
        } catch (ex: Exception) {
            logger.error("请求执行补偿:异常失败", ex)
            false
        } finally {
            locker.release(compensationLockerKey, pwd)
        }
    }

    /**
     * 本地请求库归档
     */
    fun archive(expireDays: Int, batchSize: Int, maxLockDuration: Duration) {
        val pwd = randomString(8, hasDigital = true, hasLetter = true)

        if (!locker.acquire(archiveLockerKey, pwd, maxLockDuration)) {
            return
        }

        try {
            logger.info("请求归档")

            val expireDate = LocalDateTime.now().minusDays(expireDays.toLong())
            var failCount = 0

            while (true) {
                try {
                    val archivedCount = requestManager.archiveByExpireAt(expireDate, batchSize)
                    if (archivedCount == 0) {
                        break
                    }
                } catch (ex: Exception) {
                    failCount++
                    logger.error("请求归档:失败", ex)
                    if (failCount >= 3) {
                        logger.info("请求归档:累计3次异常退出任务")
                        break
                    }
                }
            }
        } finally {
            locker.release(archiveLockerKey, pwd)
        }
    }

    /**
     * 添加分区
     */
    fun addPartition() {
        if (!enableAddPartition) {
            return
        }

        val now = LocalDateTime.now()
        addPartition("__request", now.plusMonths(1))
        addPartition("__archived_request", now.plusMonths(1))
    }

    /**
     * 创建date日期所在月下个月的分区
     */
    private fun addPartition(table: String, date: LocalDateTime) {
        val sql =
            "alter table $table add partition (partition p${date.format(DateTimeFormatter.ofPattern("yyyyMM"))} " +
                    "values less than (to_days('${
                        date.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    }-01')) ENGINE=InnoDB)"

        try {
            jdbcTemplate.execute(sql)
        } catch (ex: Exception) {
            if (ex.message?.contains("Duplicate partition") != true) {
                logger.error(
                    "分区创建异常 table = $table partition = p${date.format(DateTimeFormatter.ofPattern("yyyyMM"))}",
                    ex
                )
            }
        }
    }
}
