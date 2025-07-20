package com.only4.cap4k.ddd.core.domain.service

/**
 * 领域服务管理
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object DomainServiceSupervisorSupport {
    lateinit var instance: DomainServiceSupervisor

    fun configure(domainServiceSupervisor: DomainServiceSupervisor) {
        instance = domainServiceSupervisor
    }
}
