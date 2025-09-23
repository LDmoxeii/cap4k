package com.only4.cap4k.gradle.codegen.template

import com.alibaba.fastjson.JSON
import java.io.IOException

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
    var pattern: String? = null

    fun cloneTemplateNode(): TemplateNode {
        return JSON.parseObject(JSON.toJSONString(this), TemplateNode::class.java)
    }

    @Throws(IOException::class)
    override fun resolve(context: Map<String, String?>): PathNode {
        super.resolve(context)
        this.tag = ""
        return this
    }
}
