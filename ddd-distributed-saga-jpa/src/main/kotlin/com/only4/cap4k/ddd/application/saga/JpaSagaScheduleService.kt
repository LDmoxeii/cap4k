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
    private val log = LoggerFactory.getLogger(JpaSagaScheduleService::class.java)
    private var compensationRunning = false

    fun init() {
        addPartition()
    }

    /**
     * Saga执行补偿
     */
    fun compense(batchSize: Int, maxConcurrency: Int, interval: Duration, maxLockDuration: Duration) {
        if (compensationRunning) {
            log.info("Saga执行补偿:上次Saga执行补偿仍未结束，跳过")
            return
        }

        compensationRunning = true
        try {
            val now = LocalDateTime.now()
            val nextTryTime = now.plus(interval)

            while (true) {
                val processed = processSagaBatch(batchSize, nextTryTime, maxLockDuration)
                if (!processed) break
            }
        } finally {
            compensationRunning = false
        }
    }

    private fun processSagaBatch(batchSize: Int, nextTryTime: LocalDateTime, maxLockDuration: Duration): Boolean {
        val pwd = randomString(8, hasDigital = true, hasLetter = true)

        return try {
            if (!locker.acquire(compensationLockerKey, pwd, maxLockDuration)) {
                return false
            }

            try {
                val sagaRecords = sagaManager.getByNextTryTime(nextTryTime, batchSize)

                if (sagaRecords.isEmpty()) {
                    return false
                }

                sagaRecords.forEach { sagaRecord ->
                    log.info("Saga执行补偿: {}", sagaRecord)
                    sagaManager.resume(sagaRecord, nextTryTime)
                }

                true
            } catch (ex: Exception) {
                log.error("Saga执行补偿:异常失败", ex)
                false
            } finally {
                locker.release(compensationLockerKey, pwd)
            }
        } catch (ex: Exception) {
            log.error("Saga执行补偿:锁获取异常", ex)
            false
        }
    }

    /**
     * Saga归档
     */
    fun archive(expireDays: Int, batchSize: Int, maxLockDuration: Duration) {
        val pwd = randomString(8, hasDigital = true, hasLetter = true)

        if (!locker.acquire(archiveLockerKey, pwd, maxLockDuration)) {
            return
        }

        try {
            log.info("Saga归档")

            val expireDate = LocalDateTime.now().minusDays(expireDays.toLong())
            var failCount = 0

            while (true) {
                try {
                    val archivedCount = sagaManager.archiveByExpireAt(expireDate, batchSize)
                    if (archivedCount == 0) {
                        break
                    }
                } catch (ex: Exception) {
                    failCount++
                    log.error("Saga归档:失败", ex)
                    if (failCount >= 3) {
                        log.info("Saga归档:累计3次异常退出任务")
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
                log.error(
                    "分区创建异常 table = $table partition = p${date.format(DateTimeFormatter.ofPattern("yyyyMM"))}",
                    ex
                )
            }
        }
    }
}
