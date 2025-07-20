package com.only4.cap4k.ddd.core.application.command

import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * 无返回结果的命令参数基类
 * @author LD_moxeii
 * @date 2025/07/20
 */
abstract class NoneResultCommandParam<PARAM : RequestParam<Unit>> : Command<PARAM, Unit> {
    abstract override fun exec(request: PARAM)
}
