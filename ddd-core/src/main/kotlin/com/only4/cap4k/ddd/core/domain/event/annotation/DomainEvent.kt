package com.only4.cap4k.ddd.core.domain.event.annotation

/**
 * 领域事件
 *
 * @author binking338
 * @date
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainEvent(
    /**
     * 领域事件名称
     * @return
     */
    val value: String = "",
    /**
     * 事件记录是否持久化
     * @return
     */
    val persist: Boolean = false
)
