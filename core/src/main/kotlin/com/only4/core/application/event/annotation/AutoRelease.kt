package com.only4.core.application.event.annotation

import kotlin.reflect.KClass

/**
 * 自动发布注解
 * 用于将领域事件自动转换为集成事件并发布
 *
 * @author binking338
 * @date 2024/8/29
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRelease(
    /**
     * 源领域事件类型
     * 指定要监听的领域事件类型
     */
    val sourceDomainEventClass: KClass<*> = Unit::class,

    /**
     * 延迟发布时间（秒）
     * 指定事件发布前的延迟时间，默认为0表示立即发布
     */
    val delayInSeconds: Long = 0,

    /**
     * 领域事件到集成事件的转换器
     * 实现org.springframework.core.convert.converter.Converter接口
     * 用于将领域事件转换为集成事件
     */
    val converterClass: KClass<*> = Unit::class
)

/**
 * 自动发布注解集合
 * 用于在同一个类上配置多个自动发布规则
 *
 * @author binking338
 * @date 2024/9/11
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoReleases(vararg val value: AutoRelease)
