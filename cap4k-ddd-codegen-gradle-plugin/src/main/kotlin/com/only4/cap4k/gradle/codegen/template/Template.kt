package com.only4.cap4k.gradle.codegen.template

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
     * 获取标签匹配的模板列表
     */
    fun select(tag: String): List<TemplateNode> =
        templates?.filter { it.tag == tag } ?: emptyList()

}
