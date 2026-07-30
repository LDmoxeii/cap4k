package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole

internal fun designBlock(
    tag: String,
    family: String,
    variant: String = "",
    packageName: String = "order",
    name: String,
    description: String = name,
    aggregates: List<String> = emptyList(),
    eventName: String = "",
    persist: Boolean? = null,
    fields: List<FieldModel> = emptyList(),
    resultFields: List<FieldModel> = emptyList(),
    requestDefinition: SemanticValueDefinition? = null,
    responseDefinition: SemanticValueDefinition? = null,
): DesignBlockModel = DesignBlockModel(
    tag = tag,
    packageName = packageName,
    name = name,
    description = description,
    aggregates = aggregates,
    eventName = eventName,
    persist = persist,
    artifacts = listOf(ArtifactSelectionModel(family, variant)),
    request = requestDefinition ?: semanticDefinition(
        packageName = packageName,
        typeName = "$name.Request",
        role = requestRole(tag),
        fields = fields,
    ),
    response = responseDefinition ?: if (tag in setOf("command", "query", "capability", "api_payload")) {
        semanticDefinition(
            packageName = packageName,
            typeName = "$name.Response",
            role = responseRole(tag),
            fields = resultFields,
        )
    } else {
        null
    },
)

internal fun queryBlock(
    packageName: String = "order.read",
    family: String = "query",
    variant: String = "",
    name: String = "FindOrder",
    fields: List<FieldModel> = emptyList(),
    resultFields: List<FieldModel> = emptyList(),
): DesignBlockModel = designBlock(
    tag = "query",
    family = family,
    variant = variant,
    packageName = packageName,
    name = name,
    description = "find order",
    aggregates = listOf("Order"),
    fields = fields,
    resultFields = resultFields,
)

internal fun semanticDefinition(
    packageName: String,
    typeName: String,
    role: SemanticValueRole,
    fields: List<FieldModel> = emptyList(),
): SemanticValueDefinition = SemanticValueDefinition(
    identity = CanonicalTypeIdentity(
        packageName = packageName,
        typePath = typeName.split('.'),
        kind = CanonicalTypeKind.NESTED_VALUE,
    ),
    role = role,
    fields = fields.map { field ->
        val type = legacySemanticType(field.type + if (field.nullable && !field.type.endsWith("?")) "?" else "")
        SemanticValueField(
            name = field.name,
            type = type,
            defaultValue = field.defaultValue?.takeIf { it.isNotBlank() }?.let { raw ->
                SemanticDefaultExpression(
                    kotlinExpression = if (type is SemanticBuiltinTypeRef && type.kind == SemanticBuiltinType.STRING &&
                        !(raw.startsWith('"') && raw.endsWith('"'))
                    ) {
                        "\"${raw.replace("\"", "\\\"")}\""
                    } else {
                        raw
                    },
                    sourceExpression = raw,
                )
            },
        )
    },
)

internal fun semanticValueObject(
    packageName: String,
    name: String,
): com.only4.cap4k.plugin.pipeline.api.ValueObjectModel =
    com.only4.cap4k.plugin.pipeline.api.ValueObjectModel(
        definition = SemanticValueDefinition(
            identity = CanonicalTypeIdentity(packageName, listOf(name), CanonicalTypeKind.VALUE_OBJECT),
            role = SemanticValueRole.VALUE_OBJECT,
        ),
    )

private fun legacySemanticType(expression: String): SemanticTypeRef {
    val normalized = expression.trim()
    val nullable = normalized.endsWith('?')
    val core = normalized.removeSuffix("?")
    fun argumentBody(): String = core.substringAfter('<').dropLast(1)
    return when {
        core.startsWith("List<") -> SemanticListTypeRef(legacySemanticType(argumentBody()), nullable)
        core.startsWith("Set<") -> SemanticSetTypeRef(legacySemanticType(argumentBody()), nullable)
        core.startsWith("Map<") -> {
            val arguments = argumentBody().split(',', limit = 2)
            SemanticMapTypeRef(legacySemanticType(arguments[0]), legacySemanticType(arguments[1]), nullable)
        }
        core.uppercase() in SemanticBuiltinType.entries.map { it.name } ->
            SemanticBuiltinTypeRef(SemanticBuiltinType.valueOf(core.uppercase()), nullable)
        else -> {
            val packagePart = core.substringBeforeLast('.', missingDelimiterValue = "test.types")
            val simpleName = core.substringAfterLast('.')
            SemanticNamedTypeRef(
                CanonicalTypeIdentity(packagePart, listOf(simpleName), CanonicalTypeKind.EXTERNAL),
                nullable,
            )
        }
    }
}

private fun requestRole(tag: String): SemanticValueRole = when (tag) {
    "command" -> SemanticValueRole.COMMAND_REQUEST
    "query" -> SemanticValueRole.QUERY_REQUEST
    "capability" -> SemanticValueRole.CAPABILITY_REQUEST
    "api_payload" -> SemanticValueRole.API_PAYLOAD_REQUEST
    "domain_event" -> SemanticValueRole.DOMAIN_EVENT
    "integration_event" -> SemanticValueRole.INTEGRATION_EVENT
    else -> SemanticValueRole.API_PAYLOAD_REQUEST
}

private fun responseRole(tag: String): SemanticValueRole = when (tag) {
    "command" -> SemanticValueRole.COMMAND_RESPONSE
    "query" -> SemanticValueRole.QUERY_RESPONSE
    "capability" -> SemanticValueRole.CAPABILITY_RESPONSE
    "api_payload" -> SemanticValueRole.API_PAYLOAD_RESPONSE
    else -> error("unsupported response role for $tag")
}
