package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel

internal fun DesignRenderModel.toIntegrationEventContextMap(
    block: DesignBlockModel,
    variant: String,
): Map<String, Any?> = mapOf(
    "packageName" to packageName,
    "typeName" to typeName,
    "description" to description,
    "descriptionText" to descriptionText,
    "descriptionCommentText" to descriptionCommentText,
    "descriptionKotlinStringLiteral" to descriptionKotlinStringLiteral,
    "variant" to variant,
    "eventName" to block.eventName,
    "eventNameKotlinStringLiteral" to block.eventName.toKotlinStringLiteral(),
    "inbound" to (variant == "inbound"),
    "outbound" to (variant == "outbound"),
    "imports" to imports,
    "fields" to fields,
    "nestedTypes" to nestedTypes,
    "buildingBlock" to block.buildingBlockContext("integration-event", variant),
)

internal data class DesignIntegrationEventSubscriberRenderModel(
    val packageName: String,
    val typeName: String,
    val eventTypeName: String,
    val eventType: String,
    val description: String,
    val descriptionCommentText: String,
    val variant: String,
    val eventName: String,
    val inbound: Boolean,
    val outbound: Boolean,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "eventTypeName" to eventTypeName,
        "eventType" to eventType,
        "description" to description,
        "descriptionCommentText" to descriptionCommentText,
        "variant" to variant,
        "eventName" to eventName,
        "inbound" to inbound,
        "outbound" to outbound,
        "imports" to imports,
    )
}

internal object DesignIntegrationEventSubscriberRenderModelFactory {
    fun create(
        subscriberPackageName: String,
        eventType: String,
        block: DesignBlockModel,
        variant: String,
    ): DesignIntegrationEventSubscriberRenderModel {
        val eventTypeName = block.integrationEventTypeName()
        return DesignIntegrationEventSubscriberRenderModel(
            packageName = subscriberPackageName,
            typeName = "${eventTypeName}Subscriber",
            eventTypeName = eventTypeName,
            eventType = eventType,
            description = block.description,
            descriptionCommentText = block.description.toKDocCommentText(),
            variant = variant,
            eventName = block.eventName,
            inbound = variant == "inbound",
            outbound = variant == "outbound",
            imports = listOf(eventType),
        )
    }
}
