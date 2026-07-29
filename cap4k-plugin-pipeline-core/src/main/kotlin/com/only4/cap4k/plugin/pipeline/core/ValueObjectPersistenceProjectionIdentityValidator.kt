package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.JsonValuePersistenceProjection
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel

internal object ValueObjectPersistenceProjectionIdentityValidator {
    fun validate(
        valueObjects: Iterable<ValueObjectModel>,
        canonicalDeclarations: Iterable<CanonicalTypeIdentity>,
        artifactDeclarationFqns: Iterable<String> = emptyList(),
    ) {
        val declarations = (
            canonicalDeclarations.map { identity -> identity.fqn } +
                artifactDeclarationFqns.map(String::trim).filter(String::isNotEmpty)
            ).toSet()
        val projections = valueObjects.mapNotNull { valueObject ->
            (valueObject.persistence as? JsonValuePersistenceProjection)?.let { projection ->
                valueObject.definition.identity.fqn to projection.converterClassFqn.trim()
            }
        }
        projections
            .groupBy { (_, converterFqn) -> converterFqn }
            .entries
            .firstOrNull { (_, owners) -> owners.size > 1 }
            ?.let { (converterFqn, owners) ->
                throw IllegalArgumentException(
                    "JSON value-object converter identity $converterFqn is derived by multiple value objects: " +
                        owners.map { (ownerFqn, _) -> ownerFqn }.sorted().joinToString(", ")
                )
            }
        projections.firstOrNull { (_, converterFqn) -> converterFqn in declarations }?.let { (ownerFqn, converterFqn) ->
            throw IllegalArgumentException(
                "value object $ownerFqn JSON converter identity conflicts with canonical/artifact declaration: $converterFqn"
            )
        }
    }
}
