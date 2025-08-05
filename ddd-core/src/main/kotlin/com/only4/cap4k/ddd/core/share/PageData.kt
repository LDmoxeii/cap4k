package com.only4.cap4k.ddd.core.share


/**
 * 请使用 PageData.create 静态方法创建实例
 *
 * @author LD_moxeii
 * @date 2025/07/21
 */
open class PageData<T : Any> protected constructor(
    /**
     * 页码
     */
    val pageNum: Int,

    /**
     * 页大小
     */
    val pageSize: Int,

    /**
     * 总记录数
     */
    val totalCount: Long,

    /**
     * 记录列表
     */
    val list: List<T>
) {

    /**
     * 总页数
     */
    val totalPages: Int get() = if (totalCount == 0L) 0 else ((totalCount + pageSize - 1) / pageSize).toInt()

    /**
     * 是否为空
     */
    val isEmpty: Boolean get() = list.isEmpty()

    /**
     * 转换分页结果类型
     */
    fun <D : Any> transform(map: (T) -> D): PageData<D> =
        PageData(pageSize, pageNum, totalCount, list.map(map))

    companion object {
        /**
         * 生成空分页返回
         */
        @JvmStatic
        fun <T : Any> empty(pageSize: Int = 10, pageNum: Int = 1): PageData<T> =
            PageData(pageNum, pageSize, 0L, emptyList())

        /**
         * 新建分页结果
         */
        @JvmStatic
        fun <T : Any> create(pageParam: PageParam, totalCount: Long, list: List<T>): PageData<T> =
            PageData(pageParam.pageNum, pageParam.pageSize, totalCount, list)

        /**
         * 新建分页结果
         */
        @JvmStatic
        fun <T : Any> create(pageSize: Int, pageNum: Int, totalCount: Long, list: List<T>): PageData<T> =
            PageData(pageSize, pageNum, totalCount, list)
    }
}
