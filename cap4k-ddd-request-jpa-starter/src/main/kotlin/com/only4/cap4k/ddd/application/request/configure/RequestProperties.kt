package com.only4.cap4k.ddd.application.request.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.application")
class RequestProperties(
    var requestScheduleThreadPoolSize: Int = 10,
    var requestScheduleThreadFactoryClassName: String = "",
)
