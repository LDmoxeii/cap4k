package com.only4.core.application.query

import com.only4.core.application.RequestParam

/**
 * 列表查询接口
 *
 * @author binking338
 * @date
 */
interface ListQuery<out ITEM : Any, in PARAM : RequestParam<List<@UnsafeVariance ITEM>>> :
    Query<List<ITEM>, PARAM>
