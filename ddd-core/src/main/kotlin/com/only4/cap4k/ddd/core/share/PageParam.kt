package com.only4.cap4k.ddd.core.share

/**
 * 分页参数
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
open class PageParam protected constructor(
    /**
     * 页码
     */
    var pageNum: Int = 1,

    /**
     * 页大小
     */
    var pageSize: Int = 10,

    /**
     * 排序
     */
    val sort: MutableList<OrderInfo> = mutableListOf()
) {

    init {
        require(pageNum > 0) { "页码必须大于0" }
        require(pageSize > 0) { "页大小必须大于0" }
    }

    /**
     * 添加排序字段
     *
     * @param field
     * @param desc
     * @return
     */
    fun orderBy(field: String, desc: Boolean): PageParam {
        sort.add(if (desc) OrderInfo.desc(field) else OrderInfo.asc(field))
        return this
    }

    /**
     * 添加排序字段
     *
     * @param field
     * @param desc
     * @return
     */
    fun orderBy(field: Any, desc: Boolean): PageParam {
        sort.add(if (desc) OrderInfo.desc(field.toString()) else OrderInfo.asc(field.toString()))
        return this
    }

    /**
     * 降序排序
     */
    fun orderByDesc(field: String): PageParam = orderBy(field, true)

    /**
     * 降序排序
     */
    fun orderByDesc(field: Any): PageParam = orderBy(field, true)

    /**
     * 升序排序
     */
    fun orderByAsc(field: String): PageParam = orderBy(field, false)

    /**
     * 升序排序
     */
    fun orderByAsc(field: Any): PageParam = orderBy(field, false)

    /**
     * 重置排序字段
     */
    fun orderReset(): PageParam {
        sort.clear()
        return this
    }

    /**
     * 设置页码
     */
    fun page(pageNum: Int): PageParam {
        require(pageNum > 0) { "页码必须大于0" }
        this.pageNum = pageNum
        return this
    }

    /**
     * 设置页大小
     */
    fun size(pageSize: Int): PageParam {
        require(pageSize > 0) { "页大小必须大于0" }
        this.pageSize = pageSize
        return this
    }

    companion object {
        /**
         * 创建分页参数
         */
        @JvmStatic
        fun of(pageNum: Int, pageSize: Int, sort: MutableList<OrderInfo> = mutableListOf()): PageParam =
            PageParam(pageNum, pageSize, sort)

        /**
         * 创建分页参数，pageNum=1
         */
        @JvmStatic
        fun limit(pageSize: Int, sort: MutableList<OrderInfo> = mutableListOf()): PageParam =
            PageParam(1, pageSize, sort)
    }
}
