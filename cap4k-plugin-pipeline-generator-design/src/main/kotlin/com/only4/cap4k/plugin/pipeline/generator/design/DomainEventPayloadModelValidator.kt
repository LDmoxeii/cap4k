package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.SemanticArrayTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition

internal fun CanonicalModel.validateDomainEventPayloads() {
    designBlocks
        .filter { block -> block.tag == "domain_event" }
        .forEach { block -> block.validateDomainEventPayload(this) }
}

internal fun DesignBlockModel.validateDomainEventPayload(model: CanonicalModel) {
    val eventValue = requireNotNull(request) { "domain_event $name is missing its canonical event value" }
    val entityTypes = model.entities
        .map { entity -> "${entity.packageName}.${entity.name}" }
        .toSet()
    val inspectableValues = buildMap {
        eventValue.collectDefinitionsInto(this)
        model.valueObjects.forEach { valueObject -> valueObject.definition.collectDefinitionsInto(this) }
    }

    eventValue.fields.forEach { field ->
        val fieldPath = field.sourcePath.ifBlank { "fields.${field.name}" }
        validateDomainEventType(
            eventName = name,
            fieldPath = fieldPath,
            type = field.type,
            entityTypes = entityTypes,
            inspectableValues = inspectableValues,
            inspectingDefinitions = emptySet(),
        )
    }
}

private fun SemanticValueDefinition.collectDefinitionsInto(
    destination: MutableMap<String, SemanticValueDefinition>,
) {
    destination[identity.fqn] = this
    nestedDefinitions.forEach { nested -> nested.collectDefinitionsInto(destination) }
}

private fun validateDomainEventType(
    eventName: String,
    fieldPath: String,
    type: SemanticTypeRef,
    entityTypes: Set<String>,
    inspectableValues: Map<String, SemanticValueDefinition>,
    inspectingDefinitions: Set<String>,
) {
    when (type) {
        is SemanticBuiltinTypeRef -> Unit
        is SemanticNamedTypeRef -> {
            require(type.symbol.fqn !in entityTypes) {
                "domain_event $eventName field $fieldPath references persistent Entity/Aggregate type ${type.symbol.fqn}."
            }
            val definition = inspectableValues[type.symbol.fqn] ?: return
            if (definition.identity.fqn in inspectingDefinitions) return
            val nextInspectingDefinitions = inspectingDefinitions + definition.identity.fqn
            definition.fields.forEach { nestedField ->
                validateDomainEventType(
                    eventName = eventName,
                    fieldPath = "$fieldPath.${nestedField.name}",
                    type = nestedField.type,
                    entityTypes = entityTypes,
                    inspectableValues = inspectableValues,
                    inspectingDefinitions = nextInspectingDefinitions,
                )
            }
        }
        is SemanticListTypeRef -> validateDomainEventType(
            eventName,
            "$fieldPath[]",
            type.elementType,
            entityTypes,
            inspectableValues,
            inspectingDefinitions,
        )
        is SemanticSetTypeRef -> validateDomainEventType(
            eventName,
            "$fieldPath[]",
            type.elementType,
            entityTypes,
            inspectableValues,
            inspectingDefinitions,
        )
        is SemanticArrayTypeRef -> validateDomainEventType(
            eventName,
            "$fieldPath[]",
            type.elementType,
            entityTypes,
            inspectableValues,
            inspectingDefinitions,
        )
        is SemanticMapTypeRef -> {
            validateDomainEventType(
                eventName,
                "$fieldPath{key}",
                type.keyType,
                entityTypes,
                inspectableValues,
                inspectingDefinitions,
            )
            validateDomainEventType(
                eventName,
                "$fieldPath{value}",
                type.valueType,
                entityTypes,
                inspectableValues,
                inspectingDefinitions,
            )
        }
    }
}
