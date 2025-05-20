package com.only4.core.application.command

import com.only4.core.application.RequestHandler
import com.only4.core.application.RequestParam

/**
 * 命令接口
 *
 * @param <PARAM></PARAM>
 * @param <RESULT>
 * @author binking338
 * @date
</RESULT> */
interface Command<RESULT, PARAM : RequestParam<RESULT>> :
    RequestHandler<RESULT, PARAM> {
    override fun exec(request: PARAM): RESULT
}
