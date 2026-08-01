package com.only4.cap4k.ddd.core.domain.managed

import kotlin.reflect.KClass

enum class ManagedFieldRole {
    IDENTIFIER,
    VERSION,
    SOFT_DELETE,
    SCOPE,
    INITIALIZATION,
    ENRICHMENT,
    DATABASE_GENERATED,
}

enum class ManagedExplicitValuePolicy {
    PRESERVE_IF_VALID,
    REQUIRE,
    REQUIRE_CONTEXT_MATCH,
    OVERWRITE,
    FORBID,
}

enum class ManagedFieldLifecycle {
    ENTITY_ADMISSION,
    PERSISTENCE_ENRICHMENT,
    PERSISTENCE_PROVIDER,
    DATABASE,
}

enum class ManagedValueAuthority {
    CALLER,
    FRAMEWORK,
    MANAGED_HANDLER,
    PERSISTENCE_PROVIDER,
    DATABASE,
    NONE,
}

data class PersistenceParticipation(
    val insert: ManagedValueAuthority,
    val update: ManagedValueAuthority,
)

sealed interface ManagedFieldRuntimeSupport {
    class ApplicationIdentifier(
        val isAbsent: (Any?) -> Boolean,
        val allocateTarget: (() -> Any)?,
        val validateTarget: (Any) -> Unit,
    ) : ManagedFieldRuntimeSupport

    data class SoftDelete(
        val activeSentinel: Any,
    ) : ManagedFieldRuntimeSupport

    class ForbiddenExplicitValue(
        val isProviderPlaceholder: (Any?) -> Boolean,
    ) : ManagedFieldRuntimeSupport
}

data class ManagedFieldBinding(
    val entityType: KClass<*>,
    val fieldName: String,
    val persistencePropertyName: String,
    val columnName: String,
    val targetType: KClass<*>,
    val nullable: Boolean,
    val policyKey: String,
    val role: ManagedFieldRole,
    val explicitValue: ManagedExplicitValuePolicy,
    val lifecycles: Set<ManagedFieldLifecycle>,
    val handlerQualifier: String?,
    val handlerSlot: String?,
    val semanticValueType: KClass<*>,
    val valueAdapterQualifier: String?,
    val persistence: PersistenceParticipation,
    val runtimeSupport: ManagedFieldRuntimeSupport? = null,
) {
    init {
        require(fieldName.isNotBlank()) { "managed field name must not be blank" }
        require(persistencePropertyName.isNotBlank()) {
            "managed persistence property name must not be blank for ${entityType.qualifiedName}.$fieldName"
        }
        require(columnName.isNotBlank()) {
            "managed column name must not be blank for ${entityType.qualifiedName}.$fieldName"
        }
        require(policyKey.isNotBlank()) {
            "managed policy key must not be blank for ${entityType.qualifiedName}.$fieldName"
        }
        require(lifecycles.isNotEmpty()) {
            "managed lifecycle set must not be empty for ${entityType.qualifiedName}.$fieldName"
        }
        require(handlerQualifier == null || handlerQualifier.isNotBlank()) {
            "managed handler qualifier must not be blank for ${entityType.qualifiedName}.$fieldName"
        }
        require(handlerSlot == null || handlerSlot.isNotBlank()) {
            "managed handler slot must not be blank for ${entityType.qualifiedName}.$fieldName"
        }
        require(handlerQualifier != null || handlerSlot == null) {
            "managed handler slot requires a qualifier for ${entityType.qualifiedName}.$fieldName"
        }
    }

    val label: String
        get() = "${entityType.qualifiedName}.$fieldName[$policyKey]"
}

interface ManagedFieldCatalog {
    val bindings: List<ManagedFieldBinding>
}

interface ManagedFieldAccessor {
    val entityType: KClass<*>
    val fieldName: String
    val policyKey: String
    val mutationFootprint: Set<String>

    fun readRaw(entity: Any): Any?

    fun writeRaw(entity: Any, value: Any?)
}

interface ManagedValueAdapter {
    val qualifier: String
    val sourceType: KClass<*>

    fun supports(targetType: KClass<*>): Boolean

    fun adapt(value: Any, targetType: KClass<*>): Any
}

interface ManagedFieldHandle {
    val fieldName: String
    val persistencePropertyName: String
    val policyKey: String
    val handlerQualifier: String?
    val handlerSlot: String?
    val semanticValueType: KClass<*>
    val targetType: KClass<*>
    val nullable: Boolean
    val runtimeSupport: ManagedFieldRuntimeSupport?
    val mutationFootprint: Set<String>

    fun readTarget(): Any?

    fun adaptSemantic(value: Any?): Any?

    fun matchesSemantic(value: Any?): Boolean

    fun assignSemantic(value: Any?)
}
