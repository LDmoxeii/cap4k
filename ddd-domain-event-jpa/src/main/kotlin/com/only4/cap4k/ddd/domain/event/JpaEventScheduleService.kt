package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.distributed.Locker
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.share.misc.TextUtils
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 事件调度服务
 * 失败定时重试
 */
class JpaEventScheduleService(
    private val eventPublisher: EventPublisher,
    private val eventRecordRepository: EventRecordRepository,
    private val locker: Locker,
    private val svcName: String,
    private val compensationLockerKey: String,
    private val archiveLockerKey: String,
    private val enableAddPartition: Boolean,
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(JpaEventScheduleService::class.java)
    private var compensationRunning = false

    // 懒加载初始化分区
    private val partitionInitializer by lazy {
        addPartition()
    }

    /**
     * 事件发送补偿
     */
    fun compense(batchSize: Int, maxConcurrency: Int, interval: Duration, maxLockDuration: Duration) {
        if (compensationRunning) {
            logger.info("事件发送补偿:上次事件发送补偿仍未结束，跳过")
            return
        }

        compensationRunning = true
        val pwd = TextUtils.randomString(8, hasDigital = true, hasLetter = true)
        val lockerKey = compensationLockerKey

        try {
            var noneEvent = false
            val now = LocalDateTime.now()

            while (!noneEvent) {
                try {
                    if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        return
                    }

                    val eventRecords = eventRecordRepository.getByNextTryTime(svcName, now.plus(interval), batchSize)

                    if (eventRecords.isEmpty()) {
                        noneEvent = true
                        continue
                    }

                    for (eventRecord in eventRecords) {
                        logger.info("事件发送补偿: {}", eventRecord)
                        eventPublisher.retry(eventRecord, now.plus(interval))
                    }
                } catch (ex: Exception) {
                    logger.error("事件发送补偿:异常失败", ex)
                } finally {
                    locker.release(lockerKey, pwd)
                }
            }
        } finally {
            compensationRunning = false
        }
    }

    /**
     * 本地事件库归档
     */
    fun archive(expireDays: Int, batchSize: Int, maxLockDuration: Duration) {

        val pwd = TextUtils.randomString(8, hasDigital = true, hasLetter = true)
        val lockerKey = archiveLockerKey

        if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
            return
        }

        logger.info("事件归档")

        val now = LocalDateTime.now()
        var failCount = 0

        while (true) {
            try {
                val archivedCount =
                    eventRecordRepository.archiveByExpireAt(svcName, now.plusDays(expireDays.toLong()), batchSize)
                if (archivedCount == 0) {
                    break
                }
            } catch (ex: Exception) {
                failCount++
                logger.error("事件归档:失败", ex)
                if (failCount >= 3) {
                    logger.info("事件归档:累计3次异常退出任务")
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
        addPartition("__event", now.plusMonths(1))
        addPartition("__archived_event", now.plusMonths(1))
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
            if (!ex.message?.contains("Duplicate partition")!!) {
                logger.error(
                    "分区创建异常 table = $table partition = p${date.format(DateTimeFormatter.ofPattern("yyyyMM"))}",
                    ex
                )
            }
        }
    }
}
