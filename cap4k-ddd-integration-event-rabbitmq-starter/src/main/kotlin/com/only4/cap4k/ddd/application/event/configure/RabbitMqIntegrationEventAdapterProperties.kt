package com.only4.cap4k.ddd.application.event.configure

import com.only4.cap4k.ddd.application.event.RabbitMqIntegrationEventRoute
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("cap4k.ddd.integration-event.rabbitmq")
class RabbitMqIntegrationEventAdapterProperties(
    var publishThreadPoolSize: Int = 4,
    var publishThreadFactoryClassName: String = "",
    var routes: MutableMap<String, RabbitMqIntegrationEventRoute> = linkedMapOf(),
    var confirmTimeout: Duration = Duration.ofSeconds(10),
    var exchangeType: String = "direct",
    var messageCharset: String = "UTF-8",
    var recoveryInterval: Duration = Duration.ofSeconds(5),
)
