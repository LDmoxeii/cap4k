package com.only4.core.application.command

import com.only4.core.application.RequestParam

/**
 * 无返回命令
 *
 * @author binking338
 * @date
 */
abstract class NoneResultCommandParam<PARAM : RequestParam<Void>> :
    Command<Void, PARAM> {
    abstract override fun exec(request: PARAM): Void
}
