package com.only4.core.domain.aggregate

/**
 * 实体ID
 *
 * @author binking338
 * @date 2025/4/8
 */
interface Id<AGGREGATE, KEY> {
    /**
     * 获取实体Key
     *
     * @return
     */
    val value: KEY

    class Default<AGGREGATE, KEY>(protected val key: KEY) : Id<AGGREGATE, KEY> {

        override fun toString(): String = key.toString()

        override fun hashCode(): Int = key?.hashCode() ?: 0

        override val value: KEY
            get() = key


        override fun equals(obj: Any?): Boolean {
            if (obj !is Default<*, *> || obj.javaClass != this.javaClass) {
                return false
            }
            return key == (obj as Default<AGGREGATE, KEY>).key
        }
    }
}
