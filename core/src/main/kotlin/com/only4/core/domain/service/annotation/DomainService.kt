package com.only4.core.domain.service.annotation

/**
 * 领域服务注解
 *
 * @author binking338
 * @date 2024/9/3
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
