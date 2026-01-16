package com.only4.cap4k.codegen.context.design.models

import com.only4.cap4k.codegen.misc.toUpperCamelCase
import com.only4.cap4k.codegen.context.design.models.common.PayloadField

data class DomainEventDesign(
    override val type: String = "domain_event",
    override val `package`: String,
    override val name: String,
    override val desc: String,
    override val aggregate: String,
    override val aggregates: List<String>,
    override val primaryAggregateMetadata: AggregateInfo?,
    override val aggregateMetadataList: List<AggregateInfo>,
    val requestFields: List<PayloadField> = emptyList(),
    val responseFields: List<PayloadField> = emptyList(),
    val persist: Boolean = false,
) : BaseDesign {
    override fun className(): String {
        var candidate = name
        if (!candidate.endsWith("Evt") && !candidate.endsWith("Event")) {
            candidate += "DomainEvent"
        }
        return toUpperCamelCase(candidate)!!
    }
}
