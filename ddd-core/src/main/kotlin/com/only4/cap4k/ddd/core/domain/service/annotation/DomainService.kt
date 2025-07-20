package com.only4.cap4k.ddd.core.domain.service.annotation

/**
 * 领域服务注解
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainService(
    /**
     * 领域服务名称
     *
     * @return
     */
    val name: String = "",
    /**
     * 领域服务描述
     *
     * @return
     */
    val description: String = ""
)
