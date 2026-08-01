package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BuiltInManagedFieldPolicies
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ManagedCreationInputPolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedExplicitValuePolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldLifecycle
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyDefinition
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicyDefinitionOwner
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicySelectionProvenance
import com.only4.cap4k.plugin.pipeline.api.ManagedSemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.ManagedValueAuthority
import com.only4.cap4k.plugin.pipeline.api.OwnedManagedFieldPolicyDefinition
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedEntityPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedFieldPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedWriteSurfacePolicy
import com.only4.cap4k.plugin.pipeline.api.requireManagedFieldPolicyKey
import java.util.Locale

internal object ManagedFieldPolicyResolver {
    fun resolve(
        config: ProjectConfig,
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
        contributedDefinitions: List<OwnedManagedFieldPolicyDefinition>,
    ): List<ResolvedManagedEntityPolicy> {
        val definitions = collectDefinitions(contributedDefinitions)
        val tableByName = tables.associateBy { it.tableName.lowercase(Locale.ROOT) }
        val resolved = entities.mapNotNull { entity ->
            val table = tableByName[entity.tableName.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            resolveEntity(config, entity, table, definitions)
        }
        validateQualifierKinds(resolved)
        return resolved
    }

    private fun collectDefinitions(
        contributed: List<OwnedManagedFieldPolicyDefinition>,
    ): Map<String, OwnedManagedFieldPolicyDefinition> {
        val all = BuiltInManagedFieldPolicies.definitions.map { definition ->
            OwnedManagedFieldPolicyDefinition(definition, ManagedPolicyDefinitionOwner.BuiltIn)
        } + contributed

        all.forEach(::validateDefinition)
        all.groupBy { it.definition.key }
            .entries
            .firstOrNull { it.value.size > 1 }
            ?.let { (key, owners) ->
                throw IllegalArgumentException(
                    "duplicate managed field policy definition $key: " +
                        owners.joinToString { describeOwner(it.owner) },
                )
            }
        return all.associateBy { it.definition.key }
    }

    private fun validateDefinition(owned: OwnedManagedFieldPolicyDefinition) {
        val definition = owned.definition
        requireManagedFieldPolicyKey(definition.key)
        require(definition.lifecycles.isNotEmpty()) {
            "managed field policy ${definition.key} must declare at least one lifecycle"
        }
        require(
            ManagedFieldLifecycle.ENTITY_ADMISSION !in definition.lifecycles ||
                ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT !in definition.lifecycles
        ) {
            "managed field policy ${definition.key} cannot combine ENTITY_ADMISSION and PERSISTENCE_ENRICHMENT"
        }

        val handlerKind = handlerKind(definition)
        if (handlerKind == null) {
            require(definition.handlerQualifier == null) {
                "managed field policy ${definition.key} declares handler qualifier without an application handler lifecycle"
            }
        } else {
            require(!definition.handlerQualifier.isNullOrBlank()) {
                "managed field policy ${definition.key} requires a handler qualifier for $handlerKind"
            }
            requireManagedFieldPolicyKey(requireNotNull(definition.handlerQualifier), "handler qualifier")
        }
        require(definition.handlerSlot == null || definition.handlerQualifier != null) {
            "managed field policy ${definition.key} declares a slot without a handler qualifier"
        }
        definition.handlerSlot?.let { slot ->
            require(slot.matches(HandlerSlotRegex)) {
                "handler slot must match [a-z][a-z0-9-]* for managed field policy ${definition.key}: $slot"
            }
        }
        definition.valueAdapterQualifier?.let { qualifier ->
            requireManagedFieldPolicyKey(qualifier, "managed value adapter qualifier")
        }

        validateCreationInput(definition)
        validateExplicitValueLifecycle(definition)
        validateAuthority(definition, insert = true, owner = owned.owner)
        validateAuthority(definition, insert = false, owner = owned.owner)

        if (definition.role == ManagedFieldRole.IDENTIFIER) {
            require(definition.key.startsWith("identifier.")) {
                "identifier managed field policy must use identifier.* key: ${definition.key}"
            }
        } else {
            require(!definition.key.startsWith("identifier.")) {
                "managed field policy ${definition.key} uses identifier.* key but declares role ${definition.role}"
            }
        }
    }

    private fun validateCreationInput(definition: ManagedFieldPolicyDefinition) {
        if (definition.creationInput == ManagedCreationInputPolicy.OMIT) return

        require(
            definition.persistence.insert in setOf(
                ManagedValueAuthority.CALLER,
                ManagedValueAuthority.FRAMEWORK,
                ManagedValueAuthority.MANAGED_HANDLER,
            )
        ) {
            "managed field policy ${definition.key} creation input ${definition.creationInput} requires an " +
                "application-visible INSERT authority, but was ${definition.persistence.insert}"
        }
        require(
            definition.explicitValue !in setOf(
                ManagedExplicitValuePolicy.OVERWRITE,
                ManagedExplicitValuePolicy.FORBID,
            )
        ) {
            "managed field policy ${definition.key} creation input ${definition.creationInput} cannot use " +
                "explicit value ${definition.explicitValue}"
        }
        require(
            definition.creationInput != ManagedCreationInputPolicy.OPTIONAL ||
                definition.explicitValue != ManagedExplicitValuePolicy.REQUIRE
        ) {
            "managed field policy ${definition.key} OPTIONAL creation input cannot require an explicit value"
        }
    }

    private fun validateExplicitValueLifecycle(definition: ManagedFieldPolicyDefinition) {
        when (definition.explicitValue) {
            ManagedExplicitValuePolicy.PRESERVE_IF_VALID,
            ManagedExplicitValuePolicy.REQUIRE,
            ManagedExplicitValuePolicy.REQUIRE_CONTEXT_MATCH,
            -> require(ManagedFieldLifecycle.ENTITY_ADMISSION in definition.lifecycles) {
                "managed field policy ${definition.key} explicit value ${definition.explicitValue} requires ENTITY_ADMISSION"
            }

            ManagedExplicitValuePolicy.OVERWRITE -> require(
                ManagedFieldLifecycle.ENTITY_ADMISSION in definition.lifecycles ||
                    ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT in definition.lifecycles
            ) {
                "managed field policy ${definition.key} explicit value OVERWRITE requires an application handler lifecycle"
            }

            ManagedExplicitValuePolicy.FORBID -> Unit
        }
    }

    private fun validateAuthority(
        definition: ManagedFieldPolicyDefinition,
        insert: Boolean,
        owner: ManagedPolicyDefinitionOwner,
    ) {
        val operation = if (insert) "INSERT" else "UPDATE"
        val authority = if (insert) definition.persistence.insert else definition.persistence.update
        val valid = when (authority) {
            ManagedValueAuthority.CALLER -> insert && ManagedFieldLifecycle.ENTITY_ADMISSION in definition.lifecycles
            ManagedValueAuthority.FRAMEWORK ->
                insert && owner == ManagedPolicyDefinitionOwner.BuiltIn &&
                    ManagedFieldLifecycle.ENTITY_ADMISSION in definition.lifecycles
            ManagedValueAuthority.MANAGED_HANDLER -> if (insert) {
                handlerKind(definition) != null
            } else {
                ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT in definition.lifecycles
            }
            ManagedValueAuthority.PERSISTENCE_PROVIDER ->
                ManagedFieldLifecycle.PERSISTENCE_PROVIDER in definition.lifecycles
            ManagedValueAuthority.DATABASE -> ManagedFieldLifecycle.DATABASE in definition.lifecycles
            ManagedValueAuthority.NONE -> true
        }
        require(valid) {
            "managed field policy ${definition.key} has invalid $operation authority $authority for " +
                "lifecycles ${definition.lifecycles} and owner ${describeOwner(owner)}"
        }
    }

    private fun resolveEntity(
        config: ProjectConfig,
        entity: EntityModel,
        table: DbTableSnapshot,
        definitions: Map<String, OwnedManagedFieldPolicyDefinition>,
    ): ResolvedManagedEntityPolicy {
        require(table.primaryKey.size == 1) {
            "table ${table.tableName} must have exactly one physical primary-key column for managed field resolution"
        }
        val fieldByColumn = entity.fields.associateBy { (it.columnName ?: it.name).lowercase(Locale.ROOT) }
        val columnDefaults = normalizeColumnDefaults(config)
        val fields = table.columns.mapNotNull { column ->
            val selection = selectPolicy(config, table, column, columnDefaults) ?: return@mapNotNull null
            val owned = definitions[selection.key] ?: throw IllegalArgumentException(
                "unresolved managed field policy ${selection.key} for ${table.tableName}.${column.name} " +
                    "selected from ${describeSelection(selection.provenance)}",
            )
            val field = requireNotNull(fieldByColumn[column.name.lowercase(Locale.ROOT)]) {
                "missing canonical entity field identity for ${entity.packageName}.${entity.name}.${column.name}"
            }
            val targetFieldType = resolvedTargetFieldType(config, field)
            validateSchemaCompatibility(config, entity, table, column, targetFieldType, owned.definition)
            resolveField(field.name, targetFieldType, field.nullable, column, selection.provenance, owned)
        }

        val identifier = fields.filter { it.role == ManagedFieldRole.IDENTIFIER }
        require(identifier.size == 1) {
            "table ${table.tableName} must resolve exactly one identifier managed field policy; " +
                "resolved=${identifier.map { it.policyKey }}"
        }
        validateRoleCardinality(entity, fields, ManagedFieldRole.VERSION)
        validateRoleCardinality(entity, fields, ManagedFieldRole.SOFT_DELETE)
        validateSlots(entity, fields)

        return ResolvedManagedEntityPolicy(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = table.tableName,
            fields = fields,
            writeSurface = buildWriteSurface(entity, fields),
        )
    }

    private fun normalizeColumnDefaults(config: ProjectConfig): Map<String, Pair<String, String>> {
        val normalized = linkedMapOf<String, Pair<String, String>>()
        config.managedFields.columnPolicyDefaults.forEach { (rawColumn, policyKey) ->
            val column = rawColumn.trim()
            require(column.isNotEmpty()) { "managedFields.columnPolicyDefaults contains a blank column name" }
            requireManagedFieldPolicyKey(policyKey, "managedFields.columnPolicyDefaults[$column]")
            val key = column.lowercase(Locale.ROOT)
            require(key !in normalized) {
                "managedFields.columnPolicyDefaults contains duplicate normalized column $column"
            }
            normalized[key] = policyKey to "managedFields.columnPolicyDefaults[$column]"
        }
        return normalized
    }

    private fun selectPolicy(
        config: ProjectConfig,
        table: DbTableSnapshot,
        column: DbColumnSnapshot,
        columnDefaults: Map<String, Pair<String, String>>,
    ): PolicySelection? {
        column.managedPolicyKey?.let { key ->
            return PolicySelection(
                key = key,
                provenance = ManagedPolicySelectionProvenance.ExplicitColumnAnnotation(
                    "${table.tableName}.${column.name}#comment:@Managed",
                ),
            )
        }
        columnDefaults[column.name.lowercase(Locale.ROOT)]?.let { (key, path) ->
            return PolicySelection(key, ManagedPolicySelectionProvenance.ExactColumnDefault(path))
        }
        if (column.isPrimaryKey) {
            val key = config.managedFields.identifierDefaultPolicy
            requireManagedFieldPolicyKey(key, "managedFields.identifierDefaultPolicy")
            return PolicySelection(
                key,
                ManagedPolicySelectionProvenance.IdentifierDefault("managedFields.identifierDefaultPolicy"),
            )
        }
        return null
    }

    private fun validateSchemaCompatibility(
        config: ProjectConfig,
        entity: EntityModel,
        table: DbTableSnapshot,
        column: DbColumnSnapshot,
        fieldType: String,
        definition: ManagedFieldPolicyDefinition,
    ) {
        require(column.isPrimaryKey == (definition.role == ManagedFieldRole.IDENTIFIER)) {
            val fact = if (column.isPrimaryKey) "physical primary-key" else "non-primary-key"
            "managed field policy ${definition.key} with role ${definition.role} is incompatible with $fact " +
                "column ${table.tableName}.${column.name}"
        }
        if (definition.key == "identifier.database-identity") {
            AggregateIdPolicyResolver.validateType(
                config = config,
                entity = entity,
                strategy = "identity",
            )
        }
        if (
            definition.role == ManagedFieldRole.IDENTIFIER &&
            definition.persistence.insert in setOf(
                ManagedValueAuthority.PERSISTENCE_PROVIDER,
                ManagedValueAuthority.DATABASE,
            )
        ) {
            require(definition.key == "identifier.database-identity") {
                "managed identifier policy ${definition.key} cannot be projected by the current JPA provider; " +
                    "only identifier.database-identity supports provider/database identifier assignment"
            }
        }
        if (definition.role == ManagedFieldRole.VERSION) {
            require(fieldType in SupportedVersionTypes) {
                "unsupported version type for table ${table.tableName}, entity ${entity.packageName}.${entity.name}, " +
                    "field ${column.name}: $fieldType; supported types: Short, Int, Long"
            }
        }
    }

    private fun resolveField(
        fieldName: String,
        fieldType: String,
        nullable: Boolean,
        column: DbColumnSnapshot,
        selection: ManagedPolicySelectionProvenance,
        owned: OwnedManagedFieldPolicyDefinition,
    ): ResolvedManagedFieldPolicy {
        val definition = owned.definition
        val semanticType = when (val ref = definition.semanticValueType) {
            ManagedSemanticTypeRef.TargetField -> fieldType
            is ManagedSemanticTypeRef.FixedFqn -> ref.value
        }
        if (!sameJvmType(semanticType, fieldType)) {
            require(!definition.valueAdapterQualifier.isNullOrBlank()) {
                "managed field policy ${definition.key} resolves semantic type $semanticType but target field " +
                    "$fieldName has type $fieldType; valueAdapterQualifier is required"
            }
        }
        return ResolvedManagedFieldPolicy(
            fieldName = fieldName,
            columnName = column.name,
            fieldType = fieldType,
            nullable = nullable,
            selection = selection,
            definitionOwner = owned.owner,
            policyKey = definition.key,
            role = definition.role,
            creationInput = definition.creationInput,
            explicitValue = definition.explicitValue,
            lifecycles = definition.lifecycles,
            handlerQualifier = definition.handlerQualifier,
            handlerSlot = definition.handlerSlot,
            semanticValueType = semanticType,
            valueAdapterQualifier = definition.valueAdapterQualifier,
            persistence = definition.persistence,
        )
    }

    private fun validateRoleCardinality(
        entity: EntityModel,
        fields: List<ResolvedManagedFieldPolicy>,
        role: ManagedFieldRole,
    ) {
        val matching = fields.filter { it.role == role }
        require(matching.size <= 1) {
            "entity ${entity.packageName}.${entity.name} has multiple managed fields with role $role: " +
                matching.joinToString { it.fieldName }
        }
    }

    private fun validateSlots(entity: EntityModel, fields: List<ResolvedManagedFieldPolicy>) {
        fields.filter { it.handlerQualifier != null }
            .groupBy { requireNotNull(it.handlerQualifier) }
            .forEach { (qualifier, group) ->
                if (group.size <= 1) return@forEach
                require(group.all { !it.handlerSlot.isNullOrBlank() }) {
                    "entity ${entity.packageName}.${entity.name} qualifier $qualifier is used by multiple fields; " +
                        "every field must declare a nonblank handler slot"
                }
                val slots = group.map { requireNotNull(it.handlerSlot) }
                require(slots.distinct().size == slots.size) {
                    "entity ${entity.packageName}.${entity.name} qualifier $qualifier has duplicate handler slots: $slots"
                }
            }
    }

    private fun validateQualifierKinds(policies: List<ResolvedManagedEntityPolicy>) {
        policies.flatMap { it.fields }
            .filter { it.handlerQualifier != null }
            .groupBy { requireNotNull(it.handlerQualifier) }
            .forEach { (qualifier, fields) ->
                val kinds = fields.map { field ->
                    when {
                        ManagedFieldLifecycle.ENTITY_ADMISSION in field.lifecycles -> "ManagedEntityInitializer"
                        ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT in field.lifecycles -> "JpaPersistenceEnricher"
                        else -> error("resolved handler qualifier without a handler lifecycle: ${field.policyKey}")
                    }
                }.distinct()
                require(kinds.size == 1) {
                    "managed handler qualifier $qualifier is reused across handler kinds: ${kinds.joinToString()}"
                }
            }
    }

    private fun buildWriteSurface(
        entity: EntityModel,
        fields: List<ResolvedManagedFieldPolicy>,
    ): ResolvedWriteSurfacePolicy {
        val managedByName = fields.associateBy { it.fieldName }
        return ResolvedWriteSurfacePolicy(
            createAllowedFields = entity.fields.mapNotNull { field ->
                val policy = managedByName[field.name] ?: return@mapNotNull field.name
                field.name.takeIf { policy.creationInput != ManagedCreationInputPolicy.OMIT }
            },
            updateAllowedFields = entity.fields.mapNotNull { field ->
                val policy = managedByName[field.name] ?: return@mapNotNull field.name
                field.name.takeIf {
                    policy.persistence.update == ManagedValueAuthority.CALLER
                }
            },
        )
    }

    private fun handlerKind(definition: ManagedFieldPolicyDefinition): String? = when {
        ManagedFieldLifecycle.ENTITY_ADMISSION in definition.lifecycles -> "ManagedEntityInitializer"
        ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT in definition.lifecycles -> "JpaPersistenceEnricher"
        else -> null
    }

    private fun sameJvmType(left: String, right: String): Boolean =
        normalizeJvmType(left) == normalizeJvmType(right)

    private fun resolvedTargetFieldType(config: ProjectConfig, field: com.only4.cap4k.plugin.pipeline.api.FieldModel): String {
        val binding = field.typeBinding?.takeIf(String::isNotBlank) ?: return field.type
        if ('.' in binding) return binding
        return config.typeRegistry.entries[binding]?.fqn ?: binding
    }

    private fun normalizeJvmType(type: String): String = when (val normalized = type.removeSuffix("?").trim()) {
        "Byte", "kotlin.Byte", "java.lang.Byte" -> "kotlin.Byte"
        "Short", "kotlin.Short", "java.lang.Short" -> "kotlin.Short"
        "Int", "kotlin.Int", "Integer", "java.lang.Integer" -> "kotlin.Int"
        "Long", "kotlin.Long", "java.lang.Long" -> "kotlin.Long"
        "Float", "kotlin.Float", "java.lang.Float" -> "kotlin.Float"
        "Double", "kotlin.Double", "java.lang.Double" -> "kotlin.Double"
        "Boolean", "kotlin.Boolean", "java.lang.Boolean" -> "kotlin.Boolean"
        "String", "kotlin.String", "java.lang.String" -> "kotlin.String"
        "UUID", "java.util.UUID" -> "java.util.UUID"
        "Instant", "java.time.Instant" -> "java.time.Instant"
        else -> normalized
    }

    private fun describeOwner(owner: ManagedPolicyDefinitionOwner): String = when (owner) {
        ManagedPolicyDefinitionOwner.BuiltIn -> "built-in"
        is ManagedPolicyDefinitionOwner.Extension ->
            "extension ${owner.extensionId}/${owner.contributionId}"
    }

    private fun describeSelection(provenance: ManagedPolicySelectionProvenance): String = when (provenance) {
        is ManagedPolicySelectionProvenance.ExplicitColumnAnnotation -> provenance.sourceLocation
        is ManagedPolicySelectionProvenance.ExactColumnDefault -> provenance.configurationPath
        is ManagedPolicySelectionProvenance.IdentifierDefault -> provenance.configurationPath
    }

    private data class PolicySelection(
        val key: String,
        val provenance: ManagedPolicySelectionProvenance,
    )

    private val HandlerSlotRegex = Regex("[a-z][a-z0-9-]*")
    private val SupportedVersionTypes = setOf(
        "Short", "kotlin.Short", "java.lang.Short",
        "Int", "kotlin.Int", "Integer", "java.lang.Integer",
        "Long", "kotlin.Long", "java.lang.Long",
    )
}
