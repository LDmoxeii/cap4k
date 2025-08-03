package com.only4.cap4k.ddd.application.saga.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * saga调度配置
 *
 * @author binking338
 * @date 2024/10/15
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.application.saga.schedule")
open class SagaScheduleProperties(
    /**
     * 补偿发送-批量查询事件数量
     */
    var compenseBatchSize: Int = 10,
    /**
     * 补偿发送-最大并行线程（进程）数
     */
    var compenseMaxConcurrency: Int = 10,
    /**
     * 补偿发送-间隔（秒）
     */
    var compenseIntervalSeconds: Int = 60,
    /**
     * 补偿发送-分布式锁时长（秒）
     */
    var compenseMaxLockSeconds: Int = 30,
    /**
     * 补偿发送-CRON
     */
    var compenseCron: String = "0 */1 * * * ?",

    /**
     * 记录归档-批量查询事件数量
     */
    var archiveBatchSize: Int = 100,
    /**
     * 记录归档-保留时长
     */
    var archiveExpireDays: Int = 7,
    /**
     * 记录归档-分布式锁时长（秒）
     */
    var archiveMaxLockSeconds: Int = 172800,
    /**
     * 记录归档-CRON
     */
    var archiveCron: String = "0 0 2 * * ?",

    /**
     * 分区表-启用添加分区
     */
    var addPartitionEnable: Boolean = true,
    /**
     * 分区表-添加分区CRON
     */
    var addPartitionCron: String = "0 0 0 * * ?"
)
