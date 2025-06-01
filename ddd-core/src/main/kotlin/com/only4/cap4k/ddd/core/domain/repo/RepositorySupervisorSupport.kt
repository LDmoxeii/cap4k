package com.only4.cap4k.ddd.core.domain.repo

/**
 * 仓储管理器配置
 *
 * @author binking338
 * @date 2024/8/25
 */
object RepositorySupervisorSupport {
    lateinit var instance: RepositorySupervisor

    fun configure(repositorySupervisor: RepositorySupervisor) {
        instance = repositorySupervisor
    }
}
