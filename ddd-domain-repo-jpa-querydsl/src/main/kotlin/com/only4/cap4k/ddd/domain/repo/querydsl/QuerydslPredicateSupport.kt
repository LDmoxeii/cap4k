package com.only4.cap4k.ddd.domain.repo.querydsl

import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.domain.repo.toSpringData
import com.querydsl.core.types.Predicate
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.querydsl.QPageRequest
import org.springframework.data.querydsl.QSort
import com.only4.cap4k.ddd.core.domain.repo.Predicate as DomainPredicate

/**
 * Querydsl仓储检索断言Support
 *
 * @author binking338
 * @date 2025/3/29
 */
object QuerydslPredicateSupport {

    fun <ENTITY : Any> resumeSort(
        predicate: DomainPredicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList()
    ): Sort {
        val querydslPredicate = predicate as? QuerydslPredicate<ENTITY>
            ?: throw DomainException("Unsupported predicate type: ${predicate::class.java.name}")
        return when {
            querydslPredicate.orderSpecifiers.isNotEmpty() -> QSort(querydslPredicate.orderSpecifiers)
            else -> toSpringData(orders)
        }
    }

    fun <ENTITY : Any> resumePageable(predicate: DomainPredicate<ENTITY>, pageParam: PageParam): Pageable {
        val querydslPredicate = predicate as? QuerydslPredicate<ENTITY>
            ?: throw DomainException("Unsupported predicate type: ${predicate::class.java.name}")
        return when {
            querydslPredicate.orderSpecifiers.isNotEmpty() ->
                QPageRequest.of(pageParam.pageNum, pageParam.pageSize, QSort(querydslPredicate.orderSpecifiers))

            else -> toSpringData(pageParam)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <ENTITY : Any> resumePredicate(predicate: DomainPredicate<ENTITY>): Predicate {
        val querydslPredicate = predicate as? QuerydslPredicate<ENTITY>
            ?: throw DomainException("Unsupported predicate type: ${predicate::class.java.name}")
        return querydslPredicate.predicate
    }

    /**
     * 获取断言实体类型
     */
    @Suppress("UNCHECKED_CAST")
    fun <ENTITY : Any> reflectEntityClass(predicate: DomainPredicate<ENTITY>): Class<ENTITY> {
        val querydslPredicate = predicate as? QuerydslPredicate<ENTITY>
            ?: throw DomainException("Unsupported predicate type: ${predicate::class.java.name}")
        return querydslPredicate.entityClass
    }
}
