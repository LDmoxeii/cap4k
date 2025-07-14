package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 值对象
 *
 * @author binking338
 * @date 2024/9/18
 */
interface ValueObject<ID> {
    /**
     * 值对象哈希码
     *
     * @return
     */
    fun hash(): ID
}
