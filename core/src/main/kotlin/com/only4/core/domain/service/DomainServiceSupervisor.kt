package com.only4.core.domain.service

/**
 * 领域服务管理器
 *
 * @author binking338
 * @date 2024/9/4
 */
interface DomainServiceSupervisor {
    /**
     * 获取领域服务
     * @param domainServiceClass
     * @return
     * @param <DOMAIN_SERVICE>
    </DOMAIN_SERVICE> */
    fun <DOMAIN_SERVICE> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE

    companion object {
        val instance: DomainServiceSupervisor
            get() = DomainServiceSupervisorSupport.instance
    }
}
