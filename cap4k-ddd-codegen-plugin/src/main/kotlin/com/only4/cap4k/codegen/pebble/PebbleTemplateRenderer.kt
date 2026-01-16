package com.only4.cap4k.codegen.pebble

import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter

object PebbleTemplateRenderer {

    /**
     * 渲染字符串模板（支持递归解析）
     *
     * 用于渲染纯模板字符串内容，会递归解析直到不再包含模板语法或达到最大递归深度
     *
     * @param templateContent 模板内容字符串
     * @param context 模板上下文
     * @return 渲染后的字符串
     */
    fun renderString(
        templateContent: String,
        context: Map<String, Any?>,
    ): String {
        val engine = ensureInitialized()
        var result = templateContent
        var iterations = 0
        val maxIterations = 10

        // 正则匹配 Pebble 语法：{{ ... }} 或 {% ... %}
        val pebblePattern = Regex("""(\{\{.*?}}|\{%.*?%})""")

        while (pebblePattern.containsMatchIn(result) && iterations < maxIterations) {
            val writer = StringWriter()
            val template = engine.getTemplate(result)
            template.evaluate(writer, context)

            val newResult = writer.toString()

            // 如果解析后结果没有变化，说明无法继续解析，停止递归
            if (newResult == result) {
                break
            }

            result = newResult
            iterations++
        }

        return result
    }

    /**
     * 确保 Pebble 引擎已初始化
     */
    private fun ensureInitialized(): PebbleEngine {
        if (!PebbleInitializer.isInitialized()) {
            PebbleInitializer.initPebble()
        }
        return PebbleInitializer.getEngine()
    }
}
