package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind

internal data class GeneratedOwnIdDescriptor(
    val entityName: String,
    val entityPackageName: String,
    val idFieldName: String,
    val idTypeName: String,
    val idTypeFqn: String,
    val strategy: String,
    val backingType: String,
) {
    val entityFqn: String = "$entityPackageName.$entityName"
}

internal object GeneratedOwnIdPlanning {
    fun from(model: CanonicalModel): List<GeneratedOwnIdDescriptor> =
        model.strongIds.asSequence()
            .filter { it.kind == StrongIdKind.OWN_ID }
            .filter { it.idStrategy in setOf("uuid7", "snowflake") }
            .filter { it.isEmbeddedId }
            .map { strongId ->
                require(strongId.valueType in setOf("String", "UUID", "Long")) {
                    "unsupported generated own ID backing for ${strongId.packageName}.${strongId.typeName}: ${strongId.valueType}"
                }
                val entityName = requireNotNull(strongId.ownerEntityName) {
                    "missing owner entity for ${strongId.packageName}.${strongId.typeName}"
                }
                val entityPackage = requireNotNull(strongId.ownerEntityPackageName) {
                    "missing owner entity package for ${strongId.packageName}.${strongId.typeName}"
                }
                val entity = requireNotNull(model.entities.singleOrNull {
                    it.name == entityName && it.packageName == entityPackage
                }) {
                    "missing owner entity model for ${strongId.packageName}.${strongId.typeName}"
                }
                require(entity.idField.type.removeSuffix("?").substringAfterLast('.') == strongId.typeName) {
                    "owner ID field ${entity.packageName}.${entity.name}.${entity.idField.name} " +
                        "does not use ${strongId.typeName}"
                }
                requireNotNull(strongId.ownerAggregateName) {
                    "missing owner aggregate for ${strongId.packageName}.${strongId.typeName}"
                }
                requireNotNull(strongId.ownerAggregatePackageName) {
                    "missing owner aggregate package for ${strongId.packageName}.${strongId.typeName}"
                }
                GeneratedOwnIdDescriptor(
                    entityName = entityName,
                    entityPackageName = entityPackage,
                    idFieldName = entity.idField.name,
                    idTypeName = strongId.typeName,
                    idTypeFqn = "${strongId.packageName}.${strongId.typeName}",
                    strategy = requireNotNull(strongId.idStrategy),
                    backingType = strongId.valueType,
                )
            }
            .sortedBy { it.entityFqn }
            .toList()
}
