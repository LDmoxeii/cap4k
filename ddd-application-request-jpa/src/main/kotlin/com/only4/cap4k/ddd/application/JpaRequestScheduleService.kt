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
 * @author binking338
 * @date 2025/5/17
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

    fun init() {
        addPartition()
    }

    @Volatile
    private var compensationRunning = false

    fun compense(batchSize: Int, maxConcurrency: Int, interval: Duration, maxLockDuration: Duration) {
        if (compensationRunning) {
            logger.info("Request执行补偿:上次Request执行补偿仍未结束，跳过")
            return
        }
        compensationRunning = true

        val pwd = randomString(8, true, true)
        val lockerKey = compensationLockerKey
        try {
            var noneRequest = false
            val now = LocalDateTime.now()
            while (!noneRequest) {
                try {
                    if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        return
                    }
                    val requestRecords = requestManager.getByNextTryTime(now.plus(interval), batchSize)
                    if (requestRecords.isNullOrEmpty()) {
                        noneRequest = true
                        continue
                    }
                    requestRecords.forEach { requestRecord ->
                        logger.info("Request执行补偿: {}", requestRecord)
                        requestManager.resume(requestRecord, now.plus(interval))
                    }
                } catch (ex: Exception) {
                    logger.error("Request执行补偿:异常失败", ex)
                } finally {
                    locker.release(lockerKey, pwd)
                }
            }
        } finally {
            compensationRunning = false
        }
    }

    /**
     * Request归档
     */
    fun archive(expireDays: Int, batchSize: Int, maxLockDuration: Duration) {
        val pwd = randomString(8, true, true)
        val lockerKey = archiveLockerKey

        if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
            return
        }
        logger.info("Request归档")

        val now = LocalDateTime.now()
        var failCount = 0
        while (true) {
            try {
                val archivedCount = requestManager.archiveByExpireAt(now.plusDays(expireDays.toLong()), batchSize)
                if (archivedCount == 0) {
                    break
                }
            } catch (ex: Exception) {
                failCount++
                logger.error("Request归档:失败", ex)
                if (failCount >= 3) {
                    logger.info("Request归档:累计3次异常退出任务")
                    break
                }
            }
        }
        locker.release(lockerKey, pwd)
    }

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
     *
     * @param table
     * @param date
     */
    private fun addPartition(table: String, date: LocalDateTime) {
        val sql = "alter table $table add partition (partition p${date.format(DateTimeFormatter.ofPattern("yyyyMM"))} values less than (to_days('${date.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))}-01')) ENGINE=InnoDB)"
        try {
            jdbcTemplate.execute(sql)
        } catch (ex: Exception) {
            if (!ex.message.orEmpty().contains("Duplicate partition")) {
                logger.error(
                    "分区创建异常 table = {} partition = p{}",
                    table,
                    date.format(DateTimeFormatter.ofPattern("yyyyMM")),
                    ex
                )
            }
        }
    }
}
