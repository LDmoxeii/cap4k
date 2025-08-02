package com.only4.cap4k.ddd.core.application

/**
 * 请求参数接口
 * 作为所有请求参数类型的基接口
 * 用于定义请求参数的基本结构和约束
 *
 * @param RESULT 请求处理结果的类型，必须是具体类型
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface RequestParam<RESULT: Any>
