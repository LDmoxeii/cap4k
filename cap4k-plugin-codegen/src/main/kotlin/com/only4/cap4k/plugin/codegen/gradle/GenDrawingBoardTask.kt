package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContextBuilder
import com.only4.cap4k.plugin.codegen.context.drawingboard.MutableDrawingBoardContext
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardGenerator
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codegen.template.PathNode
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal

open class GenDrawingBoardTask : GenArchTask(), MutableDrawingBoardContext {
    @get:Internal
    override val elements: MutableList<DesignElement> = mutableListOf()
    @get:Internal
    override val elementsByTag: MutableMap<String, MutableList<DesignElement>> = mutableMapOf()

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        val tag = templateNodes.firstOrNull()?.tag ?: return
        if (tag == "drawing_board") {
            templateParentPath[tag] = parentPath
            templatePackage[tag] = ""
            return
        }
        super.renderTemplate(templateNodes, parentPath)
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        genDrawingBoard()
    }

    private fun genDrawingBoard() {
        buildContext()
        val templateTag = "drawing_board"
        val templateNodes = template?.select(templateTag).orEmpty()
        if (templateNodes.isEmpty()) {
            logger.warn("No template node found for tag=$templateTag; drawing_board.json will not be generated.")
            return
        }
        val parentPath = templateParentPath[templateTag]
        if (parentPath.isNullOrBlank()) {
            logger.warn("No template parent path found for tag=$templateTag; drawing_board.json will not be generated.")
            return
        }

        val generator = DrawingBoardGenerator()
        val documents = with(this) { generator.documents() }
        if (documents.isEmpty()) {
            logger.warn("No drawing board documents generated; drawing_board.json will not be generated.")
            return
        }
        documents.forEach { document ->
            val generatorName = generator.generatorName(document)
            val templateContext = baseMap.toMutableMap().apply {
                putAll(with(this@GenDrawingBoardTask) { generator.buildContext(document) })
            }
            val pathNodes = renderDrawingBoardPathNodes(
                templateNodes = templateNodes,
                baseContext = templateContext,
                generatorName = generatorName,
                content = document.content
            )
            if (pathNodes.isEmpty()) {
                logger.warn("No template node matched for generator=$generatorName; drawing board document skipped.")
                return@forEach
            }
            pathNodes.forEach { pathNode ->
                forceRender(pathNode, parentPath)
            }
        }
    }

    private fun buildContext() {
        DrawingBoardContextBuilder().build(this)
    }

    companion object {
        @JvmStatic
        internal fun renderDrawingBoardPathNodes(
            templateNodes: List<TemplateNode>,
            baseContext: Map<String, Any?>,
            generatorName: String,
            content: String
        ): List<PathNode> {
            if (templateNodes.isEmpty()) return emptyList()
            val selected = TemplateNode.mergeAndSelect(templateNodes, emptyList(), generatorName)
            return selected.map { templateNode ->
                val resolved = templateNode.deepCopy().apply { resolve(baseContext) }
                resolved.toPathNode().apply {
                    data = content
                    format = "raw"
                }
            }
        }
    }
}
