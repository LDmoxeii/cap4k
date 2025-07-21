package com.only4.cap4k.ddd.core.share

import kotlin.properties.Delegates


/**
 * 请使用 PageData.create 静态方法创建实例
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
open class PageData<T> protected constructor() {

    /**
     * 页码
     */
    private var pageNum by Delegates.notNull<Int>()

    /**
     * 页大小
     */
    private var pageSize by Delegates.notNull<Int>()

    /**
     * 总记录数
     */
    private var totalCount by Delegates.notNull<Long>()

    /**
     * 记录列表
     */
    private lateinit var list: List<T>

    /**
     * 转换分页结果类型
     *
     * @param <D>
     * @return
    </D> */
    fun <D> transform(map: (T) -> D): PageData<D> = create(pageSize, pageNum, totalCount, list.map(map))


    companion object {
        /**
         * 生成空分页返回
         *
         * @param pageSize
         * @param clazz
         * @param <T>
         * @return
        </T> */
        fun <T> empty(pageSize: Int, clazz: Class<T>): PageData<T> =
            create(pageSize, 1, 0L, mutableListOf())

        /**
         * 新建分页结果
         *
         * @param pageParam
         * @param list
         * @param <T>
         * @return
        </T> */
        fun <T> create(pageParam: PageParam, totalCount: Long, list: List<T>): PageData<T> {
            val pageData = PageData<T>()
            pageData.pageSize = pageParam.pageSize
            pageData.pageNum = pageParam.pageNum
            pageData.totalCount = totalCount
            pageData.list = list
            return pageData
        }

        /**
         * 新建分页结果
         *
         * @param pageSize
         * @param pageNum
         * @param list
         * @param <T>
         * @return
        </T> */
        fun <T> create(pageSize: Int, pageNum: Int, totalCount: Long, list: List<T>): PageData<T> {
            val pageData = PageData<T>()
            pageData.pageSize = pageSize
            pageData.pageNum = pageNum
            pageData.totalCount = totalCount
            pageData.list = list
            return pageData
        }
    }
}
