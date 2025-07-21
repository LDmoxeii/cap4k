package com.only4.cap4k.ddd.core.share.annotation

/**
 * 重试注解
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Retry(
    /**
     * 重试次数
     * 只有集成事件重试次数才有意义
     *
     * @return
     */
    val retryTimes: Int = 15,
    /**
     * 重试时间间隔，单位分钟
     * @return
     */
    vararg val retryIntervals: Int = [],
    /**
     * 过期时长，单位分钟，默认一天
     * @return
     */
    val expireAfter: Int = 1440
)
