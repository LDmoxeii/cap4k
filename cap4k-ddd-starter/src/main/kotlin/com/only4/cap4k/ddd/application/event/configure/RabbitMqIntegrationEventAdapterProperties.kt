package com.only4.cap4k.ddd.application.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * RabbitMq集成事件适配器配置
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.integration.event.rabbitmq")
class RabbitMqIntegrationEventAdapterProperties(
    /**
     * 异步发送线程池大小
     */
    var publishThreadPoolSize: Int = 4,
    /**
     * 异步发送线程工厂类名
     */
    var publishThreadFactoryClassName: String = "",
    /**
     * 是否自动声明交换机
     */
    var autoDeclareExchange: Boolean = true,
    /**
     * 是否自动声明队列
     */
    var autoDeclareQueue: Boolean = true,
    /**
     * 默认交换机类型
     */
    var defaultExchangeType: String = "direct"
)
