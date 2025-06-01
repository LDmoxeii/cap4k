package com.only4.cap4k.ddd.core.application.distributed.annotation

/**
 * 可重入锁注解
 * 用于标记需要加锁的方法或属性，支持分布式锁和本地锁
 *
 * @author binking338
 * @date 2025/5/14
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class Reentrant(
    /**
     * 是否可重入
     * 默认为false，表示不可重入
     */
    val value: Boolean = false,

    /**
     * 锁的唯一标识
     * 默认为空字符串，表示使用方法签名作为标识
     * 建议在分布式场景下使用业务相关的唯一标识
     */
    val key: String = "",

    /**
     * 是否使用分布式锁
     * 默认为false，表示使用本地锁
     * 设置为true时，将使用分布式锁实现
     */
    val distributed: Boolean = false,

    /**
     * 锁的过期时间
     * 支持的时间单位：
     * - ms: 毫秒
     * - s: 秒
     * - m: 分钟
     * - h: 小时
     * - d: 天
     * 不区分大小写
     * 支持java.time.Duration.parse格式
     * 默认值为"6h"
     */
    val expire: String = "6h"
)
