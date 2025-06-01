package com.only4.cap4k.ddd.core.application.query

import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam

/**
 * 分页查询参数抽象类
 * 用于定义分页查询操作的参数，继承自PageParam
 * 支持自定义查询条件和分页信息
 *
 * @author binking338
 * @date 2024/9/6
 *
 * @param RESPONSE_ITEM 响应数据项类型，表示分页结果中的单个数据项
 * @property pageNum 页码，从1开始计数
 * @property pageSize 每页大小，表示每页包含的数据项数量
 */
abstract class PageQueryParam<RESPONSE_ITEM : Any>(
    pageNum: Int,
    pageSize: Int
) : PageParam(pageNum, pageSize),
    RequestParam<PageData<RESPONSE_ITEM>>
