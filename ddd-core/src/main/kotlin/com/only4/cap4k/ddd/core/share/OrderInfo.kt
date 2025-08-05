package com.only4.cap4k.ddd.core.share

/**
 * 排序定义
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
open class OrderInfo protected constructor(
    val field: String,
    val desc: Boolean = false
) {
    companion object {
        /**
         * 升序
         */
        fun asc(field: String): OrderInfo = OrderInfo(field, false)

        /**
         * 升序
         */
        fun asc(field: Any): OrderInfo = asc(field.toString())

        /**
         * 降序
         */
        fun desc(field: String): OrderInfo = OrderInfo(field, true)

        /**
         * 降序
         */
        fun desc(field: Any): OrderInfo = desc(field.toString())

        /**
         * 构建器
         */
        fun builder(): OrderInfosBuilder = OrderInfosBuilder()
    }

    class OrderInfosBuilder {
        private val orderInfos = mutableListOf<OrderInfo>()

        fun asc(field: String): OrderInfosBuilder = apply {
            orderInfos.add(OrderInfo.asc(field))
        }

        fun asc(field: Any): OrderInfosBuilder = apply {
            orderInfos.add(OrderInfo.asc(field))
        }

        fun desc(field: String): OrderInfosBuilder = apply {
            orderInfos.add(OrderInfo.desc(field))
        }

        fun desc(field: Any): OrderInfosBuilder = apply {
            orderInfos.add(OrderInfo.desc(field))
        }

        fun build(): List<OrderInfo> = orderInfos.toList()
    }
}
