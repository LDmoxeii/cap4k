package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.DomainEventModel

internal data class DesignDomainEventHandlerRenderModel(
    val packageName: String,
    val typeName: String,
    val domainEventTypeName: String,
    val domainEventType: String,
    val aggregateName: String,
    val description: String,
    val descriptionCommentText: String,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "domainEventTypeName" to domainEventTypeName,
        "domainEventType" to domainEventType,
        "aggregateName" to aggregateName,
        "description" to description,
        "descriptionCommentText" to descriptionCommentText,
        "imports" to imports,
    )
}

internal object DesignDomainEventHandlerRenderModelFactory {
    fun create(
        eventHandlerPackageName: String,
        domainEventType: String,
        block: DesignBlockModel,
    ): DesignDomainEventHandlerRenderModel {
        val domainEventTypeName = block.domainEventTypeName()
        return DesignDomainEventHandlerRenderModel(
            packageName = eventHandlerPackageName,
            typeName = "${domainEventTypeName}Subscriber",
            domainEventTypeName = domainEventTypeName,
            domainEventType = domainEventType,
            aggregateName = block.aggregates.single(),
            description = block.description,
            descriptionCommentText = block.description.toKDocCommentText(),
            imports = listOf(domainEventType),
        )
    }

    fun create(
        eventHandlerPackageName: String,
        domainEventType: String,
        event: DomainEventModel,
    ): DesignDomainEventHandlerRenderModel {
        return DesignDomainEventHandlerRenderModel(
            packageName = eventHandlerPackageName,
            typeName = "${event.typeName}Subscriber",
            domainEventTypeName = event.typeName,
            domainEventType = domainEventType,
            aggregateName = event.aggregateName,
            description = event.description,
            descriptionCommentText = event.description.toKDocCommentText(),
            imports = listOf(domainEventType),
        )
    }
}
