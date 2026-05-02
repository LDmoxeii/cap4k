package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import java.util.Locale

private val UNIQUE_IDENTIFIER_SPLIT_REGEX = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")

internal data class AggregateUniqueConstraintSelection(
    val physicalName: String,
    val normalizedName: String,
    val suffix: String,
    val requestProps: List<FieldModel>,
    val filteredControlFields: List<FieldModel>,
    val idType: String,
    val excludeIdParamName: String,
    val queryTypeName: String,
    val queryHandlerTypeName: String,
    val validatorTypeName: String,
)

internal object AggregateUniqueConstraintPlanning {
    fun from(
        entity: EntityModel,
        providerControl: AggregatePersistenceProviderControl? = null,
    ): List<AggregateUniqueConstraintSelection> {
        val selections = entity.uniqueConstraints.map { constraint ->
            val resolvedFields = selectConstraintFields(entity, constraint.columns)
            val controlColumnNames = controlColumnNames(entity, providerControl)
            val filteredControlFields = resolvedFields.filter { field ->
                (field.columnName ?: field.name).lowercase(Locale.ROOT) in controlColumnNames
            }
            val businessFields = resolvedFields.filterNot { field ->
                (field.columnName ?: field.name).lowercase(Locale.ROOT) in controlColumnNames
            }
            val normalizedName = normalizeUniqueName(entity.tableName, constraint.physicalName)
            require(businessFields.isNotEmpty()) {
                "Unique constraint ${constraint.physicalName} on entity ${entity.name} has no business fields after filtering control fields."
            }
            val suffix = resolveSuffix(
                normalizedName = normalizedName,
                businessFields = businessFields,
            )

            AggregateUniqueConstraintSelection(
                physicalName = constraint.physicalName,
                normalizedName = normalizedName,
                suffix = suffix,
                requestProps = businessFields,
                filteredControlFields = filteredControlFields,
                idType = entity.idField.type,
                excludeIdParamName = "exclude${entity.name}Id",
                queryTypeName = "Unique${entity.name}${suffix}Qry",
                queryHandlerTypeName = "Unique${entity.name}${suffix}QryHandler",
                validatorTypeName = "Unique${entity.name}${suffix}",
            )
        }
        validateUniqueSelections(entity, selections)
        return selections
    }

    fun from(model: CanonicalModel): List<Pair<EntityModel, List<AggregateUniqueConstraintSelection>>> {
        val providerControlsByEntity = model.aggregatePersistenceProviderControls.associateBy {
            it.entityPackageName to it.entityName
        }
        return model.entities
            .map { entity ->
                entity to from(
                    entity = entity,
                    providerControl = providerControlsByEntity[entity.packageName to entity.name],
                )
            }
            .filter { (_, selections) -> selections.isNotEmpty() }
    }

    private fun selectConstraintFields(entity: EntityModel, columns: List<String>): List<FieldModel> {
        val fieldsByName = entity.fields.associateBy { field ->
            field.name.lowercase(Locale.ROOT)
        }
        val fieldsByNormalizedName = entity.fields.associateBy { field ->
            uniqueLowerCamel(field.name).lowercase(Locale.ROOT)
        }
        val fieldsByColumnName = entity.fields
            .filter { field -> field.columnName != null }
            .associateBy { field -> requireNotNull(field.columnName).lowercase(Locale.ROOT) }
        val resolvedColumns = columns.map { column ->
            val columnKey = column.lowercase(Locale.ROOT)
            column to (
                fieldsByName[columnKey]
                    ?: fieldsByNormalizedName[uniqueLowerCamel(column).lowercase(Locale.ROOT)]
                    ?: fieldsByColumnName[columnKey]
                )
        }
        val missingColumns = resolvedColumns.filter { (_, field) -> field == null }.map { (column, _) -> column }
        require(missingColumns.isEmpty()) {
            "Unique constraint columns not found in entity ${entity.name}: ${missingColumns.joinToString(", ")}"
        }
        return resolvedColumns.map { (_, field) ->
            requireNotNull(field)
        }
    }

