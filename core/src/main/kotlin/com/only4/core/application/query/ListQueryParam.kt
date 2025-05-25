package com.only4.core.application.query

import com.only4.core.application.RequestParam

/**
 * 列表查询参数
 *
 * @author binking338
 * @date 2024/9/6
 */
interface ListQueryParam<RESPONSE_ITEM : Any> :
    RequestParam<List<RESPONSE_ITEM>>
