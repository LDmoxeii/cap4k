package com.only4.cap4k.codegen.context.design.models

import com.only4.cap4k.codegen.misc.toUpperCamelCase
import com.only4.cap4k.codegen.context.design.models.common.PayloadField

data class QueryDesign(
    override val type: String,
    override val `package`: String,
    override val name: String,
    override val desc: String,
    override val aggregate: String?,
    override val aggregates: List<String>,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
    val requestFields: List<PayloadField> = emptyList(),
    val responseFields: List<PayloadField> = emptyList(),
) : BaseDesign {
    override fun className(): String {
        val candidate = if (name.endsWith("Qry")) name else "${name}Qry"
        return toUpperCamelCase(candidate)!!
    }
}
