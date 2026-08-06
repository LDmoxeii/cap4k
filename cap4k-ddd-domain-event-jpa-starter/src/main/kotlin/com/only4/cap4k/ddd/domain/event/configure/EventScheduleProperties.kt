package com.only4.cap4k.ddd.domain.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.domain.event.schedule")
class EventScheduleProperties(
    var retryBatchSize: Int = 10,
    var retryIntervalSeconds: Int = 60,
    var retryMaxLockSeconds: Int = 30,
    var retryCron: String = "0 */1 * * * ?",
    var addPartitionEnable: Boolean = true,
    var addPartitionCron: String = "0 0 0 * * ?",
)
