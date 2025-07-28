package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.repo.Predicate
import org.springframework.data.jpa.domain.Specification

/**
 * Jpa仓储检索断言Support
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
object JpaPredicateSupport {

    /**
     * 复原ID
     */
    @Suppress("UNCHECKED_CAST")
    fun <ENTITY : Any, ID> resumeId(predicate: Predicate<ENTITY>): ID? {
        if (predicate !is JpaPredicate) {
            return null
        }
        val ids = predicate.ids ?: return null
        val iterator = ids.iterator()
        return if (iterator.hasNext()) iterator.next() as ID else null
    }

    /**
     * 复原IDS
     */
    @Suppress("UNCHECKED_CAST")
    fun <ENTITY : Any, ID> resumeIds(predicate: Predicate<ENTITY>): Iterable<ID>? {
        if (predicate !is JpaPredicate) {
            return null
        }
        return predicate.ids as? Iterable<ID>
    }

    /**
     * 复原Specification
     */
    fun <ENTITY : Any> resumeSpecification(predicate: Predicate<ENTITY>): Specification<ENTITY>? {
        if (predicate !is JpaPredicate) {
            return null
        }
        return predicate.spec
    }

    /**
     * 获取断言实体类型
     */
    @Suppress("UNCHECKED_CAST")
    fun <ENTITY : Any> reflectEntityClass(predicate: Predicate<ENTITY>): Class<ENTITY>? {
        if (predicate !is JpaPredicate) {
            return null
        }
        return predicate.entityClass
    }
}
