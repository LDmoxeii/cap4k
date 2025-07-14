package com.only4.cap4k.ddd.core.application.query

import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * 列表查询接口
 * 用于处理返回列表类型结果的查询操作，支持自定义查询参数
 *
 * @author binking338
 * @date 2024/9/6
 *
 * @param ITEM 列表项类型，表示查询结果中的单个数据项
 * @param PARAM 查询参数类型，必须实现RequestParam接口，用于定义查询条件
 */
interface ListQuery<PARAM : RequestParam<List<ITEM>>, ITEM> :
    Query<List<ITEM>, PARAM>
