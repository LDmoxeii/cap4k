package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface Command<in PARAM : RequestParam<out RESULT>, out RESULT: Any> : RequestHandler<PARAM, RESULT> {
    override fun exec(request: PARAM): RESULT
}
