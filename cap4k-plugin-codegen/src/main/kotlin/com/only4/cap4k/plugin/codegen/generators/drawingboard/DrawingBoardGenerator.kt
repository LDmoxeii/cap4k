package com.only4.cap4k.plugin.codegen.generators.drawingboard

import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContext
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * Drawing board template generator contract.
 */
interface DrawingBoardGenerator {
    /**
     * Element tag for grouping (cli/qry/cmd/payload/de).
     */
    val tag: String

    /**
     * Template tag in arch template.
     */
    val templateTag: String
        get() = "drawing_board"

    /**
     * Generator order (smaller runs first).
     */
    val order: Int

    /**
     * Whether this generator should run.
     */
    context(ctx: DrawingBoardContext)
    fun shouldGenerate(): Boolean

    /**
     * Build template context for this tag.
     */
    context(ctx: DrawingBoardContext)
    fun buildContext(): Map<String, Any?>

    /**
     * Generator full name (kept for parity with DesignGenerator).
     */
    fun generatorFullName(): String

    /**
     * Generator name used for template pattern matching.
     */
    fun generatorName(): String

    /**
     * Default template nodes when arch template has no matching nodes.
     */
    fun getDefaultTemplateNodes(): List<TemplateNode>

    /**
     * Callback after generation.
     */
    context(ctx: DrawingBoardContext)
    fun onGenerated() {}
}
