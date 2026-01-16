package com.only4.cap4k.codegen.context.design.models

import com.only4.cap4k.codegen.misc.toUpperCamelCase

data class DomainServiceDesign(
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
        val candidate = if (name.endsWith("Service")) name else "${name}Service"
        return toUpperCamelCase(candidate)!!
    }
}
