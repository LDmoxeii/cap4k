package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DomainEventModel

internal data class DesignDomainEventHandlerRenderModel(
    val packageName: String,
    val typeName: String,
    val domainEventTypeName: String,
    val domainEventType: String,
    val aggregateName: String,
    val description: String,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "domainEventTypeName" to domainEventTypeName,
        "domainEventType" to domainEventType,
        "aggregateName" to aggregateName,
        "description" to description,
        "imports" to imports,
    )
}

internal object DesignDomainEventHandlerRenderModelFactory {
    fun create(basePackage: String, event: DomainEventModel): DesignDomainEventHandlerRenderModel {
        return DesignDomainEventHandlerRenderModel(
            packageName = "$basePackage.application.${event.packageName}.events",
            typeName = "${event.typeName}Subscriber",
            domainEventTypeName = event.typeName,
            domainEventType = "$basePackage.domain.${event.packageName}.events.${event.typeName}",
            aggregateName = event.aggregateName,
            description = event.description,
            imports = listOf("$basePackage.domain.${event.packageName}.events.${event.typeName}"),
        )
    }
}
