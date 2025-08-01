package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.application.saga.SagaManager
import com.only4.cap4k.ddd.core.share.misc.randomString
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Saga调度服务
 * 失败定时重试
 *
 * @author binking338
 * @date 2024/10/14
 */
class JpaSagaScheduleService(
    private val sagaManager: SagaManager,
    private val locker: Locker,
    private val compensationLockerKey: String,
    private val archiveLockerKey: String,
    private val enableAddPartition: Boolean,
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(JpaSagaScheduleService::class.java)
    private var compensationRunning = false

    fun init() {
        addPartition()
    }

    /**
     * Saga执行补偿
     */
    fun compense(batchSize: Int, maxConcurrency: Int, interval: Duration, maxLockDuration: Duration) {
        if (compensationRunning) {
            logger.info("Saga执行补偿:上次Saga执行补偿仍未结束，跳过")
            return
        }

        compensationRunning = true
        val pwd = randomString(8, hasDigital = true, hasLetter = true)
        val lockerKey = compensationLockerKey

        try {
            var noneSaga = false
            val now = LocalDateTime.now()

            while (!noneSaga) {
                var lockAcquired = false
                try {
                    if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        return
                    }
                    lockAcquired = true

                    val sagaRecords = sagaManager.getByNextTryTime(now.plus(interval), batchSize)

                    if (sagaRecords.isEmpty()) {
                        noneSaga = true
                        continue
                    }

                    for (sagaRecord in sagaRecords) {
                        logger.info("Saga执行补偿: {}", sagaRecord)
                        sagaManager.resume(sagaRecord, now.plus(interval))
                    }
                } catch (ex: Exception) {
                    logger.error("Saga执行补偿:异常失败", ex)
                } finally {
                    if (lockAcquired) {
                        locker.release(lockerKey, pwd)
                    }
                }
            }
        } finally {
            compensationRunning = false
        }
    }

    /**
     * Saga归档
     */
    fun archive(expireDays: Int, batchSize: Int, maxLockDuration: Duration) {
        val pwd = randomString(8, hasDigital = true, hasLetter = true)
        val lockerKey = archiveLockerKey

        if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
            return
        }

        logger.info("Saga归档")

        val now = LocalDateTime.now()
        var failCount = 0

        while (true) {
            try {
                val archivedCount = sagaManager.archiveByExpireAt(now.plusDays(expireDays.toLong()), batchSize)
                if (archivedCount == 0) {
                    break
                }
            } catch (ex: Exception) {
                failCount++
                logger.error("Saga归档:失败", ex)
                if (failCount >= 3) {
                    logger.info("Saga归档:累计3次异常退出任务")
                    break
                }
            }
        }

        locker.release(lockerKey, pwd)
    }

    /**
     * 添加分区
     */
    fun addPartition() {
        if (!enableAddPartition) {
            return
        }

        val now = LocalDateTime.now()
        addPartition("__saga", now.plusMonths(1))
        addPartition("__archived_saga", now.plusMonths(1))
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