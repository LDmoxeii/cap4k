package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * 命令接口
 * 用于处理应用程序中的命令操作，每个命令都对应一个具体的业务操作
 *
 * @param RESULT 命令执行的结果类型
 * @param PARAM 命令参数类型，必须实现RequestParam接口
 * @author binking338
 * @date 2024/12/29
 */
interface Command<RESULT : Any, PARAM : RequestParam<RESULT>> : RequestHandler<RESULT, PARAM> {
    /**
     * 执行命令
     *
     * @param request 命令参数
     * @return 命令执行结果
     */
    override fun exec(request: PARAM): RESULT
}
