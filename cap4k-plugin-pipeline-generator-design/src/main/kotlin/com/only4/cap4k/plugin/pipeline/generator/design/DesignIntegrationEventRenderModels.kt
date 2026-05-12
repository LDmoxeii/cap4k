package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.IntegrationEventModel
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole

internal fun DesignRenderModel.toIntegrationEventContextMap(
    event: IntegrationEventModel,
): Map<String, Any?> = mapOf(
    "packageName" to packageName,
    "typeName" to typeName,
    "description" to description,
    "descriptionText" to descriptionText,
    "descriptionCommentText" to descriptionCommentText,
    "descriptionKotlinStringLiteral" to descriptionKotlinStringLiteral,
    "role" to event.role.contextValue(),
    "eventName" to event.eventName,
    "eventNameKotlinStringLiteral" to event.eventName.toKotlinStringLiteral(),
    "inbound" to (event.role == IntegrationEventRole.INBOUND),
    "outbound" to (event.role == IntegrationEventRole.OUTBOUND),
    "imports" to imports,
    "fields" to requestFields,
    "nestedTypes" to requestNestedTypes,
)

internal data class DesignIntegrationEventSubscriberRenderModel(
    val packageName: String,
    val typeName: String,
    val eventTypeName: String,
    val eventType: String,
    val description: String,
    val descriptionCommentText: String,
    val role: String,
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
        "role" to role,
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
        event: IntegrationEventModel,
    ): DesignIntegrationEventSubscriberRenderModel {
        return DesignIntegrationEventSubscriberRenderModel(
            packageName = subscriberPackageName,
            typeName = "${event.typeName}Subscriber",
            eventTypeName = event.typeName,
            eventType = eventType,
            description = event.description,
            descriptionCommentText = event.description.toKDocCommentText(),
            role = event.role.contextValue(),
            eventName = event.eventName,
            inbound = event.role == IntegrationEventRole.INBOUND,
            outbound = event.role == IntegrationEventRole.OUTBOUND,
            imports = listOf(eventType),
        )
    }
}

private fun IntegrationEventRole.contextValue(): String = name.lowercase()
