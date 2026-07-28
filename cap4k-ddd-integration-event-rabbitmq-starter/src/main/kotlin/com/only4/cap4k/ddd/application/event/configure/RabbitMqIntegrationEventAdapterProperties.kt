package com.only4.cap4k.ddd.application.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.integration.event.rabbitmq")
class RabbitMqIntegrationEventAdapterProperties(
    var publishThreadPoolSize: Int = 4,
    var publishThreadFactoryClassName: String = "",
    var autoDeclareExchange: Boolean = true,
    var autoDeclareQueue: Boolean = true,
    var defaultExchangeType: String = "direct",
)
