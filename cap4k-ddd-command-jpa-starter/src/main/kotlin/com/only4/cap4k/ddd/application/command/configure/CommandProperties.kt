package com.only4.cap4k.ddd.application.command.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.application")
class CommandProperties(
    var commandScheduleThreadPoolSize: Int = 10,
    var commandScheduleThreadFactoryClassName: String = "",
)
