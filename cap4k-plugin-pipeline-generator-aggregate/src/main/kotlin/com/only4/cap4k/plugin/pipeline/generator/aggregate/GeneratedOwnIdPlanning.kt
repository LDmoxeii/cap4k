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
            .onEach { strongId ->
                strongId.idStrategy?.let { strategy ->
                    ApplicationIdentifierStrategyContract.rejectRetiredPolicy(
                        strategy,
                        "${strongId.packageName}.${strongId.typeName}",
                    )
                }
            }
            .filter { it.idStrategy == "uuid7" }
            .filter { it.isEmbeddedId }
            .map { strongId ->
                val strategy = ApplicationIdentifierStrategyContract.requireUuid7(
                    requireNotNull(strongId.idStrategy) {
                        "missing application-side Strong ID strategy for ${strongId.packageName}.${strongId.typeName}"
                    },
                    "${strongId.packageName}.${strongId.typeName}",
                )
                require(strongId.valueType in setOf("String", "UUID")) {
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
                    strategy = strategy,
                    backingType = strongId.valueType,
                )
            }
            .sortedBy { it.entityFqn }
            .toList()
}
