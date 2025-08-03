package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.impl.DefaultMediator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * CQS自动配置类
 *
 * @author binking338
 * @date 2024/8/24
 */
@Configuration
open class MediatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Mediator::class)
    open fun defaultMediator(applicationContext: ApplicationContext): DefaultMediator {
        val defaultMediator = DefaultMediator()
        MediatorSupport.configure(defaultMediator)
        MediatorSupport.configure(applicationContext)
        return defaultMediator
    }
}
