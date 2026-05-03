package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ResolvedIdPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedMarkerPolicy
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

        val idControls = resolvedPolicies.map { policy ->
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
        val generatedValueStrategy = idColumn.generatedValueStrategy
        val (idStrategy, idSource) = when {
            generatedValueStrategy != null ->
                AggregateIdPolicyResolver.normalizeStrategy(generatedValueStrategy) to SpecialFieldSource.DB_EXPLICIT
            idColumn.generatedValueDeclared ->
                AggregateIdPolicyResolver.normalizeStrategy(defaultStrategy) to SpecialFieldSource.DB_EXPLICIT
            else ->
                AggregateIdPolicyResolver.normalizeStrategy(defaultStrategy) to SpecialFieldSource.DSL_DEFAULT
        }

        AggregateIdPolicyResolver.validateType(
            config = config,
            entity = entity,
            strategy = idStrategy,
        )
        val idKind = AggregateIdPolicyResolver.resolveKind(idStrategy)
        val deletedPolicy = resolveMarkerPolicy(
            markerName = "deleted",
            explicitColumns = table.columns.filter { it.deleted == true },
            defaultColumnName = config.aggregateSpecialFieldDefaults.deletedDefaultColumn,
            table = table,
            entity = entity,
            fieldByColumnName = fieldByColumnName,
        )
        val versionPolicy = resolveMarkerPolicy(
            markerName = "version",
            explicitColumns = table.columns.filter { it.version == true },
            defaultColumnName = config.aggregateSpecialFieldDefaults.versionDefaultColumn,
            table = table,
            entity = entity,
            fieldByColumnName = fieldByColumnName,
        )

        return AggregateSpecialFieldResolvedPolicy(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = entity.tableName,
            id = ResolvedIdPolicy(
                fieldName = entity.idField.name,
                columnName = idColumn.name,
                strategy = idStrategy,
                kind = idKind,
                writePolicy = if (idKind == AggregateIdPolicyKind.APPLICATION_SIDE) {
                    SpecialFieldWritePolicy.CREATE_ONLY
                } else {
                    SpecialFieldWritePolicy.READ_ONLY
                },
                source = idSource,
            ),
            deleted = deletedPolicy,
            version = versionPolicy,
        )
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
            .filter { (it.generatedValueDeclared || it.generatedValueStrategy != null) && !it.name.equals(idColumn.name, ignoreCase = true) }
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
                source = SpecialFieldSource.DB_EXPLICIT,
            )
        }

        val normalizedDefaultColumn = defaultColumnName.trim()
        if (normalizedDefaultColumn.isBlank()) {
            return ResolvedMarkerPolicy(
                enabled = false,
                source = SpecialFieldSource.NONE,
            )
        }

        val defaultColumn = table.columns.firstOrNull { it.name.equals(normalizedDefaultColumn, ignoreCase = true) }
            ?: return ResolvedMarkerPolicy(
                enabled = false,
                source = SpecialFieldSource.NONE,
            )

        return ResolvedMarkerPolicy(
            enabled = true,
            fieldName = resolveFieldName(fieldByColumnName, entity, defaultColumn),
            columnName = defaultColumn.name,
            source = SpecialFieldSource.DSL_DEFAULT,
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
