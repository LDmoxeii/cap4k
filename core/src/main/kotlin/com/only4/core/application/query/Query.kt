package com.only4.core.application.query

import com.only4.core.application.RequestHandler
import com.only4.core.application.RequestParam

/**
 * 查询接口
 *
 * @author binking338
 * @date
 *
 * @param <PARAM></PARAM> 查询参数
 * @param <RESULT> 查询结果
</RESULT> */
interface Query<RESULT : Any, PARAM : RequestParam<RESULT>> :
    RequestHandler<RESULT, PARAM> {
    override fun exec(request: PARAM): RESULT
}
