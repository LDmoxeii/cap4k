package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel

internal fun aggregateElementContext(
    aggregate: String,
    name: String,
    packageName: String,
    description: String,
    type: String,
    root: Boolean = false,
): Map<String, Any?> = mapOf(
    "aggregate" to aggregate,
    "aggregateKotlinStringLiteral" to aggregate.toKotlinStringLiteral(),
    "name" to name,
    "nameKotlinStringLiteral" to name.toKotlinStringLiteral(),
    "packageName" to packageName,
    "packageNameKotlinStringLiteral" to packageName.toKotlinStringLiteral(),
    "description" to description,
    "descriptionKotlinStringLiteral" to description.toKotlinStringLiteral(),
    "type" to type,
    "typeKotlinStringLiteral" to type.toKotlinStringLiteral(),
    "root" to root,
)

internal fun aggregateElementContext(
    entity: EntityModel,
    aggregateName: String,
): Map<String, Any?> = aggregateElementContext(
    aggregate = aggregateName,
    name = entity.name,
    packageName = entity.packageName,
    description = entity.comment,
    type = "entity",
    root = entity.aggregateRoot,
)

internal fun aggregateRootName(entity: EntityModel, entities: List<EntityModel>): String {
    val entitiesByKey = entities.associateBy { EntityRootKey(it.packageName, it.name) }
    val entitiesByName = entities.groupBy { it.name }
    val resolving = mutableSetOf<EntityRootKey>()

    fun resolve(current: EntityModel): String {
        val key = EntityRootKey(current.packageName, current.name)
        if (!resolving.add(key)) {
            throw IllegalArgumentException(
                "Cannot resolve aggregate root for entity ${entity.name}: circular parent chain at ${current.name}."
            )
        }
        val parentName = current.parentEntityName?.takeIf { it.isNotBlank() }
        val rootName = when {
            current.aggregateRoot -> current.name
            parentName == null -> throw IllegalArgumentException(
                "Cannot resolve aggregate root for child entity ${current.name}: parentEntityName is required."
            )
            else -> {
                val parent = entitiesByKey[EntityRootKey(current.packageName, parentName)] ?:
                    entitiesByName[parentName]?.singleOrNull()
                parent?.let { resolve(it) } ?: throw IllegalArgumentException(
                    "Cannot resolve aggregate root for child entity ${current.name}: parent entity $parentName was not found."
                )
            }
        }
        resolving.remove(key)
        return rootName
    }

    return resolve(entity)
}

internal fun aggregateRootNameOrNull(entity: EntityModel, entities: List<EntityModel>): String? =
    try {
        aggregateRootName(entity, entities)
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun strongIdAggregateElementContext(strongId: StrongIdModel): Map<String, Any?> =
    aggregateElementContext(
        aggregate = when (strongId.kind) {
            StrongIdKind.OWN_ID,
            StrongIdKind.AGGREGATE_REFERENCE,
            -> strongId.ownerAggregateName.orEmpty()
            StrongIdKind.REFERENCE -> ""
        },
        name = strongId.typeName,
        packageName = strongId.packageName,
        description = "",
        type = "strong-id",
        root = strongId.kind == StrongIdKind.OWN_ID &&
            strongId.ownerEntityName == strongId.ownerAggregateName &&
            strongId.ownerEntityPackageName == strongId.ownerAggregatePackageName,
    )

private data class EntityRootKey(
    val packageName: String,
    val name: String,
)
