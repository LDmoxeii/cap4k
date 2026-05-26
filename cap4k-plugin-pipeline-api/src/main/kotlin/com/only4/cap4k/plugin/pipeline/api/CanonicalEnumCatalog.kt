package com.only4.cap4k.plugin.pipeline.api

import java.util.Locale

data class CanonicalEnumDescriptor(
    val ownerPackageName: String?,
    val ownerScope: String?,
    val typeName: String,
    val fqn: String,
    val items: List<EnumItemModel>,
    val shared: Boolean,
)

class CanonicalEnumCatalog private constructor(
    val sharedEnums: List<CanonicalEnumDescriptor>,
    val localEnums: List<CanonicalEnumDescriptor>,
    private val sharedEnumFqns: Map<String, String>,
    private val sharedEnumItems: Map<String, List<EnumItemModel>>,
    private val localEnumFqns: Map<LocalEnumOwnerKey, String>,
    private val localEnumItems: Map<LocalEnumOwnerKey, List<EnumItemModel>>,
    private val localValueObjectFqns: Map<LocalValueObjectOwnerKey, String>,
    private val sharedValueObjectFqns: Map<String, String>,
    private val typeRegistry: Map<String, TypeRegistryEntry>,
) {
    val allEnums: List<CanonicalEnumDescriptor> = sharedEnums + localEnums

    fun resolveFieldType(typeName: String, enumItems: List<EnumItemModel>): String {
        return resolveFieldType(ownerPackageName = null, typeName = typeName, enumItems = enumItems)
    }

    fun resolveFieldType(ownerPackageName: String?, typeName: String, enumItems: List<EnumItemModel>): String {
        return resolveKnownType(ownerPackageName, typeName) ?: typeName
    }

    private fun resolveTypeBinding(ownerPackageName: String?, typeName: String): String {
        return resolveKnownType(ownerPackageName, typeName)
            ?: if (typeName in builtInTypeNames) {
                typeName
            } else {
                throw IllegalArgumentException(
                    "unresolved type binding for $typeName: expected enum manifest, type registry, FQN, or built-in type"
                )
            }
    }

    private fun resolveKnownType(ownerPackageName: String?, typeName: String): String? {
        if ('.' in typeName) {
            return typeName
        }
        if (ownerPackageName != null) {
            localValueObjectFqns[LocalValueObjectOwnerKey(ownerPackageName = ownerPackageName, typeBinding = typeName)]?.let {
                return it
            }
            localEnumFqns[LocalEnumOwnerKey(ownerPackageName = ownerPackageName, typeBinding = typeName)]?.let {
                return it
            }
        }
        val sharedValueObjectFqn = sharedValueObjectFqns[typeName]
        if (sharedValueObjectFqn != null) {
            return sharedValueObjectFqn
        }
        val sharedFqn = sharedEnumFqns[typeName]
        if (sharedFqn != null) {
            return sharedFqn
        }
        val mapped = typeRegistry[typeName]?.fqn
        if (mapped != null) {
            return mapped
        }
        return null
    }

    fun resolveFieldType(field: FieldModel): String {
        return resolveFieldType(ownerPackageName = null, field = field)
    }

    fun resolveFieldType(ownerPackageName: String?, field: FieldModel): String {
        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() }
        if (typeBinding != null) {
            return resolveTypeBinding(ownerPackageName, typeBinding)
        }
        return resolveFieldType(ownerPackageName, field.type, field.enumItems)
    }

    fun resolveEnumItems(ownerPackageName: String?, field: FieldModel): List<EnumItemModel> {
        if (field.enumItems.isNotEmpty()) {
            return field.enumItems
        }
        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (ownerPackageName != null) {
            localEnumItems[LocalEnumOwnerKey(ownerPackageName = ownerPackageName, typeBinding = typeBinding)]?.let {
                return it
            }
        }
        return sharedEnumItems[typeBinding].orEmpty()
    }

    companion object {
        fun from(
            model: CanonicalModel,
            basePackage: String,
            typeRegistry: Map<String, TypeRegistryEntry>,
        ): CanonicalEnumCatalog =
            from(model, ArtifactLayoutResolver(basePackage, ArtifactLayoutConfig()), typeRegistry)

        fun from(
            model: CanonicalModel,
            artifactLayout: ArtifactLayoutResolver,
            typeRegistry: Map<String, TypeRegistryEntry>,
        ): CanonicalEnumCatalog {
            val sharedEnumDefinitions = buildSharedEnumDefinitions(model.sharedEnums, artifactLayout)
            val sharedEnumFqns = sharedEnumDefinitions.mapValues { (_, definition) -> definition.fqn }
            val sharedEnumItems = sharedEnumDefinitions.mapValues { (_, definition) -> definition.enumItems }
            sharedEnumFqns.keys.firstOrNull { it in typeRegistry }?.let { typeName ->
                throw IllegalArgumentException(
                    "ambiguous type binding for $typeName: matches both shared enum and general type registry"
                )
            }
            val localEnumDefinitions = buildLocalEnumDefinitions(model.entities, artifactLayout)
            val localEnumFqns = localEnumDefinitions.mapValues { (_, definition) -> definition.fqn }
            val localEnumItems = localEnumDefinitions.mapValues { (_, definition) -> definition.enumItems }
            val valueObjectDefinitions = buildValueObjectDefinitions(model)
            localEnumFqns.keys.firstOrNull { key -> key.typeBinding in sharedEnumFqns }?.let { key ->
                throw IllegalArgumentException(
                    "ambiguous enum ownership for ${key.typeBinding}: matches both shared enum and local enum in ${key.ownerPackageName}"
                )
            }
            localEnumFqns.keys.firstOrNull { key -> key.typeBinding in typeRegistry }?.let { key ->
                throw IllegalArgumentException(
                    "ambiguous enum ownership for ${key.typeBinding}: " +
                        "matches both local enum in ${key.ownerPackageName} and general type registry"
                )
            }
            return CanonicalEnumCatalog(
                sharedEnums = sharedEnumDefinitions.values.map { definition ->
                    CanonicalEnumDescriptor(
                        ownerPackageName = null,
                        ownerScope = null,
                        typeName = definition.typeName,
                        fqn = definition.fqn,
                        items = definition.enumItems,
                        shared = true,
                    )
                },
                localEnums = localEnumDefinitions.map { (key, definition) ->
                    CanonicalEnumDescriptor(
                        ownerPackageName = key.ownerPackageName,
                        ownerScope = definition.ownerScope,
                        typeName = key.typeBinding,
                        fqn = definition.fqn,
                        items = definition.enumItems,
                        shared = false,
                    )
                },
                sharedEnumFqns = sharedEnumFqns,
                sharedEnumItems = sharedEnumItems,
                localEnumFqns = localEnumFqns,
                localEnumItems = localEnumItems,
                localValueObjectFqns = valueObjectDefinitions.local,
                sharedValueObjectFqns = valueObjectDefinitions.shared,
                typeRegistry = typeRegistry,
            )
        }

        private fun buildSharedEnumDefinitions(
            definitions: List<SharedEnumDefinition>,
            artifactLayout: ArtifactLayoutResolver,
        ): Map<String, SharedEnumDefinitionPlan> {
            val grouped = definitions.groupBy { it.typeName.trim() }
            grouped.entries.firstOrNull { it.value.size > 1 }?.key?.let { duplicated ->
                throw IllegalArgumentException("duplicate shared enum definition: $duplicated")
            }
            return grouped.mapValues { (typeName, values) ->
                val definition = values.single()
                val packageName = resolveSharedEnumPackageName(definition.packageName, artifactLayout)
                val fqn = if (packageName.isBlank()) {
                    definition.typeName
                } else {
                    "$packageName.${definition.typeName}"
                }
                SharedEnumDefinitionPlan(typeName = typeName, fqn = fqn, enumItems = definition.items)
            }
        }

        private fun resolveSharedEnumPackageName(
            packageName: String,
            artifactLayout: ArtifactLayoutResolver,
        ): String {
            val trimmed = packageName.trim()
            if ('.' in trimmed) {
                return trimmed
            }
            return artifactLayout.aggregateSharedEnumPackage(trimmed)
        }

        private fun buildLocalEnumDefinitions(
            entities: List<EntityModel>,
            artifactLayout: ArtifactLayoutResolver,
        ): Map<LocalEnumOwnerKey, LocalEnumDefinition> {
            val grouped = entities
                .flatMap { entity ->
                    entity.fields.mapNotNull { field ->
                        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        if (field.enumItems.isEmpty()) {
                            return@mapNotNull null
                        }
                        LocalEnumOwnerKey(
                            ownerPackageName = entity.packageName,
                            typeBinding = typeBinding,
                        ) to LocalEnumDefinition(
                            fqn = buildLocalEnumFqn(entity, typeBinding, artifactLayout),
                            ownerScope = entity.tableName.lowercase(Locale.ROOT),
                            enumItems = field.enumItems,
                        )
                    }
                }
                .groupBy({ it.first }, { it.second })

            grouped.entries.firstOrNull { entry ->
                entry.value.map { it.enumItems }.distinct().size > 1
            }?.let { entry ->
                throw IllegalArgumentException("conflicting local enum definition for ${entry.value.first().fqn}")
            }
            return grouped.mapValues { (_, values) -> values.first() }
        }

        private fun buildLocalEnumFqn(
            entity: EntityModel,
            typeName: String,
            artifactLayout: ArtifactLayoutResolver,
        ): String =
            "${artifactLayout.aggregateLocalEnumPackage(entity.packageName)}.$typeName"

        private fun buildValueObjectDefinitions(model: CanonicalModel): ValueObjectDefinitions {
            val entityPackageByName = model.entities.associateBy(
                keySelector = { it.name },
                valueTransform = { it.packageName },
            )
            val localDefinitions = model.valueObjects
                .filter { it.scope == ValueObjectScope.AGGREGATE }
                .mapNotNull { valueObject ->
                    val ownerPackageName = entityPackageByName[valueObject.aggregate] ?: return@mapNotNull null
                    LocalValueObjectOwnerKey(
                        ownerPackageName = ownerPackageName,
                        typeBinding = valueObject.name,
                    ) to valueObject.fqn()
                }
                .groupBy({ it.first }, { it.second })
            localDefinitions.entries.firstOrNull { (_, values) -> values.size > 1 }?.let { (key, _) ->
                throw IllegalArgumentException("Ambiguous value object type override: ${key.typeBinding}")
            }

            val sharedDefinitions = model.valueObjects
                .filter { it.scope == ValueObjectScope.SHARED }
                .map { it.name to it.fqn() }
                .groupBy({ it.first }, { it.second })
            sharedDefinitions.entries.firstOrNull { (_, values) -> values.size > 1 }?.let { (typeName, _) ->
                throw IllegalArgumentException("Ambiguous value object type override: $typeName")
            }

            return ValueObjectDefinitions(
                local = localDefinitions.mapValues { (_, values) -> values.first() },
                shared = sharedDefinitions.mapValues { (_, values) -> values.first() },
            )
        }

        private fun ValueObjectModel.fqn(): String = "${packageName}.${name}"
    }
}

private data class LocalEnumOwnerKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

private data class LocalValueObjectOwnerKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

private data class LocalEnumDefinition(
    val fqn: String,
    val ownerScope: String,
    val enumItems: List<EnumItemModel>,
)

private data class SharedEnumDefinitionPlan(
    val typeName: String,
    val fqn: String,
    val enumItems: List<EnumItemModel>,
)

private data class ValueObjectDefinitions(
    val local: Map<LocalValueObjectOwnerKey, String>,
    val shared: Map<String, String>,
)

private val builtInTypeNames = setOf(
    "Any",
    "Array",
    "Boolean",
    "Byte",
    "Char",
    "Collection",
    "Double",
    "Float",
    "Int",
    "Iterable",
    "List",
    "Long",
    "Map",
    "MutableCollection",
    "MutableIterable",
    "MutableList",
    "MutableMap",
    "MutableSet",
    "Nothing",
    "Number",
    "Pair",
    "Sequence",
    "Set",
    "Short",
    "String",
    "Triple",
    "Unit",
)
