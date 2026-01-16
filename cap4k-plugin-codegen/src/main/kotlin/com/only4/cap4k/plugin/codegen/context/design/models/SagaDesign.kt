package com.only4.cap4k.plugin.codegen.context.design.models

import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase

data class SagaDesign(
    override val type: String,
    override val `package`: String,
    override val name: String,
    override val desc: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
) : BaseDesign {
    override fun className(): String {
        val candidate = if (name.endsWith("Saga")) name else "${name}Saga"
        return toUpperCamelCase(candidate)!!
    }
}
