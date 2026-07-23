package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.impl.DefaultMediator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * CQS自动配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
class MediatorAutoConfiguration {

    @Bean
    fun mediatorSupportInitializer(
        mediator: Mediator,
        applicationContext: ApplicationContext,
        identifierGenerator: IdentifierGenerator,
    ): InitializingBean = InitializingBean {
        MediatorSupport.configure(mediator)
        MediatorSupport.configure(applicationContext)
        MediatorSupport.configure(identifierGenerator)
    }

    @Bean
    @ConditionalOnMissingBean(Mediator::class)
    fun defaultMediator(
        identifierGenerator: IdentifierGenerator,
    ): DefaultMediator =
        DefaultMediator(identifierGenerator)
}
