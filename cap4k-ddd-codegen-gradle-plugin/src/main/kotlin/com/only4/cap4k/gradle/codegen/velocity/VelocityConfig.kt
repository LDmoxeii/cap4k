package com.only4.cap4k.gradle.codegen.velocity

/**
 * Velocity 引擎配置类
 *
 * @property encoding 模板文件编码
 * @property strictReferences 是否启用严格引用模式 (true: 未定义变量抛异常, false: 输出 ${var})
 * @property cacheEnabled 是否启用模板缓存
 * @author cap4k-codegen
 * @date 2024/12/21
 */
data class VelocityConfig(
    val encoding: String = "UTF-8",
    val strictReferences: Boolean = false,
    val cacheEnabled: Boolean = true,
) {
    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = VelocityConfig()
    }
}
