package com.only4.cap4k.ddd.application.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * http集成事件适配器配置
 *
 * @author binking338
 * @date 2025/5/21
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.integration.event.http")
open class HttpIntegrationEventAdapterProperties(
    /**
     * 异步发送线程池大小
     */
    var publishThreadPoolSize: Int = 4,
    /**
     * 异步发送线程工厂类名
     */
    var publishThreadFactoryClassName: String = ""
)
