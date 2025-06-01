package com.only4.cap4k.ddd.core

import org.springframework.context.ApplicationContext

/**
 * 中介者配置
 *
 * @author binking338
 * @date 2024/8/24
 */
object MediatorSupport {
    lateinit var instance: Mediator

    lateinit var ioc: ApplicationContext

    fun configure(mediator: Mediator) {
        instance = mediator
    }

    fun configure(applicationContext: ApplicationContext) {
        ioc = applicationContext
    }
}
