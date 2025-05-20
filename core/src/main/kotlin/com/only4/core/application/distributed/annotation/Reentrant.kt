package com.only4.core.application.distributed.annotation

/**
 * 可重入锁
 *
 * @author binking338
 * @date 2025/5/14
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(
    AnnotationRetention.RUNTIME
)
annotation class Reentrant(
    /**
     * 是否可重入
     * @return
     */
    val value: Boolean = false,
    /**
     * 唯一识别码，默认使用方法签名
     * @return
     */
    val key: String = "",
    /**
     * 是否分布式
     * @return
     */
    val distributed: Boolean = false,
    /**
     * 锁过期时间
     * 支持 ms, s, m, h, d 为单位（不区分大小写）
     * 支持 [java.time.Duration].parse
     *
     * @return
     */
    val expire: String = "6h"
)
