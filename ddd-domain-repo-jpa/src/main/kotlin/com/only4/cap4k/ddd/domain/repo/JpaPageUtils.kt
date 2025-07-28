/**
 * 分页工具类
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
@file:JvmName("JpaPageUtils")

package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import org.springframework.beans.BeanUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * 从JPA转换
 */
fun <T : Any> fromSpringData(page: Page<T>?): PageData<T> {
    if (page == null) {
        return PageData.empty(10)
    }
    return PageData.create(
        page.pageable.pageSize,
        page.pageable.pageNumber + 1,
        page.totalElements,
        page.content
    )
}

/**
 * 从JPA转换
 */
fun <S : Any, D : Any> fromSpringData(page: Page<S>, desClass: Class<D>): PageData<D> {
    return fromSpringData(page).transform { s ->
        try {
            @Suppress("DEPRECATION")
            val d = desClass.newInstance()
            BeanUtils.copyProperties(s, d)
            d
        } catch (throwable: Throwable) {
            throw DomainException("分页类型转换异常", throwable)
        }
    }
}

/**
 * 从JPA转换
 */
fun <S : Any, D : Any> fromSpringData(page: Page<S>, transformer: (S) -> D): PageData<D> {
    return fromSpringData(page).transform(transformer)
}

/**
 * 转换为Spring Data的Pageable
 */
fun toSpringData(param: PageParam?): Pageable {
    if (param == null) {
        return PageRequest.of(0, 10)
    }

    return if (param.sort.isEmpty()) {
        PageRequest.of(param.pageNum - 1, param.pageSize)
    } else {
        val orders = toSpringData(param.sort)
        PageRequest.of(param.pageNum - 1, param.pageSize, orders)
    }
}
