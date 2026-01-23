package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContextBuilder
import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContext
import com.only4.cap4k.plugin.codegen.context.drawingboard.MutableDrawingBoardContext
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardGenerator
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardCliGenerator
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardCmdGenerator
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardDeGenerator
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardPayloadGenerator
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardQryGenerator
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal

open class GenDrawingBoardTask : GenArchTask(), MutableDrawingBoardContext {
    @get:Internal
    override val elements: MutableList<DesignElement> = mutableListOf()
    @get:Internal
    override val elementsByTag: MutableMap<String, MutableList<DesignElement>> = mutableMapOf()

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        genDrawingBoard()
    }

    private fun genDrawingBoard() {
        val context = buildDrawingBoardContext()
        if (context.elements.isEmpty()) return

        with(context) {
            generateDrawingBoardFiles()
        }
    }

    private fun buildDrawingBoardContext(): DrawingBoardContext {
        val builders = listOf(
            DrawingBoardContextBuilder()
        )
        builders.sortedBy { it.order }.forEach { builder ->
            builder.build(this)
        }
        return this
    }

    context(ctx: DrawingBoardContext)
    private fun generateDrawingBoardFiles() {
        val generators = listOf(
            DrawingBoardCmdGenerator(),
            DrawingBoardQryGenerator(),
            DrawingBoardCliGenerator(),
            DrawingBoardPayloadGenerator(),
            DrawingBoardDeGenerator()
        )

        generators.sortedBy { it.order }.forEach { generator ->
            generateForTag(generator)
        }
    }

    context(ctx: DrawingBoardContext)
    private fun generateForTag(
        generator: DrawingBoardGenerator
    ) {
        if (!generator.shouldGenerate()) return

        val parentPath = ctx.templateParentPath[generator.tag]!!

        val templateContext = generator.buildContext().toMutableMap()
        val generatorName = generator.generatorName()
        val ctxTop = ctx.templateNodeMap.getOrDefault(generator.tag, emptyList())
        val defTop = generator.getDefaultTemplateNodes()

        val selected = TemplateNode.mergeAndSelect(ctxTop, defTop, generatorName)
        if (selected.isEmpty()) {
            logger.warn("No template node matched for generator=$generatorName; drawing board document skipped.")
            return
        }

        selected.forEach { templateNode ->
            val pathNode = templateNode.resolve(templateContext)
            forceRender(pathNode, parentPath)
        }

        generator.onGenerated()
    }
}
