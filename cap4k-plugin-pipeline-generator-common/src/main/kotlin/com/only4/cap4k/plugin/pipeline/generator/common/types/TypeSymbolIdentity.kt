package com.only4.cap4k.plugin.pipeline.generator.common.types

const val PROJECT_TYPE_REGISTRY_SOURCE = "project-type-registry"
const val STRONG_ID_SOURCE = "strong-id"
const val MANIFEST_ENUM_SOURCE = "manifest-enum"
const val MANIFEST_VALUE_OBJECT_SOURCE = "manifest-value-object"
const val EXPLICIT_FQCN_SOURCE = "explicit-fqcn"
const val AGGREGATE_SOURCE = "aggregate"

data class TypeSymbolIdentity(
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
