package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.core.application.command.CommandManager
import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.share.misc.randomString
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 命令调度服务
 * 失败定时重试
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
class JpaCommandScheduleService(
    private val commandManager: CommandManager,
    private val locker: Locker,
    private val retryLockerKey: String,
    private val archiveLockerKey: String,
    private val enableAddPartition: Boolean,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(JpaCommandScheduleService::class.java)
    private var retryRunning = false

    fun init() {
        addPartition()
    }

    /**
     * 重试到期但尚未成功的可靠命令
     */
    fun retry(batchSize: Int, interval: Duration, maxLockDuration: Duration) {
        if (retryRunning) {
            log.info("可靠命令重试:上次重试仍未结束，跳过")
            return
        }

        retryRunning = true
        try {
            val now = LocalDateTime.now()
            val nextTryTime = now.plus(interval)

            while (true) {
                val processed = processCommandBatch(batchSize, nextTryTime, maxLockDuration)
                if (!processed) break
            }
        } finally {
            retryRunning = false
        }
    }

    private fun processCommandBatch(batchSize: Int, nextTryTime: LocalDateTime, maxLockDuration: Duration): Boolean {
        val pwd = randomString(8, hasDigital = true, hasLetter = true)

        if (!locker.acquire(retryLockerKey, pwd, maxLockDuration)) {
            return false
        }

        return try {
            val commandRecords = commandManager.getByNextTryTime(nextTryTime, batchSize)

            if (commandRecords.isEmpty()) {
                return false
            }

            commandRecords.forEach { commandRecord ->
                log.info("可靠命令重试: {}", commandRecord)
                commandManager.resume(commandRecord, nextTryTime)
            }

            true
        } catch (ex: Exception) {
            log.error("可靠命令重试:异常失败", ex)
            false
        } finally {
            locker.release(retryLockerKey, pwd)
        }
    }

    /**
     * 本地命令库归档
     */
    fun archive(expireDays: Int, batchSize: Int, maxLockDuration: Duration) {
        val pwd = randomString(8, hasDigital = true, hasLetter = true)

        if (!locker.acquire(archiveLockerKey, pwd, maxLockDuration)) {
            return
        }

        try {
            log.info("命令归档")

            val expireDate = LocalDateTime.now().minusDays(expireDays.toLong())
            var failCount = 0

            while (true) {
                try {
                    val archivedCount = commandManager.archiveByExpireAt(expireDate, batchSize)
                    if (archivedCount == 0) {
                        break
                    }
                } catch (ex: Exception) {
                    failCount++
                    log.error("命令归档:失败", ex)
                    if (failCount >= 3) {
                        log.info("命令归档:累计3次异常退出任务")
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
        addPartition("__command", now.plusMonths(1))
        addPartition("__archived_command", now.plusMonths(1))
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
