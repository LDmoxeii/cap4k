package com.only4.cap4k.gradle.codegen.template

import com.only4.cap4k.gradle.codegen.misc.concatPathOrHttpUri
import com.only4.cap4k.gradle.codegen.misc.isAbsolutePathOrHttpUri
import com.only4.cap4k.gradle.codegen.misc.loadFileContent

/**
 * 脚手架模板文件节点
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
open class PathNode {
    /**
     * 节点类型：root|dir|file|segment
     */
    var type: String? = null

    /**
     * 节点标签：关联模板
     */
    var tag: String? = null

    /**
     * 节点名称
     */
    var name: String? = null

    /**
     * 模板源类型：raw|url
     */
    var format: String = "raw"

    /**
     * 输出编码
     */
    var encoding: String? = null

    /**
     * 模板数据
     */
    var data: String? = null

    /**
     * 冲突处理：skip|warn|overwrite
     */
    var conflict: String = "skip"

    /**
     * 下级节点
     */
    var children: MutableList<PathNode>? = null

    companion object {
        private const val MAX_PLACEHOLDER_DEPTH = 10

        private val directory = ThreadLocal<String>()
        fun setDirectory(dir: String) = directory.set(dir)
        fun clearDirectory() = directory.remove()
        fun getDirectory(): String = directory.get()
    }

    open fun resolve(context: Map<String, String?>): PathNode {
        name = name
            ?.replace("${'$'}{basePackage}", "${'$'}{basePackage__as_path}")
            ?.let { escape(it, context) }

        val rawData = when (format.lowercase()) {
            "url" -> data?.let { src ->
                val abs = if (isAbsolutePathOrHttpUri(src)) src else concatPathOrHttpUri(getDirectory(), src)
                loadFileContent(abs, context["archTemplateEncoding"] ?: "UTF-8", getDirectory())
            } ?: ""

            else -> data ?: ""
        }

        data = escape(rawData, context)
        format = "raw" // 解析后统一视为 raw

        children?.forEach { it.resolve(context) }
        return this
    }

    protected fun escape(content: String?, context: Map<String, String?>): String {
        if (content.isNullOrEmpty()) return ""
        return replacePlaceholders(content, context)
            .let(::applyEscapeSymbols)
    }

    private val ESCAPE_SYMBOLS = mapOf(
        "symbol_pound" to "#",
        "symbol_escape" to "\\",
        "symbol_dollar" to "$"
    )

    private tailrec fun replacePlaceholders(current: String, context: Map<String, String?>, depth: Int = 0): String {
        if (depth >= MAX_PLACEHOLDER_DEPTH || !current.contains("${'$'}{")) return current
        var changed = false
        var next = current
        context.forEach { (k, v) ->
            if (v != null && next.contains("${'$'}{$k}")) {
                next = next.replace("${'$'}{$k}", v)
                changed = true
            }
        }
        return if (!changed) next else replacePlaceholders(next, context, depth + 1)
    }

    private fun applyEscapeSymbols(text: String): String {
        var result = text
        ESCAPE_SYMBOLS.forEach { (k, v) -> result = result.replace("${'$'}{$k}", v) }
        return result
    }
}
