/**
 * 排序工具类
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */

@file:JvmName("JpaSortUtils")

package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.share.OrderInfo
import org.springframework.data.domain.Sort


/**
 * 将OrderInfo列表转换为Spring Data的Sort对象
 */
fun toSpringData(orders: Collection<OrderInfo>): Sort {
    if (orders.isEmpty()) {
        return Sort.unsorted()
    }
    return Sort.by(
        orders.map { orderInfo ->
            Sort.Order(
                if (orderInfo.desc) Sort.Direction.DESC else Sort.Direction.ASC,
                orderInfo.field
            )
        }
    )
}

/**
 * 将OrderInfo数组转换为Spring Data的Sort对象
 */
fun toSpringData(vararg orders: OrderInfo): Sort = toSpringData(orders.toList())

