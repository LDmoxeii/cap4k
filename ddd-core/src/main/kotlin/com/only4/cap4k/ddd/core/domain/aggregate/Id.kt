package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 实体ID
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
interface Id<AGGREGATE: Any, KEY: Any> {
    /**
     * 获取实体Key
     *
     * @return
     */
    val value: KEY

    open class Default<AGGREGATE: Any, KEY: Any>(protected val key: KEY) : Id<AGGREGATE, KEY> {

        override fun toString(): String = key.toString()

        override fun hashCode(): Int = key.hashCode()

        override val value: KEY
            get() = key


        override fun equals(other: Any?): Boolean {
            if (other !is Default<*, *> || other.javaClass != this.javaClass) {
                return false
            }
            return key == other.key
        }
    }
}
