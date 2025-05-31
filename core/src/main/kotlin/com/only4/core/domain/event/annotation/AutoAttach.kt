package com.only4.core.domain.event.annotation

import com.only4.core.domain.repo.PersistType
import kotlin.reflect.KClass


/**
 * 自动附加领域事件
 * 聚合根持久化变更 -> 领域事件
 *
 * @author binking338
 * @date 2024/8/29
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoAttach(
    /**
     * 持久化变更源实体类型
     *
     * @return
     */
    val sourceEntityClass: KClass<*> = Unit::class,

    /**
     * 持久化变更类型
     *
     * @return
     */
    val persistType: Array<PersistType> = [
        PersistType.CREATE,
        PersistType.UPDATE,
        PersistType.DELETE
    ],

    /**
     * 延迟发布（秒）
     * @return
     */
    val delayInSeconds: Int = 0,

    /**
     * 实体 -> 领域事件 转换器
     * [org.springframework.core.convert.converter.Converter]
     * @return [?][<]
     */
    val converterClass: KClass<*> = Unit::class
)
