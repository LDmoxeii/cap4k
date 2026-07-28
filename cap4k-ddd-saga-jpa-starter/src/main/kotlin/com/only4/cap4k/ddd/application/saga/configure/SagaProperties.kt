package com.only4.cap4k.ddd.application.saga.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.application.saga")
class SagaProperties(
    var asyncThreadPoolSize: Int = 4,
    var asyncThreadFactoryClassName: String = "",
)
