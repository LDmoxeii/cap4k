package com.only4.cap4k.ddd.core.share

import kotlin.properties.Delegates

/**
 * 分页参数
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
open class PageParam {

    /**
     * 页码
     */
    var pageNum by Delegates.notNull<Int>()

    /**
     * 页大小
     */
    var pageSize by Delegates.notNull<Int>()

    /**
     * 排序
     */
    private var sort: MutableCollection<OrderInfo> = mutableListOf()

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
     * 添加排序字段
     *
     * @param field
     * @return
     */
    fun orderByDesc(field: String): PageParam {
        return orderBy(field, true)
    }

    /**
     * 添加排序字段
     *
     * @param field
     * @return
     */
    fun orderByDesc(field: Any): PageParam {
        return orderBy(field, true)
    }

    /**
     * 添加排序字段
     *
     * @param field
     * @return
     */
    fun orderByAsc(field: String): PageParam {
        return orderBy(field, false)
    }

    /**
     * 添加排序字段
     *
     * @param field
     * @return
     */
    fun orderByAsc(field: Any): PageParam {
        return orderBy(field, false)
    }

    /**
     * 重置排序字段
     *
     * @return
     */
    fun orderReset(): PageParam {
        sort.clear()
        return this
    }

    companion object {
        /**
         * 创建分页参数
         *
         * @param pageNum
         * @param pageSize
         * @param sort
         * @return
         */
        fun of(pageNum: Int, pageSize: Int, sort: MutableList<OrderInfo> = mutableListOf()): PageParam =
            PageParam().apply {
                this.pageNum = pageNum
                this.pageSize = pageSize
                this.sort = sort
            }


        /**
         * 创建分页参数，pageNum=1
         *
         * @param pageSize
         * @param sort
         * @return
         */
        fun limit(pageSize: Int, sort: MutableList<OrderInfo> = mutableListOf()): PageParam {
            return of(1, pageSize, sort)
        }
    }
}
