package com.only4.cap4k.gradle.codegen.template

import com.alibaba.fastjson.JSON
import com.only4.cap4k.gradle.codegen.misc.SourceFileUtils
import java.io.IOException

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
     * 模板数据数据
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

    fun clone(): PathNode {
        return JSON.parseObject(JSON.toJSONString(this), PathNode::class.java)
    }

    companion object {
        private val directory = ThreadLocal<String>()

        fun setDirectory(dir: String) {
            directory.set(dir)
        }

        fun clearDirectory() {
            directory.remove()
        }

        fun getDirectory(): String? {
            return directory.get()
        }
    }

    @Throws(IOException::class)
    open fun resolve(context: Map<String, String?>): PathNode {
        name?.let { nameValue ->
            this.name = nameValue.replace("\${basePackage}", "\${basePackage__as_path}")
            this.name = escape(this.name ?: "", context)
        }

        val rawData = when (format) {
            "url" -> {
                data?.let { dataUrl ->
                    var url = dataUrl
                    if (!SourceFileUtils.isAbsolutePathOrHttpUri(url)) {
                        url = SourceFileUtils.concatPathOrHttpUri(getDirectory() ?: "", url)
                    }
                    SourceFileUtils.loadFileContent(url, context["archTemplateEncoding"] ?: "UTF-8", getDirectory())
                } ?: ""
            }

            "raw" -> data ?: ""
            else -> data ?: ""
        }

        this.data = escape(rawData, context)
        this.format = "raw"

        children?.forEach { child ->
            child.resolve(context)
        }

        return this
    }

    protected fun escape(content: String?, context: Map<String, String?>): String {
        if (content == null) return ""

        val escapeCharacters = mapOf(
            "symbol_pound" to "#",
            "symbol_escape" to "\\",
            "symbol_dollar" to "$"
        )

        var maxReplace = 10
        var result: String = content

        while (maxReplace-- > 0) {
            var hasReplacement = false
            for ((key, value) in context) {
                if (key != null && value != null) {
                    val oldResult = result
                    result = result.replace("\${$key}", value)
                    if (oldResult != result) {
                        hasReplacement = true
                    }
                }
            }
            if (!hasReplacement || !result.contains("\${")) {
                break
            }
        }

        for ((key, value) in escapeCharacters) {
            result = result.replace("\${$key}", value)
        }

        return result
    }
}