    private fun controlColumnNames(
        entity: EntityModel,
        providerControl: AggregatePersistenceProviderControl?,
    ): Set<String> = buildSet {
        providerControl?.softDeleteColumn
            ?.lowercase(Locale.ROOT)
            ?.let(::add)

        providerControl?.versionFieldName?.let { versionFieldName ->
            entity.fields
                .firstOrNull { field -> field.name.equals(versionFieldName, ignoreCase = true) }
                ?.let { field -> (field.columnName ?: field.name).lowercase(Locale.ROOT) }
                ?.let(::add)
        }
    }

    private fun normalizeUniqueName(tableName: String, physicalName: String): String {
        val trimmed = physicalName.trim()
        val tablePrefix = "${tableName}_"
        val withoutTablePrefix = if (trimmed.startsWith(tablePrefix, ignoreCase = true)) {
            trimmed.substring(tablePrefix.length)
        } else {
            trimmed
        }
        return normalizeH2BackingIndexName(withoutTablePrefix)
    }

    private fun normalizeH2BackingIndexName(normalizedName: String): String {
        // Intentionally narrow: "index" can be a valid business fragment such as uk_index_email.
        val h2BackingIndex = Regex("^(.+)_INDEX_[A-Z0-9]$").find(normalizedName) ?: return normalizedName
        val candidate = h2BackingIndex.groupValues[1]
        return if (isExplicitUniqueName(candidate)) candidate else normalizedName
    }

    private fun resolveSuffix(
        normalizedName: String,
        businessFields: List<FieldModel>,
    ): String {
        if (normalizedName.equals("uk", ignoreCase = true)) {
            return ""
        }

        val explicitFragment = EXPLICIT_UNIQUE_FRAGMENT_REGEX.find(normalizedName)
        if (explicitFragment != null) {
            return uniqueUpperCamel(explicitFragment.groupValues[1])
        }

        return businessFields.joinToString(separator = "") { field ->
            uniqueUpperCamel(field.name)
        }
    }

    private fun validateUniqueSelections(
        entity: EntityModel,
        selections: List<AggregateUniqueConstraintSelection>,
    ) {
        val emptySuffixCount = selections.count { it.suffix.isEmpty() }
        require(emptySuffixCount <= 1) {
            "Entity ${entity.name} has multiple aggregate unique constraints resolving to Unique${entity.name}."
        }
        requireNoDuplicateNames(entity, "validator", selections.map { it.validatorTypeName })
        requireNoDuplicateNames(entity, "query", selections.map { it.queryTypeName })
        requireNoDuplicateNames(entity, "query handler", selections.map { it.queryHandlerTypeName })
    }

    private fun requireNoDuplicateNames(
        entity: EntityModel,
        label: String,
        names: List<String>,
    ) {
        val duplicates = names
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "Duplicate aggregate unique $label names for entity ${entity.name}: ${duplicates.joinToString(", ")}"
        }
    }

    private fun isExplicitUniqueName(normalizedName: String): Boolean =
        normalizedName.equals("uk", ignoreCase = true) || EXPLICIT_UNIQUE_FRAGMENT_REGEX.matches(normalizedName)
}

private val EXPLICIT_UNIQUE_FRAGMENT_REGEX = Regex("^uk(?:_v)?_(.+)$", RegexOption.IGNORE_CASE)

private fun uniqueUpperCamel(value: String): String =
    uniqueLowerCamel(value).replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
    }

private fun uniqueLowerCamel(value: String): String {
    val parts = value.trim()
        .split(UNIQUE_IDENTIFIER_SPLIT_REGEX)
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) return value

    val head = parts.first().lowercase(Locale.ROOT)
    val tail = parts.drop(1).joinToString(separator = "") { token ->
        token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
    return head + tail
}

internal fun aggregateTableSegment(tableName: String): String = tableName.lowercase(Locale.ROOT)
