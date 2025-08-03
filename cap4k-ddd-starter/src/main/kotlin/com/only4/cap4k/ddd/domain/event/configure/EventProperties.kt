package com.only4.cap4k.ddd.domain.event.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 领域事件配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.domain.event")
class EventProperties(
    /**
     * 事件扫描包范围
     * 领域事件 & 集成事件
     */
    var eventScanPackage: String = "",

    /**
     * 发布器线程池大小
     * 用于实现延迟发送
     */
    var publisherThreadPoolSize: Int = 4
)
