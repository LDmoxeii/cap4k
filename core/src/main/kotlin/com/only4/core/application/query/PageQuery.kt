package com.only4.core.application.query

import com.only4.core.application.RequestParam
import com.only4.core.share.PageData

/**
 * 分页查询接口
 * 用于处理返回分页数据的查询操作
 *
 * @author binking338
 * @date 2024/9/6
 *
 * @param ITEM 分页数据项类型
 * @param PARAM 查询参数类型，必须实现RequestParam接口
 */
interface PageQuery<ITEM : Any, PARAM : RequestParam<PageData<ITEM>>> :
    Query<PageData<ITEM>, PARAM>
