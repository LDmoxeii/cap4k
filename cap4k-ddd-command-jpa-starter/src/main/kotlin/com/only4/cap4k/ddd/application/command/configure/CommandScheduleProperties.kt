package com.only4.cap4k.ddd.application.command.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.application.command.schedule")
class CommandScheduleProperties(
    var retryBatchSize: Int = 10,
    var retryIntervalSeconds: Int = 60,
    var retryMaxLockSeconds: Int = 30,
    var retryCron: String = "0 */1 * * * ?",
    var archiveBatchSize: Int = 100,
    var archiveExpireDays: Int = 7,
    var archiveMaxLockSeconds: Int = 172800,
    var archiveCron: String = "0 0 2 * * ?",
    var addPartitionEnable: Boolean = true,
    var addPartitionCron: String = "0 0 0 * * ?",
)
