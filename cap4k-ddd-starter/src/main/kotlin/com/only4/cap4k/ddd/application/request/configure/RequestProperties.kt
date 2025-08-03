package com.only4.cap4k.ddd.application.request.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Mediator配置类
 *
 * @author binking338
 * @date 2025/5/15
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.application")
open class RequestProperties(
    /**
     * 请求调度线程池大小
     */
    var requestScheduleThreadPoolSize: Int = 10,

    /**
     * 请求调度线程工厂类名
     */
    var requestScheduleThreadFactoryClassName: String? = null
)
