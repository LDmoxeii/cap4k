package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.slf4j.LoggerFactory
import java.util.*

/**
 * JPA查询帮助类
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
object JpaQueryUtils {

    private val log = LoggerFactory.getLogger(JpaQueryUtils::class.java)

    fun interface QueryBuilder<R, F> {
        fun build(cb: CriteriaBuilder, cq: CriteriaQuery<R>, root: Root<F>)
    }

    private var jpaUnitOfWork: JpaUnitOfWork? = null
    private var retrieveCountWarnThreshold: Int = 0

    fun configure(jpaUnitOfWork: JpaUnitOfWork, retrieveCountWarnThreshold: Int) {
        this.jpaUnitOfWork = jpaUnitOfWork
        this.retrieveCountWarnThreshold = retrieveCountWarnThreshold
    }

    private val entityManager: EntityManager
        get() = jpaUnitOfWork!!.entityManager

    /**
     * 自定义查询
     * 期待返回一条记录，数据异常返回0条或多条记录将抛出异常
     */
    fun <R, F> queryOne(
        resultClass: Class<R>,
        fromEntityClass: Class<F>,
        queryBuilder: QueryBuilder<R, F>
    ): R {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(resultClass)
        val root = criteriaQuery.from(fromEntityClass)
        queryBuilder.build(criteriaBuilder, criteriaQuery, root)
        return entityManager.createQuery(criteriaQuery).singleResult
    }

    /**
     * 自定义查询
     * 返回0条或多条记录
     */
    fun <R, F> queryList(
        resultClass: Class<R>,
        fromEntityClass: Class<F>,
        queryBuilder: QueryBuilder<R, F>
    ): List<R> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(resultClass)
        val root = criteriaQuery.from(fromEntityClass)
        queryBuilder.build(criteriaBuilder, criteriaQuery, root)
        val results = entityManager.createQuery(criteriaQuery).resultList
        if (results.size > retrieveCountWarnThreshold) {
            log.warn("查询记录数过多: retrieve_count=${results.size}")
        }
        return results
    }

    /**
     * 自定义查询
     * 如果存在符合筛选条件的记录，返回第一条记录
     */
    fun <R, F> queryFirst(
        resultClass: Class<R>,
        fromEntityClass: Class<F>,
        queryBuilder: QueryBuilder<R, F>
    ): Optional<R> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(resultClass)
        val root = criteriaQuery.from(fromEntityClass)
        queryBuilder.build(criteriaBuilder, criteriaQuery, root)
        val results = entityManager.createQuery(criteriaQuery)
            .setFirstResult(0)
            .setMaxResults(1)
            .resultList
        return results.stream().findFirst()
    }

    /**
     * 自定义查询
     * 获取分页列表
     */
    fun <R, F> queryPage(
        resultClass: Class<R>,
        fromEntityClass: Class<F>,
        queryBuilder: QueryBuilder<R, F>,
        pageIndex: Int,
        pageSize: Int
    ): List<R> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(resultClass)
        val root = criteriaQuery.from(fromEntityClass)
        queryBuilder.build(criteriaBuilder, criteriaQuery, root)
        return entityManager.createQuery(criteriaQuery)
            .setFirstResult(pageSize * pageIndex)
            .setMaxResults(pageSize)
            .resultList
    }

    /**
     * 自定义查询
     * 返回查询计数
     */
    fun <F> count(
        fromEntityClass: Class<F>,
        queryBuilder: QueryBuilder<Long, F>
    ): Long {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val root = criteriaQuery.from(fromEntityClass)
        queryBuilder.build(criteriaBuilder, criteriaQuery, root)
        return entityManager.createQuery(criteriaQuery).singleResult
    }
}
