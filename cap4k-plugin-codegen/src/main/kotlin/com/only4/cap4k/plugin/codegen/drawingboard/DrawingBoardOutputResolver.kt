package com.only4.cap4k.plugin.codegen.drawingboard

import com.only4.cap4k.plugin.codegen.template.TemplateNode
import java.io.File

object DrawingBoardOutputResolver {
    fun resolve(
        tag: String,
        baseContext: Map<String, Any?>,
        templateParentPath: Map<String, String>,
        templateNodes: List<TemplateNode>,
        generatorName: String
    ): List<File> {
        val parentPath = templateParentPath[tag] ?: return emptyList()
        val matched = templateNodes.filter { it.matches(generatorName) }
        if (matched.isEmpty()) return emptyList()

        return matched.map { node ->
            val resolved = node.deepCopy().resolve(baseContext)
            val fileName = resolved.name ?: error("Template node name is required for tag=$tag")
            File(parentPath, fileName)
        }
    }
}
