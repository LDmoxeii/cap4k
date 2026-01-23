package com.only4.cap4k.plugin.codegen.template

import com.only4.cap4k.plugin.codegen.misc.concatPathOrHttpUri
import com.only4.cap4k.plugin.codegen.misc.isAbsolutePathOrHttpUri
import com.only4.cap4k.plugin.codegen.misc.loadFileContent
import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString

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
     * 模板源类型：raw|url|resource
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

    fun resolve(context: Map<String, Any?>): PathNode {
        // 渲染节点名称（纯字符串模板）
        name = name
            ?.replace("{{ basePackage }}", "{{ basePackage__as_path }}")
            ?.let { renderString(it, context) }

        // 根据 format 处理模板数据
        val rawData = when (format.lowercase()) {
            "url" -> {
                // data 存储的是模板路径/URL，需要加载文件内容
                data?.let { src ->
                    val absolutePath = if (isAbsolutePathOrHttpUri(src)) {
                        src
                    } else {
                        concatPathOrHttpUri(directory.get(), src)
                    }
                    // 使用 loadFileContent 加载文件内容（支持文件系统和 HTTP）
                    loadFileContent(absolutePath, context["archTemplateEncoding"]?.toString() ?: "UTF-8")
                } ?: ""
            }

            "resource" -> {
                // data 存储的是类路径资源路径，需要加载文件内容
                data?.let { resPath ->
                    val cleanPath = resPath.removePrefix("/").replace('\\', '/')
                    // 尝试多个类加载器
                    val content = Thread.currentThread().contextClassLoader.getResourceAsStream(cleanPath)
                        ?: PathNode::class.java.classLoader.getResourceAsStream(cleanPath)
                        ?: javaClass.classLoader.getResourceAsStream(cleanPath)
                    content?.bufferedReader()?.use { it.readText() } ?: ""
                } ?: ""
            }

            else -> {
                // data 存储的是模板内容，直接使用
                data ?: ""
            }
        }

        // 渲染模板内容
        dataBuilder(rawData, context)
        format = "raw"
        tag = ""

        // 递归处理子节点
        children?.forEach { it.resolve(context) }
        return this
    }

    open fun dataBuilder(rawData: String, context: Map<String, Any?>) {}
}
