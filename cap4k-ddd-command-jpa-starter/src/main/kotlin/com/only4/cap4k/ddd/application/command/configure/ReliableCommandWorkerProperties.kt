package com.only4.cap4k.ddd.application.command.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("cap4k.ddd.application.command.worker")
class ReliableCommandWorkerProperties(
    var workerCount: Int = 4,
    var batchSize: Int = 16,
    var pollInterval: Duration = Duration.ofSeconds(10),
    var leaseDuration: Duration = Duration.ofMinutes(2),
    var renewInterval: Duration = Duration.ofSeconds(30),
    var threadFactoryClassName: String = "",
)
