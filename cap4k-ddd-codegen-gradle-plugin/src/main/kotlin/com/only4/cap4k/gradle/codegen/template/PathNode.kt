package com.only4.cap4k.gradle.codegen.template

import com.only4.cap4k.gradle.codegen.misc.concatPathOrHttpUri
import com.only4.cap4k.gradle.codegen.misc.isAbsolutePathOrHttpUri
import com.only4.cap4k.gradle.codegen.misc.loadFileContent
import com.only4.cap4k.gradle.codegen.velocity.VelocityTemplateRenderer
import org.apache.velocity.VelocityContext

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
        private val directory = ThreadLocal<String>()
        fun setDirectory(dir: String) = directory.set(dir)
        fun clearDirectory() = directory.remove()
        fun getDirectory(): String = directory.get()
    }

    open fun resolve(context: VelocityContext): PathNode {
        name = name
            ?.replace("${'$'}{basePackage}", "${'$'}{basePackage__as_path}")
            ?.let { VelocityTemplateRenderer.INSTANCE.renderString(it, context) }

        val rawData = when (format.lowercase()) {
            "url" -> data?.let { src ->
                val abs = if (isAbsolutePathOrHttpUri(src)) src else concatPathOrHttpUri(directory.get(), src)
                loadFileContent(abs, context["archTemplateEncoding"].toString() ?: "UTF-8")
            } ?: ""

            else -> data ?: ""
        }

        data = VelocityTemplateRenderer.INSTANCE.renderString(rawData, context)
        format = "raw" // 解析后统一视为 raw

        children?.forEach { it.resolve(context) }
        return this
    }
}
