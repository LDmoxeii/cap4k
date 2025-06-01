package com.only4.cap4k.ddd.core.share


/**
 * 请使用 PageData.create 静态方法创建实例
 *
 * @author binking338
 * @date
 */
open class PageData<T : Any> protected constructor(
    /**
     * 页码
     */
    private var pageNum: Int,

    /**
     * 页大小
     */
    private var pageSize: Int,

    /**
     * 总记录数
     */
    private var totalCount: Long,

    /**
     * 记录列表
     */
    private var list: List<T>,
) {

    /**
     * 转换分页结果类型
     *
     * @param <D>
     * @return
    </D> */
    fun <D : Any> transform(map: (T) -> D): PageData<D> = create(pageSize, pageNum, totalCount, list.map(map))


    companion object {
        /**
         * 生成空分页返回
         *
         * @param pageSize
         * @param clazz
         * @param <T>
         * @return
        </T> */
        fun <T : Any> empty(pageSize: Int, clazz: Class<T>): PageData<T> =
            create(pageSize, 1, 0L, mutableListOf())

        /**
         * 新建分页结果
         *
         * @param pageParam
         * @param list
         * @param <T>
         * @return
        </T> */
        fun <T : Any> create(pageParam: PageParam, totalCount: Long, list: List<T>): PageData<T> =
            PageData(pageParam.pageSize, pageParam.pageNum, totalCount, list)

        /**
         * 新建分页结果
         *
         * @param pageSize
         * @param pageNum
         * @param list
         * @param <T>
         * @return
        </T> */
        fun <T : Any> create(pageSize: Int, pageNum: Int, totalCount: Long, list: List<T>): PageData<T> =
            PageData(pageSize, pageNum, totalCount, list)
    }
}
