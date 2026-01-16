package com.only4.cap4k.codegen.context.design

import com.only4.cap4k.codegen.context.BaseContext
import com.only4.cap4k.codegen.context.design.models.AggregateInfo
import com.only4.cap4k.codegen.context.design.models.BaseDesign
import com.only4.cap4k.codegen.context.design.models.DesignElement

interface DesignContext : BaseContext {

    val designElementMap: Map<String, List<DesignElement>>

    val aggregateMap: Map<String, AggregateInfo>

    val designMap: Map<String, List<BaseDesign>>
}
