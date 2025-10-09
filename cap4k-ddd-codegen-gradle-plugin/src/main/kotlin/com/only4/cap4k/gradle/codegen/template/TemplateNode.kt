package com.only4.cap4k.gradle.codegen.template

import com.alibaba.fastjson.JSON
import com.only4.cap4k.gradle.codegen.misc.concatPathOrHttpUri
import com.only4.cap4k.gradle.codegen.misc.isAbsolutePathOrHttpUri
import com.only4.cap4k.gradle.codegen.misc.loadFileContent

/**
 * 脚手架模板模板节点
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
class TemplateNode : PathNode() {

    /**
     * 元素匹配正则
     */
    var pattern: String = ""

    fun deepCopy(): TemplateNode {
        return JSON.parseObject(JSON.toJSONString(this), TemplateNode::class.java)
    }

    override fun resolve(context: Map<String, String?>): PathNode {
        // 判断是否为 Velocity 模板
        if (isVelocityTemplate()) {
            // Velocity 模板: 只处理 name,不处理 data
            resolveName(context)
            resolveDataForVelocity(context)
            this.tag = ""
            return this
        }

        // 非 Velocity: 使用父类逻辑(原有行为不变)
        super.resolve(context)
        this.tag = ""
        return this
    }

    /**
     * 判断是否为 Velocity 模板
     */
    fun isVelocityTemplate(): Boolean {
        return format.lowercase() in listOf("velocity", "vm")
    }

    /**
     * 解析节点名称(所有类型都需要)
     */
    private fun resolveName(context: Map<String, String?>) {
        name = name
            ?.replace("\${basePackage}", "\${basePackage__as_path}")
            ?.let { escape(it, context) }
    }

    /**
     * 为 Velocity 模板加载数据
     * - 如果是 url 格式,加载文件内容
     * - 保持原始 Velocity 语法,不调用 escape()
     */
    private fun resolveDataForVelocity(context: Map<String, String?>) {
        // 处理 URL 格式加载(与 PathNode 一致)
        val rawData = when (format.lowercase()) {
            "url" -> {
                data?.let { src ->
                    val abs = if (isAbsolutePathOrHttpUri(src)) src
                    else concatPathOrHttpUri(PathNode.getDirectory(), src)
                    loadFileContent(abs, context["archTemplateEncoding"] ?: "UTF-8")
                } ?: ""
            }

            else -> data ?: ""
        }

        // 保留原始 Velocity 内容,不调用 escape()
        data = rawData

        // 不设置 format = "raw",保留 Velocity 标记
    }
}
