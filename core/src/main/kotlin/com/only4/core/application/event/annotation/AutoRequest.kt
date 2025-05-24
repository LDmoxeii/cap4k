package com.only4.core.application.event.annotation

import kotlin.reflect.KClass

/**
 * 自动触发请求
 * 事件（领域\集成）-> 命令
 *
 * @author binking338
 * @date 2024/9/1
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRequest(
    /**
     * 目标请求
     *
     * @return
     */
    val targetRequestClass: KClass<*> = Void::class,
    /**
     * 事件 -> 请求 转换器
     * [org.springframework.core.convert.converter.Converter]
     *
     * @return [?][<]
     */
    val converterClass: KClass<*> = Void::class
)

/**
 * @author binking338
 * @date 2024/9/11
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRequests(vararg val value: AutoRequest)
