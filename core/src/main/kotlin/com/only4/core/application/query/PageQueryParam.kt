package com.only4.core.application.query

import com.only4.core.application.RequestParam
import com.only4.core.share.PageData
import com.only4.core.share.PageParam

/**
 * 分页查询参数
 *
 * @author binking338
 * @date 2024/9/6
 */
abstract class PageQueryParam<RESPONSE_ITEM : Any>(pageNum: Int, pageSize: Int) : PageParam(pageNum, pageSize),
    RequestParam<PageData<RESPONSE_ITEM>>
