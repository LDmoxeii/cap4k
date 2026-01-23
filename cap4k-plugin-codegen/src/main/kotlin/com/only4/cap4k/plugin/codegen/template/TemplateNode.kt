package com.only4.cap4k.plugin.codegen.template

import com.google.gson.Gson
import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString
import java.util.regex.Pattern

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

    @Transient
    @Volatile
    private var cachedFiles: List<TemplateNode>? = null

    @Transient
    @Volatile
    private var cachedPatternSource: String? = null

    @Transient
    @Volatile
    private var cachedPattern: Pattern? = null

    companion object {
        fun mergeAndSelect(
            ctxTop: List<TemplateNode>,
            defTop: List<TemplateNode>,
            genName: String,
        ): List<TemplateNode> {
            fun collectFiles(nodes: List<TemplateNode>): List<TemplateNode> =
                nodes.flatMap { it.collectFiles() }

            val ctxFiles = linkedMapOf<String, TemplateNode>()
            collectFiles(ctxTop).forEach { ctxFiles[it.uniqueKey()] = it }

            val defFiles = linkedMapOf<String, TemplateNode>()
            collectFiles(defTop).forEach { defFiles[it.uniqueKey()] = it }

            fun dirsByPattern(nodes: List<TemplateNode>): Map<String, TemplateNode> =
                buildMap {
                    nodes.filter { it.isDirNode() }.forEach { d ->
                        if (!this.containsKey(d.pattern)) this[d.pattern] = d
                    }
                }

            val ctxDirs = dirsByPattern(ctxTop)
            val defDirs = dirsByPattern(defTop)

            val allKeys: Set<String> = (ctxFiles.keys + defFiles.keys).toSet()
            val groups: List<TemplateNode> = allKeys.map { key ->
                val fileTpl = (ctxFiles[key] ?: defFiles[key])!!.deepCopy()
                val pattern = fileTpl.pattern
                val dirTpl = (ctxDirs[pattern] ?: defDirs[pattern])
                if (dirTpl != null) {
                    val d = dirTpl.deepCopy()
                    d.children = mutableListOf(fileTpl.toPathNode())
                    d
                } else {
                    fileTpl
                }
            }

            return groups.filter { it.matches(genName) }
        }
    }

    fun nodeType(): String = (this.type ?: "file").lowercase()
    fun isDirNode(): Boolean = nodeType() == "dir"
    fun isFileNode(): Boolean = nodeType() == "file"
    fun uniqueKey(): String = "${this.name ?: ""}#${this.pattern}"
    private fun compiledPattern(): Pattern {
        val source = pattern
        val cached = cachedPattern
        if (cached != null && cachedPatternSource == source) return cached
        val compiled = Pattern.compile(source)
        cachedPatternSource = source
        cachedPattern = compiled
        return compiled
    }

    fun matches(genName: String): Boolean = pattern.isBlank() ||
        compiledPattern().asPredicate().test(genName)

    fun collectFiles(): List<TemplateNode> {
        val cached = cachedFiles
        if (cached != null) return cached

        val result = mutableListOf<TemplateNode>()

        // 顶层（本身）是 file 的情况
        if (isFileNode()) {
            result.add(this)
        }

        // 子项遍历（只会是 PathNode）
        fun wrapPathFile(path: PathNode): TemplateNode {
            val t = TemplateNode()
            t.type = path.type
            t.tag = null
            t.name = path.name
            t.format = path.format
            t.encoding = path.encoding
            t.data = path.data
            t.conflict = path.conflict
            t.children = path.children // file 通常无子项；保留以防扩展
            t.pattern = this.pattern
            return t
        }

        fun dfsChildren(node: PathNode?) {
            if (node == null) return
            when (node.type?.lowercase()) {
                "file" -> result.add(wrapPathFile(node))
                "dir", "root", null -> node.children?.forEach { dfsChildren(it) }
                else -> node.children?.forEach { dfsChildren(it) }
            }
        }

        this.children?.forEach { dfsChildren(it) }
        cachedFiles = result
        return result
    }

    fun toPathNode(): PathNode {
        val p = PathNode()
        p.type = this.type
        p.tag = null
        p.name = this.name
        p.format = this.format
        p.encoding = this.encoding
        p.data = this.data
        p.conflict = this.conflict
        // 子项一般不存在 TemplateNode；如存在则递归转换
        p.children = this.children?.map { ch ->
            when (ch) {
                is TemplateNode -> ch.toPathNode()
                else -> {
                    val cp = PathNode()
                    cp.type = ch.type
                    cp.tag = ch.tag
                    cp.name = ch.name
                    cp.format = ch.format
                    cp.encoding = ch.encoding
                    cp.data = ch.data
                    cp.conflict = ch.conflict
                    cp.children = ch.children
                    cp
                }
            }
        }?.toMutableList()
        return p
    }

    fun deepCopy(): TemplateNode {
        val gson = Gson()
        return gson.fromJson(gson.toJson(this), TemplateNode::class.java)
    }

    override fun dataBuilder(rawData: String, context: Map<String, Any?>) {
        data = renderString(rawData, context)
    }
}
