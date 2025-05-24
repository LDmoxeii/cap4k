package com.only4.core.application.event.annotation

import kotlin.reflect.KClass

/**
 * 自动发布
 * 领域事件 -> 集成事件
 *
 * @author binking338
 * @date 2024/8/29
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRelease(
    /**
     * 源领域事件类型
     */
    val sourceDomainEventClass: KClass<*> = Void::class,

    /**
     * 延迟发布（秒）
     */
    val delayInSeconds: Int = 0,

    /**
     * 领域事件 -> 集成事件 转换器
     * {@link org.springframework.core.convert.converter.Converter}
     */
    val converterClass: KClass<*> = Void::class
)

/**
 * @author binking338
 * @date 2024/9/11
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoReleases(vararg val value: AutoRelease)
