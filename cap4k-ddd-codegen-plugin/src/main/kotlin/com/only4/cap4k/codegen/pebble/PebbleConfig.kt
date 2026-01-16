package com.only4.cap4k.codegen.pebble

data class PebbleConfig(
    val classpathPrefix: String = "templates",         // 内置模板路径
    val encoding: String = "UTF-8",
    val cacheEnabled: Boolean = false,
    val strictMode: Boolean = false,
    val autoEscaping: Boolean = false,                  // 关闭HTML自动转义,用于生成源代码
    val newLineTrimming: Boolean = false                // 禁用换行符删除,保留模板中的所有换行
) {
    companion object {
        val DEFAULT = PebbleConfig()
    }
}
