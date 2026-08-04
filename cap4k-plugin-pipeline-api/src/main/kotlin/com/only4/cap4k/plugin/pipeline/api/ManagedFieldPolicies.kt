package com.only4.cap4k.plugin.pipeline.api

private val ManagedFieldPolicyKeyRegex = Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*")

fun requireManagedFieldPolicyKey(value: String, label: String = "managed field policy key"): String {
    require(value.matches(ManagedFieldPolicyKeyRegex)) {
        "$label must match [a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*: $value"
    }
    return value
}

enum class ManagedFieldRole {
    IDENTIFIER,
    VERSION,
    SOFT_DELETE,
    SCOPE,
    INITIALIZATION,
    ENRICHMENT,
    DATABASE_GENERATED,
}

enum class ManagedCreationInputPolicy {
    OMIT,
    OPTIONAL,
    REQUIRED,
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

sealed interface ManagedSemanticTypeRef {
    data object TargetField : ManagedSemanticTypeRef

    data class FixedFqn(val value: String) : ManagedSemanticTypeRef {
        init {
            require(value.isNotBlank() && '.' in value) {
                "managed semantic fixed type must be a nonblank FQN: $value"
            }
        }
    }
}

data class PersistenceParticipation(
    val insert: ManagedValueAuthority,
    val update: ManagedValueAuthority,
)

data class ManagedFieldPolicyDefinition(
    val key: String,
    val role: ManagedFieldRole,
    val creationInput: ManagedCreationInputPolicy,
    val explicitValue: ManagedExplicitValuePolicy,
    val lifecycles: Set<ManagedFieldLifecycle>,
    val handlerQualifier: String? = null,
    val handlerSlot: String? = null,
    val semanticValueType: ManagedSemanticTypeRef = ManagedSemanticTypeRef.TargetField,
    val valueAdapterQualifier: String? = null,
    val persistence: PersistenceParticipation,
)

data class ManagedFieldPolicyContributionContext(
    val config: ProjectConfig,
    val options: Map<String, String> = emptyMap(),
)

interface ManagedFieldPolicyProvider : PipelineContribution {
    val id: String

    val descriptor: PipelineCapabilityDescriptor
        get() = PipelineCapabilityDescriptor.identityOnly(
            id,
            PipelineCapabilityKind.MANAGED_FIELD_POLICY,
            PipelineCapabilityActivation.INSTALLED,
        )

    fun definitions(context: ManagedFieldPolicyContributionContext): List<ManagedFieldPolicyDefinition>
}

sealed interface ManagedPolicySelectionProvenance {
    data class ExplicitColumnAnnotation(val sourceLocation: String) : ManagedPolicySelectionProvenance

    data class ExactColumnDefault(val configurationPath: String) : ManagedPolicySelectionProvenance

    data class IdentifierDefault(val configurationPath: String) : ManagedPolicySelectionProvenance
}

sealed interface ManagedPolicyDefinitionOwner {
    data object BuiltIn : ManagedPolicyDefinitionOwner

