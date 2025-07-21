package com.only4.cap4k.ddd.core.share

import kotlin.properties.Delegates

/**
 * 排序定义
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
class OrderInfo {

    /**
     * 排序字段
     */
    lateinit var field: String

    /**
     * 是否降序
     */
    var desc by Delegates.notNull<Boolean>()

    class OrderInfosBuilder {
        private val orderInfos: MutableList<OrderInfo> = ArrayList()

        fun asc(field: String): OrderInfosBuilder {
            orderInfos.add(Companion.asc(field))
            return this
        }

        fun desc(field: String): OrderInfosBuilder {
            orderInfos.add(Companion.desc(field))
            return this
        }

        fun asc(field: Any): OrderInfosBuilder {
            orderInfos.add(Companion.desc(field))
            return this
        }

        fun desc(field: Any): OrderInfosBuilder {
            orderInfos.add(Companion.desc(field))
            return this
        }

        fun build(): Collection<OrderInfo> {
            return orderInfos
        }
    }

    companion object {
        /**
         * 降序
         *
         * @param field
         * @return
         */
        fun desc(field: Any): OrderInfo {
            return desc(field.toString())
        }

        /**
         * 降序
         *
         * @param field
         * @return
         */
        fun desc(field: String): OrderInfo {
            val orderInfo = OrderInfo()
            orderInfo.field = field
            orderInfo.desc = true
            return orderInfo
        }


        /**
         * 升序
         *
         * @param field
         * @return
         */
        fun asc(field: Any): OrderInfo {
            return asc(field.toString())
        }

        /**
         * 升序
         *
         * @param field
         * @return
         */
        fun asc(field: String): OrderInfo {
            val orderInfo = OrderInfo()
            orderInfo.field = field
            orderInfo.desc = false
            return orderInfo
        }
    }

    fun sortBuilder(): OrderInfosBuilder {
        return OrderInfosBuilder()
    }
}
