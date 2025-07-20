package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 值对象
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface ValueObject<ID> {
    /**
     * 值对象哈希码
     *
     * @return
     */
    fun hash(): ID
}