    data class Extension(
        val extensionId: String,
        val contributionId: String,
    ) : ManagedPolicyDefinitionOwner
}

data class OwnedManagedFieldPolicyDefinition(
    val definition: ManagedFieldPolicyDefinition,
    val owner: ManagedPolicyDefinitionOwner,
)

data class ResolvedManagedFieldPolicy(
    val fieldName: String,
    val columnName: String,
    val fieldType: String,
    val nullable: Boolean,
    val selection: ManagedPolicySelectionProvenance,
    val definitionOwner: ManagedPolicyDefinitionOwner,
    val policyKey: String,
    val role: ManagedFieldRole,
    val creationInput: ManagedCreationInputPolicy,
    val explicitValue: ManagedExplicitValuePolicy,
    val lifecycles: Set<ManagedFieldLifecycle>,
    val handlerQualifier: String?,
    val handlerSlot: String?,
    val semanticValueType: String,
    val valueAdapterQualifier: String?,
    val persistence: PersistenceParticipation,
)

data class ResolvedWriteSurfacePolicy(
    val createAllowedFields: List<String> = emptyList(),
    val updateAllowedFields: List<String> = emptyList(),
)

data class ResolvedManagedEntityPolicy(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val fields: List<ResolvedManagedFieldPolicy>,
    val writeSurface: ResolvedWriteSurfacePolicy,
) {
    fun fieldByRole(role: ManagedFieldRole): ResolvedManagedFieldPolicy? =
        fields.singleOrNull { it.role == role }

    fun requireIdentifier(): ResolvedManagedFieldPolicy = requireNotNull(fieldByRole(ManagedFieldRole.IDENTIFIER)) {
        "managed entity policy $entityPackageName.$entityName has no identifier"
    }
}

object BuiltInManagedFieldPolicies {
    val definitions: List<ManagedFieldPolicyDefinition> = listOf(
        definition(
            key = "identifier.uuid7",
            role = ManagedFieldRole.IDENTIFIER,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.PRESERVE_IF_VALID,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
            handlerQualifier = "identifier.uuid7",
            insert = ManagedValueAuthority.FRAMEWORK,
        ),
        definition(
            key = "identifier.assigned",
            role = ManagedFieldRole.IDENTIFIER,
            creationInput = ManagedCreationInputPolicy.REQUIRED,
            explicitValue = ManagedExplicitValuePolicy.REQUIRE,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
            handlerQualifier = "identifier.assigned",
            insert = ManagedValueAuthority.CALLER,
        ),
        definition(
            key = "identifier.database-identity",
            role = ManagedFieldRole.IDENTIFIER,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.FORBID,
            lifecycles = setOf(ManagedFieldLifecycle.DATABASE),
            insert = ManagedValueAuthority.DATABASE,
        ),
        definition(
            key = "version",
            role = ManagedFieldRole.VERSION,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.FORBID,
            lifecycles = setOf(ManagedFieldLifecycle.PERSISTENCE_PROVIDER),
            insert = ManagedValueAuthority.PERSISTENCE_PROVIDER,
            update = ManagedValueAuthority.PERSISTENCE_PROVIDER,
        ),
        definition(
            key = "soft-delete",
            role = ManagedFieldRole.SOFT_DELETE,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.PRESERVE_IF_VALID,
            lifecycles = setOf(
                ManagedFieldLifecycle.ENTITY_ADMISSION,
                ManagedFieldLifecycle.PERSISTENCE_PROVIDER,
            ),
            handlerQualifier = "soft-delete",
            insert = ManagedValueAuthority.FRAMEWORK,
            update = ManagedValueAuthority.PERSISTENCE_PROVIDER,
        ),
        definition(
            key = "database.generated-on-insert",
            role = ManagedFieldRole.DATABASE_GENERATED,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.FORBID,
            lifecycles = setOf(ManagedFieldLifecycle.DATABASE),
            insert = ManagedValueAuthority.DATABASE,
        ),
        definition(
            key = "database.generated-always",
            role = ManagedFieldRole.DATABASE_GENERATED,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.FORBID,
            lifecycles = setOf(ManagedFieldLifecycle.DATABASE),
            insert = ManagedValueAuthority.DATABASE,
            update = ManagedValueAuthority.DATABASE,
        ),
        definition(
            key = "scope.tenant",
            role = ManagedFieldRole.SCOPE,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.REQUIRE_CONTEXT_MATCH,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
            handlerQualifier = "scope.tenant",
            insert = ManagedValueAuthority.MANAGED_HANDLER,
        ),
        definition(
            key = "initialization.request-context",
            role = ManagedFieldRole.INITIALIZATION,
            creationInput = ManagedCreationInputPolicy.OMIT,
            explicitValue = ManagedExplicitValuePolicy.OVERWRITE,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
            handlerQualifier = "initialization.request-context",
            insert = ManagedValueAuthority.MANAGED_HANDLER,
        ),
        auditDefinition(
            "enrichment.audit-time.created-at",
            "enrichment.audit-time",
            "created-at",
            update = false,
            valueAdapterQualifier = "enrichment.audit-time",
        ),
        auditDefinition(
            "enrichment.audit-time.updated-at",
            "enrichment.audit-time",
            "updated-at",
            update = true,
            valueAdapterQualifier = "enrichment.audit-time",
        ),
        auditDefinition("enrichment.audit-actor.created-by", "enrichment.audit-actor", "created-by", update = false),
        auditDefinition("enrichment.audit-actor.updated-by", "enrichment.audit-actor", "updated-by", update = true),
    )

    private fun auditDefinition(
        key: String,
        qualifier: String,
        slot: String,
        update: Boolean,
        valueAdapterQualifier: String? = null,
    ): ManagedFieldPolicyDefinition = definition(
        key = key,
        role = ManagedFieldRole.ENRICHMENT,
        creationInput = ManagedCreationInputPolicy.OMIT,
        explicitValue = ManagedExplicitValuePolicy.OVERWRITE,
        lifecycles = setOf(ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT),
        handlerQualifier = qualifier,
        handlerSlot = slot,
        semanticValueType = if (qualifier == "enrichment.audit-time") {
            ManagedSemanticTypeRef.FixedFqn("java.time.Instant")
        } else {
            ManagedSemanticTypeRef.TargetField
        },
        valueAdapterQualifier = valueAdapterQualifier,
        insert = ManagedValueAuthority.MANAGED_HANDLER,
        update = if (update) ManagedValueAuthority.MANAGED_HANDLER else ManagedValueAuthority.NONE,
    )

    private fun definition(
        key: String,
        role: ManagedFieldRole,
        creationInput: ManagedCreationInputPolicy,
        explicitValue: ManagedExplicitValuePolicy,
        lifecycles: Set<ManagedFieldLifecycle>,
        handlerQualifier: String? = null,
        handlerSlot: String? = null,
        semanticValueType: ManagedSemanticTypeRef = ManagedSemanticTypeRef.TargetField,
        valueAdapterQualifier: String? = null,
        insert: ManagedValueAuthority,
        update: ManagedValueAuthority = ManagedValueAuthority.NONE,
    ): ManagedFieldPolicyDefinition = ManagedFieldPolicyDefinition(
        key = key,
        role = role,
        creationInput = creationInput,
        explicitValue = explicitValue,
        lifecycles = lifecycles,
        handlerQualifier = handlerQualifier,
        handlerSlot = handlerSlot,
        semanticValueType = semanticValueType,
        valueAdapterQualifier = valueAdapterQualifier,
        persistence = PersistenceParticipation(insert, update),
    )
}
