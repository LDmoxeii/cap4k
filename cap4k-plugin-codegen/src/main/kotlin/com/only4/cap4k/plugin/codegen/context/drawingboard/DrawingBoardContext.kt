package com.only4.cap4k.plugin.codegen.context.drawingboard

import com.only4.cap4k.plugin.codegen.context.BaseContext
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement

interface DrawingBoardContext : BaseContext {
    val elements: List<DesignElement>
    val elementsByTag: Map<String, List<DesignElement>>
}

interface MutableDrawingBoardContext : DrawingBoardContext {
    override val elements: MutableList<DesignElement>
    override val elementsByTag: MutableMap<String, MutableList<DesignElement>>
}
