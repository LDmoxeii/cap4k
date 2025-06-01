package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 实体ID
 *
 * @author binking338
 * @date 2025/4/8
 */
interface Id<AGGREGATE : Any, KEY : Any> {
    /**
     * 获取实体Key
     *
     * @return
     */
    val value: KEY

    class Default<AGGREGATE : Any, KEY : Any>(protected val key: KEY) : Id<AGGREGATE, KEY> {

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
