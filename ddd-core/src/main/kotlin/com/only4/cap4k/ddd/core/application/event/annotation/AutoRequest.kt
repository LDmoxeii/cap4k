package com.only4.cap4k.ddd.core.application.event.annotation

import kotlin.reflect.KClass

/**
 * 自动触发请求注解
 * 用于将事件（领域事件或集成事件）自动转换为命令并执行
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRequest(
    /**
     * 目标请求类型
     * 指定要触发的命令类型
     */
    val targetRequestClass: KClass<*> = Unit::class,

    /**
     * 事件到请求的转换器
     * 实现org.springframework.core.convert.converter.Converter接口
     * 用于将事件转换为命令
     */
    val converterClass: KClass<*> = Unit::class
)

/**
 * 自动触发请求注解集合
 * 用于在同一个类上配置多个自动触发规则
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRequests(vararg val value: AutoRequest)
