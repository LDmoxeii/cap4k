package com.only4.cap4k.ddd.application.request.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.application.request.schedule")
class RequestScheduleProperties(
    var compenseBatchSize: Int = 10,
    var compenseMaxConcurrency: Int = 10,
    var compenseIntervalSeconds: Int = 60,
    var compenseMaxLockSeconds: Int = 30,
    var compenseCron: String = "0 */1 * * * ?",
    var archiveBatchSize: Int = 100,
    var archiveExpireDays: Int = 7,
    var archiveMaxLockSeconds: Int = 172800,
    var archiveCron: String = "0 0 2 * * ?",
    var addPartitionEnable: Boolean = true,
    var addPartitionCron: String = "0 0 0 * * ?",
)
