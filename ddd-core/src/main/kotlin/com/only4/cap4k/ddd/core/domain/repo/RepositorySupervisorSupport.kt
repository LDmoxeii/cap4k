package com.only4.cap4k.ddd.core.domain.repo

/**
 * 仓储管理器配置
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object RepositorySupervisorSupport {
    lateinit var instance: RepositorySupervisor

    fun configure(repositorySupervisor: RepositorySupervisor) {
        instance = repositorySupervisor
    }
}
