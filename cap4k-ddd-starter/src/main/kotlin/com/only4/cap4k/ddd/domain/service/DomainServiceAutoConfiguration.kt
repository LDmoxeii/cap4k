package com.only4.cap4k.ddd.domain.service

import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisorSupport
import com.only4.cap4k.ddd.core.domain.service.impl.DefaultDomainServiceSupervisor
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 领域服务自动配置
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
class DomainServiceAutoConfiguration {

    /**
     * 默认领域服务管理器
     *
     * @param applicationContext
     * @return
     */
    @Bean
    fun defaultDomainServiceSupervisor(applicationContext: ApplicationContext): DefaultDomainServiceSupervisor {
        return DefaultDomainServiceSupervisor(applicationContext).apply {
            DomainServiceSupervisorSupport.configure(this)
        }
    }
}
