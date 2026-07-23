package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ResolvedIdPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedFieldPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedMarkerPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedWriteSurfacePolicy
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldSource
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldWritePolicy
import java.util.Locale

internal data class AggregateSpecialFieldResolutionResult(
    val resolvedPolicies: List<AggregateSpecialFieldResolvedPolicy>,
    val idControls: List<AggregateIdPolicyControl>,
    val providerControls: List<AggregatePersistenceProviderControl>,
)

internal object AggregateSpecialFieldPolicyResolver {
    private const val DefaultIdStrategy = "uuid7"
    private const val NonStrongIdDefaultStrategy = "identity"

    fun resolve(
        config: ProjectConfig,
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): AggregateSpecialFieldResolutionResult {
        val tableByName = tables.associateBy { it.tableName.lowercase(Locale.ROOT) }
        val entityByKey = entities.associateBy { it.packageName to it.name }
        val resolvedPolicies = entities.mapNotNull { entity ->
            val table = tableByName[entity.tableName.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            resolvePolicy(
                config = config,
                entity = entity,
                table = table,
            )
        }

        val idControls = resolvedPolicies.filter { it.id.source == SpecialFieldSource.DB_EXPLICIT }.map { policy ->
            val entity = requireNotNull(entityByKey[policy.entityPackageName to policy.entityName]) {
                "missing canonical entity metadata for ${policy.entityPackageName}.${policy.entityName}"
            }
            AggregateIdPolicyResolver.toControl(entity, policy.id.strategy)
        }
        val providerControls = AggregatePersistenceProviderInference.infer(
            tables = tables,
            resolvedPolicies = resolvedPolicies,
        )

        return AggregateSpecialFieldResolutionResult(
            resolvedPolicies = resolvedPolicies,
            idControls = idControls,
            providerControls = providerControls,
        )
    }

    private fun resolvePolicy(
        config: ProjectConfig,
        entity: EntityModel,
        table: DbTableSnapshot,
    ): AggregateSpecialFieldResolvedPolicy {
        val fieldByColumnName = entity.fields.associateBy(
            keySelector = { (it.columnName ?: it.name).lowercase(Locale.ROOT) },
            valueTransform = { it.name },
        )
        val idColumn = resolveIdColumn(entity = entity, table = table)
        validateGeneratedValueDeclaration(table = table, idColumn = idColumn)

        val defaultStrategy = config.aggregateSpecialFieldDefaults.idDefaultStrategy
            .trim()
            .takeIf { it.isNotBlank() }
            ?: DefaultIdStrategy
        val generatedValueStrategy = idColumn.idStrategy?.toAggregateStrategy()
        val (idStrategy, idSource) = when {
            generatedValueStrategy != null ->
                AggregateIdPolicyResolver.normalizeStrategy(generatedValueStrategy) to SpecialFieldSource.DB_EXPLICIT
            isGeneratedAggregateRootStrongId(entity) ->
                DefaultIdStrategy to SpecialFieldSource.DSL_DEFAULT
            else ->
                NonStrongIdDefaultStrategy to SpecialFieldSource.DSL_DEFAULT
        }

        if (idSource == SpecialFieldSource.DB_EXPLICIT) {
            AggregateIdPolicyResolver.validateType(
                config = config,
                entity = entity,
                strategy = idStrategy,
            )
        }
        val idKind = AggregateIdPolicyResolver.resolveKind(idStrategy)
        val deletedPolicy = resolveMarkerPolicy(
            markerName = "deleted",
            explicitColumns = table.columns.filter { it.managedRole == DbManagedRole.DELETED },
            defaultColumnName = config.aggregateSpecialFieldDefaults.deletedDefaultColumn,
            table = table,
            entity = entity,
            fieldByColumnName = fieldByColumnName,
        )
        val versionPolicy = resolveMarkerPolicy(
            markerName = "version",
            explicitColumns = table.columns.filter { it.managedRole == DbManagedRole.VERSION },
            defaultColumnName = config.aggregateSpecialFieldDefaults.versionDefaultColumn,
            table = table,
            entity = entity,
            fieldByColumnName = fieldByColumnName,
        )
        val idPolicy = ResolvedIdPolicy(
            fieldName = entity.idField.name,
            columnName = idColumn.name,
            strategy = idStrategy,
            kind = idKind,
            writePolicy = idWritePolicy(idKind),
            source = idSource,
        )
        val managedFields = resolveManagedFields(
            config = config,
            entity = entity,
            table = table,
            fieldByColumnName = fieldByColumnName,
            id = idPolicy,
            deleted = deletedPolicy,
            version = versionPolicy,
        )

        return AggregateSpecialFieldResolvedPolicy(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = entity.tableName,
            id = idPolicy,
            deleted = deletedPolicy,
            version = versionPolicy,
            managedFields = managedFields,
            writeSurface = buildWriteSurface(entity, managedFields),
        )
    }

    private fun DbIdStrategy.toAggregateStrategy(): String = when (this) {
        DbIdStrategy.DB_IDENTITY -> "identity"
        DbIdStrategy.UUID7 -> "uuid7"
    }

    private fun idWritePolicy(kind: AggregateIdPolicyKind): SpecialFieldWritePolicy =
        if (kind == AggregateIdPolicyKind.APPLICATION_SIDE) {
            SpecialFieldWritePolicy.CREATE_ONLY
        } else {
            SpecialFieldWritePolicy.READ_ONLY
        }

    private fun isGeneratedAggregateRootStrongId(entity: EntityModel): Boolean =
        entity.aggregateRoot && entity.idField.type == "${entity.name}Id"

    private fun markerWritePolicy(markerName: String, enabled: Boolean): SpecialFieldWritePolicy = when {
        !enabled -> SpecialFieldWritePolicy.READ_WRITE
        markerName == "deleted" -> SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY
        else -> SpecialFieldWritePolicy.READ_ONLY
    }

    private fun resolveIdColumn(entity: EntityModel, table: DbTableSnapshot): DbColumnSnapshot {
        val idColumnName = (entity.idField.columnName ?: entity.idField.name).lowercase(Locale.ROOT)
        return requireNotNull(table.columns.firstOrNull { it.name.lowercase(Locale.ROOT) == idColumnName }) {
            "missing id column ${entity.idField.name} on table ${table.tableName}"
        }
    }

    private fun validateGeneratedValueDeclaration(
        table: DbTableSnapshot,
        idColumn: DbColumnSnapshot,
    ) {
        table.columns
            .filter { it.idStrategy != null && !it.name.equals(idColumn.name, ignoreCase = true) }
            .firstOrNull()
            ?.let { nonIdColumn ->
                throw IllegalArgumentException(
                    "generated value annotation can only be declared on id column: ${table.tableName}.${nonIdColumn.name}"
                )
            }
    }

    private fun resolveMarkerPolicy(
        markerName: String,
        explicitColumns: List<DbColumnSnapshot>,
        defaultColumnName: String,
        table: DbTableSnapshot,
        entity: EntityModel,
        fieldByColumnName: Map<String, String>,
    ): ResolvedMarkerPolicy {
        require(explicitColumns.size <= 1) {
            "multiple explicit $markerName columns found for table ${table.tableName}"
        }

        val explicitColumn = explicitColumns.singleOrNull()
        if (explicitColumn != null) {
            return ResolvedMarkerPolicy(
                enabled = true,
                fieldName = resolveFieldName(fieldByColumnName, entity, explicitColumn),
                columnName = explicitColumn.name,
                writePolicy = markerWritePolicy(markerName, enabled = true),
                source = SpecialFieldSource.DB_EXPLICIT,
            )
        }

        val normalizedDefaultColumn = defaultColumnName.trim()
        if (normalizedDefaultColumn.isBlank()) {
            return ResolvedMarkerPolicy(
                enabled = false,
                writePolicy = markerWritePolicy(markerName, enabled = false),
                source = SpecialFieldSource.NONE,
            )
        }

        val defaultColumn = table.columns.firstOrNull { it.name.equals(normalizedDefaultColumn, ignoreCase = true) }
            ?: return ResolvedMarkerPolicy(
                enabled = false,
                writePolicy = markerWritePolicy(markerName, enabled = false),
                source = SpecialFieldSource.NONE,
            )

        return ResolvedMarkerPolicy(
            enabled = true,
            fieldName = resolveFieldName(fieldByColumnName, entity, defaultColumn),
            columnName = defaultColumn.name,
            writePolicy = markerWritePolicy(markerName, enabled = true),
            source = SpecialFieldSource.DSL_DEFAULT,
        )
    }

    private fun resolveManagedFields(
        config: ProjectConfig,
        entity: EntityModel,
        table: DbTableSnapshot,
        fieldByColumnName: Map<String, String>,
        id: ResolvedIdPolicy,
        deleted: ResolvedMarkerPolicy,
        version: ResolvedMarkerPolicy,
    ): List<ResolvedManagedFieldPolicy> {
        val managedByColumnName = linkedMapOf<String, ResolvedManagedFieldPolicy>()
        val protectedByColumnName = linkedMapOf<String, ResolvedManagedFieldPolicy>()

        fun registerProtected(policy: ResolvedManagedFieldPolicy) {
            val key = policy.columnName.lowercase(Locale.ROOT)
            protectedByColumnName[key] = policy
            managedByColumnName[key] = policy
        }

        registerProtected(
            ResolvedManagedFieldPolicy(
                fieldName = id.fieldName,
                columnName = id.columnName,
                writePolicy = id.writePolicy,
                source = id.source,
            )
        )
        registerMarkerManagedField(deleted, DbManagedRole.DELETED)?.let(::registerProtected)
        registerMarkerManagedField(version, DbManagedRole.VERSION)?.let(::registerProtected)

        val columnsByName = table.columns.associateBy { it.name.lowercase(Locale.ROOT) }
        config.aggregateSpecialFieldDefaults.managedDefaultColumns
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { configuredColumnName ->
                val columnKey = configuredColumnName.lowercase(Locale.ROOT)
                val column = columnsByName[columnKey] ?: return@forEach
                val fieldName = fieldByColumnName[columnKey] ?: return@forEach
                managedByColumnName.putIfAbsent(
                    columnKey,
                    ResolvedManagedFieldPolicy(
                        fieldName = fieldName,
                        columnName = column.name,
                        writePolicy = SpecialFieldWritePolicy.READ_ONLY,
                        source = SpecialFieldSource.DSL_DEFAULT,
                    )
                )
            }

        table.columns.forEach { column ->
            val columnKey = column.name.lowercase(Locale.ROOT)
            val managedRole = column.managedRole ?: return@forEach
            if (columnKey in protectedByColumnName) {
                return@forEach
            }
            if (managedRole != DbManagedRole.SYSTEM && managedRole != DbManagedRole.SCOPE) {
                return@forEach
            }
            val fieldName = fieldByColumnName[columnKey] ?: return@forEach
            managedByColumnName[columnKey] = ResolvedManagedFieldPolicy(
                fieldName = fieldName,
                columnName = column.name,
                writePolicy = SpecialFieldWritePolicy.READ_ONLY,
                source = SpecialFieldSource.DB_EXPLICIT,
                managedRole = managedRole,
            )
        }

        return managedByColumnName.values.toList()
    }

    private fun registerMarkerManagedField(policy: ResolvedMarkerPolicy, managedRole: DbManagedRole): ResolvedManagedFieldPolicy? {
        if (!policy.enabled) {
            return null
        }
        return ResolvedManagedFieldPolicy(
            fieldName = requireNotNull(policy.fieldName),
            columnName = requireNotNull(policy.columnName),
            writePolicy = policy.writePolicy,
            source = policy.source,
            managedRole = managedRole,
        )
    }

    private fun buildWriteSurface(
        entity: EntityModel,
        managedFields: List<ResolvedManagedFieldPolicy>,
    ): ResolvedWriteSurfacePolicy {
        val createDenied = managedFields
            .asSequence()
            .filter {
                it.writePolicy == SpecialFieldWritePolicy.READ_ONLY ||
                    it.writePolicy == SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY
            }
            .map { it.fieldName }
            .toSet()
        val updateDenied = managedFields
            .asSequence()
            .filter { it.writePolicy != SpecialFieldWritePolicy.READ_WRITE }
            .map { it.fieldName }
            .toSet()

        return ResolvedWriteSurfacePolicy(
            createAllowedFields = entity.fields.map { it.name }.filterNot { it in createDenied },
            updateAllowedFields = entity.fields.map { it.name }.filterNot { it in updateDenied },
        )
    }

    private fun resolveFieldName(
        fieldByColumnName: Map<String, String>,
        entity: EntityModel,
        column: DbColumnSnapshot,
    ): String {
        return requireNotNull(fieldByColumnName[column.name.lowercase(Locale.ROOT)]) {
            "missing canonical entity field identity for ${entity.name}.${column.name}"
        }
    }
}
