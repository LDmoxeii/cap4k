package com.only4.cap4k.ddd.application.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.integration.event.rocketmq")
class RocketMqIntegrationEventAdapterProperties(
    var routes: MutableMap<String, RocketMqIntegrationEventRouteProperties> = linkedMapOf(),
)

class RocketMqIntegrationEventRouteProperties(
    var topic: String = "",
    var tag: String = "",
)
