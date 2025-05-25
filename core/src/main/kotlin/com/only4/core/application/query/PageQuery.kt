package com.only4.core.application.query

import com.only4.core.application.RequestParam
import com.only4.core.share.PageData

/**
 * 分页查询
 *
 * @author binking338
 * @date
 */
interface PageQuery<ITEM : Any, in PARAM : RequestParam<PageData<ITEM>>> :
    Query<PageData<ITEM>, PARAM>
