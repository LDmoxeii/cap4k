package com.only4.cap4k.gradle.codegen.velocity

import org.apache.velocity.VelocityContext

/**
 * Velocity 上下文构建器
 *
 * 职责:
 * 1. 将 Map<String, Any?> 转换为 VelocityContext
 * 2. 支持复杂对象类型(List, Map, POJO)
 * 3. 提供工具类注入
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
class VelocityContextBuilder {

    private val context = VelocityContext()

    /**
     * 批量添加变量
     */
    fun putAll(variables: Map<String, Any?>): VelocityContextBuilder {
        variables.forEach { (key, value) ->
            put(key, value)
        }
        return this
    }

    /**
     * 添加单个变量
     */
    fun put(key: String, value: Any?): VelocityContextBuilder {
        when (value) {
            null -> context.put(key, "")
            is String -> context.put(key, value)
            is Number -> context.put(key, value)
            is Boolean -> context.put(key, value)
            is Collection<*> -> context.put(key, value)
            is Map<*, *> -> context.put(key, value)
            else -> context.put(key, value) // POJO 对象直接传递
        }
        return this
    }

    /**
     * 添加工具类到上下文
     *
     * @param name 工具类名称(在模板中使用 $name.method())
     * @param tool 工具类对象
     */
    fun putTool(name: String, tool: Any): VelocityContextBuilder {
        context.put(name, tool)
        return this
    }

    /**
     * 构建 VelocityContext
     */
    fun build(): VelocityContext = context

    companion object {
        /**
         * 快捷构建方法
         */
        fun create(variables: Map<String, Any?>): VelocityContext {
            return VelocityContextBuilder()
                .putAll(variables)
                .build()
        }
    }
}
