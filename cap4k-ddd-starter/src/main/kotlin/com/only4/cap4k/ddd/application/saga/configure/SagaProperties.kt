package com.only4.cap4k.ddd.application.saga.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Saga配置类
 *
 * @author binking338
 * @date 2024/10/15
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.application.saga")
open class SagaProperties(
    /**
     * Saga异步线程池大小
     * 用于实现Saga异步执行
     */
    var asyncThreadPoolSize: Int = 4,
    /**
     * Saga异步线程池工厂类名
     * 用于实现Saga异步执行
     */
    var asyncThreadFactoryClassName: String? = null
)
