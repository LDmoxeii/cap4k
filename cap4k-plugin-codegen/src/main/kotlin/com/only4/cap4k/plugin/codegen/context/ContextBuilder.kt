package com.only4.cap4k.plugin.codegen.context

interface ContextBuilder<T> {
    /**
     * 构建顺序（数字越小越先执行）
     */
    val order: Int

    /**
     * 构建上下文信息
     */
    fun build(context: T)
}
