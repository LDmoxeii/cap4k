package com.only4.cap4k.ddd.core.application.query

import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.share.PageData

/**
 * 分页查询接口
 * 用于处理返回分页数据的查询操作
 *
 * @author LD_moxeii
 * @date 2025/07/20
 *
 * @param ITEM 分页数据项类型
 * @param PARAM 查询参数类型，必须实现RequestParam接口
 */
interface PageQuery<PARAM : RequestParam<PageData<ITEM>>, ITEM> :
    Query<PARAM, PageData<ITEM>>
