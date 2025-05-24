package com.only4.core.domain.service

/**
 * 领域服务管理
 *
 * @author binking338
 * @date 2024/9/4
 */
object DomainServiceSupervisorSupport {
    lateinit var instance: DomainServiceSupervisor

    fun configure(domainServiceSupervisor: DomainServiceSupervisor) {
        instance = domainServiceSupervisor
    }
}
