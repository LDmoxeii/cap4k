package com.only4.cap4k.ddd.core.domain.event.annotation

/**
 * 领域事件
 *
 * @author LD_moxeii
 * @date 2025/07/20
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
