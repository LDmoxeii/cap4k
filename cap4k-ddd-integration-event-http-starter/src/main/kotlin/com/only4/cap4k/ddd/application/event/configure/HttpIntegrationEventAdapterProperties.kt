package com.only4.cap4k.ddd.application.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.integration.event.http")
class HttpIntegrationEventAdapterProperties(
    var publishThreadPoolSize: Int = 4,
    var publishThreadFactoryClassName: String = "",
    var routes: MutableMap<String, String> = linkedMapOf(),
)
