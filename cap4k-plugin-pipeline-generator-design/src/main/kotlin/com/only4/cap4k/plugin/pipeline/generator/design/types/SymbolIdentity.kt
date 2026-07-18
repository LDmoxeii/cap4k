package com.only4.cap4k.plugin.pipeline.generator.design.types

internal const val PROJECT_TYPE_REGISTRY_SOURCE = "project-type-registry"
internal const val STRONG_ID_SOURCE = "strong-id"
internal const val MANIFEST_ENUM_SOURCE = "manifest-enum"
internal const val MANIFEST_VALUE_OBJECT_SOURCE = "manifest-value-object"
internal const val EXPLICIT_FQCN_SOURCE = "explicit-fqcn"
internal const val AGGREGATE_SOURCE = "aggregate"

internal data class SymbolIdentity(
    val packageName: String,
    val typeName: String,
    val moduleRole: String? = null,
    val source: String? = null,
    val ownerAggregateName: String? = null,
    val manifestOwned: Boolean = false,
    val shared: Boolean = false,
) {
    val simpleName: String
        get() = typeName.substringAfterLast('.')

    val fqcn: String
        get() = if (packageName.isBlank()) typeName else "$packageName.$typeName"
}
