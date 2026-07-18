package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.repo.Predicate
import org.springframework.data.jpa.domain.Specification

/**
 * Jpa仓储检索断言
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
class JpaPredicate<ENTITY : Any>(
    val entityClass: Class<ENTITY>,
    val spec: Specification<ENTITY>? = null,
    val ids: Iterable<Any>? = null
) : Predicate<ENTITY> {

    companion object {
        @JvmStatic
        fun <ENTITY : Any> byId(entityClass: Class<ENTITY>, id: Any): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = listOf(id))

        @JvmStatic
        fun <ENTITY : Any> byIds(entityClass: Class<ENTITY>, ids: Iterable<Any>): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = ids)

        @JvmStatic
        fun <ENTITY : Any> bySpecification(
            entityClass: Class<ENTITY>,
            specification: Specification<ENTITY>
        ): JpaPredicate<ENTITY> = JpaPredicate(entityClass, specification)
    }
}
