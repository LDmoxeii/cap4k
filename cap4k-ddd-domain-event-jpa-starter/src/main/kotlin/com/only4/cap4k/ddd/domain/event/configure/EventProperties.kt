package com.only4.cap4k.ddd.domain.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.domain.event")
class EventProperties(
    var publisherThreadPoolSize: Int = 4,
)
