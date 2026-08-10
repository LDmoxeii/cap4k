package com.only4.cap4k.ddd.application.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("cap4k.ddd.integration.event.rocketmq")
class RocketMqIntegrationEventAdapterProperties(
    var routes: MutableMap<String, RocketMqIntegrationEventRouteProperties> = linkedMapOf(),
    var recoveryInterval: Duration = Duration.ofSeconds(5),
)

class RocketMqIntegrationEventRouteProperties(
    var topic: String = "",
    var tag: String = "",
)
