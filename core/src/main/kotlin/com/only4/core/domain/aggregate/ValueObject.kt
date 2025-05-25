package com.only4.core.domain.aggregate

/**
 * 值对象
 *
 * @author binking338
 * @date 2024/9/18
 */
interface ValueObject<out ID> {
    /**
     * 值对象哈希码
     *
     * @return
     */
    fun hash(): ID
}
