package com.only4.cap4k.ddd.core.application.query

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * 查询接口
 * 定义查询操作的基本契约，用于处理查询请求并返回结果
 * 作为所有查询操作的基接口，提供统一的查询执行方法
 *
 * @author LD_moxeii
 * @date 2025/07/27
 *
 * @param RESULT 查询结果类型，表示查询操作返回的数据类型
 * @param PARAM 查询参数类型，必须实现RequestParam接口，用于定义查询条件
 */
interface Query<PARAM : RequestParam<RESULT>, RESULT : Any> :
    RequestHandler<PARAM, RESULT> {
    /**
     * 执行查询操作
     * 根据提供的查询参数执行查询并返回结果
     *
     * @param request 查询参数，包含查询所需的所有条件
     * @return 查询结果，类型由泛型参数RESULT指定
     */
    override fun exec(request: PARAM): RESULT
}
