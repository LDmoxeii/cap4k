package com.only4.cap4k.ddd.core.application.query

import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * 列表查询参数接口
 * 用于定义列表查询操作的参数
 *
 * @author LD_moxeii
 * @date 2025/07/20
 *
 * @param RESPONSE_ITEM 响应列表项类型
 */
interface ListQueryParam<RESPONSE_ITEM> :
    RequestParam<List<RESPONSE_ITEM>>
