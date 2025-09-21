package com.only4.cap4k.gradle.codegen.template

import java.io.IOException

/**
 * 模板
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
class Template : PathNode() {

    /**
     * 模板节点
     */
    var templates: MutableList<TemplateNode>? = null

    /**
     * 获取模板
     *
     * @param tag 标签
     * @return 匹配的模板节点列表
     */
    fun select(tag: String): List<TemplateNode> {
        return templates?.filter { it.tag == tag } ?: emptyList()
    }

    /**
     * 选择第一个匹配的模板
     */
    fun selectFirst(tag: String): TemplateNode? {
        return select(tag).firstOrNull()
    }

    /**
     * 检查是否有指定标签的模板
     */
    fun hasTemplate(tag: String): Boolean {
        return templates?.any { it.tag == tag } ?: false
    }

    /**
     * 添加模板节点
     */
    fun addTemplate(templateNode: TemplateNode) {
        if (templates == null) {
            templates = mutableListOf()
        }
        templates!!.add(templateNode)
    }

    /**
     * 移除模板节点
     */
    fun removeTemplate(tag: String) {
        templates?.removeAll { it.tag == tag }
    }

    /**
     * 获取所有模板标签
     */
    fun getAllTags(): Set<String> {
        return templates?.mapNotNull { it.tag }?.toSet() ?: emptySet()
    }

    @Throws(IOException::class)
    override fun resolve(context: Map<String, String?>): PathNode {
        super.resolve(context)
        templates?.forEach { template ->
            template.resolve(context)
        }
        return this
    }
}