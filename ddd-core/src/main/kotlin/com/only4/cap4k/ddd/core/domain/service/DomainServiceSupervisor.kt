package com.only4.cap4k.ddd.core.domain.service

/**
 * 领域服务管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface DomainServiceSupervisor {
    /**
     * 获取领域服务
     * @param domainServiceClass
     * @return
     * @param <DOMAIN_SERVICE>
    </DOMAIN_SERVICE> */
    fun <DOMAIN_SERVICE> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE?

    companion object {
        val instance: DomainServiceSupervisor
            get() = DomainServiceSupervisorSupport.instance
    }
}
