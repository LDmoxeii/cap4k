package com.only4.cap4k.ddd.core.domain.event

/**
 * 事件拦截器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface EventInterceptor {
    /**
     * 持久化前
     *
     * @param event
     */
    fun prePersist(event: EventRecord)

    /**
     * 持久化后
     *
     * @param event
     */
    fun postPersist(event: EventRecord)

    /**
     * 发布前
     *
     * @param event
     */
    fun preRelease(event: EventRecord)

    /**
     * 发布后
     *
     * @param event
     */
    fun postRelease(event: EventRecord)

    /**
     * 发布异常
     *
     * @param throwable
     * @param event
     */
    fun onException(throwable: Throwable, event: EventRecord)
}
