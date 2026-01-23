package com.only4.cap4k.plugin.codegen.generators.drawingboard

import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContext
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * Base generator for a single drawing board tag.
 */
abstract class AbstractDrawingBoardTagGenerator(
    final override val elementsTag: String,
    final override val order: Int = 10
) : DrawingBoardGenerator {

    override fun generatorFullName(): String = generatorName()

    override fun generatorName(): String = tag

    context(ctx: DrawingBoardContext)
    override fun shouldGenerate(): Boolean = ctx.elementsByTag[elementsTag].orEmpty().isNotEmpty()

    context(ctx: DrawingBoardContext)
    override fun buildContext(): Map<String, Any?> {
        val elements = ctx.elementsByTag[elementsTag].orEmpty()
        return ctx.baseMap.toMutableMap().apply {
            put("drawingBoardTag", tag)
            put("elements", elements)
            put("elementsByTag", ctx.elementsByTag)
        }
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@AbstractDrawingBoardTagGenerator.tag
                name = "{{ drawingBoardTag }}.json"
                format = "url"
                data = "template/_tpl/drawing_board.json.peb"
            }
        )
    }
}
