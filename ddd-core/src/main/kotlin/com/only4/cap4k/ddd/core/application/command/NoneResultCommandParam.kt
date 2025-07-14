package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * 无返回结果的命令参数基类
 * 用于处理不需要返回结果的命令操作，执行结果类型为Unit
 *
 * @param PARAM 命令参数类型，必须实现RequestParam接口且结果类型为Unit
 * @author binking338
 * @date 2024/12/29
 */
abstract class NoneResultCommandParam<PARAM : RequestParam<Unit>> : Command<PARAM, Unit> {
    /**
     * 执行无返回结果的命令
     *
     * @param request 命令参数
     */
    abstract override fun exec(request: PARAM)
}
