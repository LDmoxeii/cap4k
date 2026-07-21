package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateSoftDeletePolicy
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteTombstoneStrategy
import java.util.Locale

internal object AggregateSoftDeletePolicyResolver {
    private const val ActiveValue = "0"

    fun resolve(
        table: DbTableSnapshot,
        resolvedPolicy: AggregateSpecialFieldResolvedPolicy,
    ): AggregateSoftDeletePolicy? {
        val deleted = resolvedPolicy.deleted.takeIf { it.enabled } ?: return null
        val deletedColumnName = requireNotNull(deleted.columnName) {
            "missing soft delete column for table ${table.tableName}"
        }
        val deletedFieldName = requireNotNull(deleted.fieldName) {
            "missing soft delete field for table ${table.tableName}"
        }
        val idColumn = table.columns.firstOrNull {
            it.name.equals(resolvedPolicy.id.columnName, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "missing id column ${resolvedPolicy.id.columnName} for soft delete table ${table.tableName}"
        )
        val deletedColumn = table.columns.firstOrNull {
            it.name.equals(deletedColumnName, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "missing soft delete column $deletedColumnName for table ${table.tableName}"
        )

        validateDeletedColumn(table, deletedColumn)
        validateSelfIdAssignable(table, idColumn, deletedColumn)

        return AggregateSoftDeletePolicy(
            fieldName = deletedFieldName,
            columnName = deletedColumn.name,
            activeValue = ActiveValue,
            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
            activePredicateSql = "${deletedColumn.name} = $ActiveValue",
            deleteAssignmentSql = "${deletedColumn.name} = ${idColumn.name}",
        )
    }

    private fun validateDeletedColumn(table: DbTableSnapshot, deletedColumn: DbColumnSnapshot) {
        require(!deletedColumn.nullable) {
            "soft delete column ${table.tableName}.${deletedColumn.name} must be non-null for active value 0"
        }
        require(isDefaultZero(deletedColumn.defaultValue)) {
            "soft delete column ${table.tableName}.${deletedColumn.name} must declare default 0 for active value 0"
        }
    }

    private fun validateSelfIdAssignable(
        table: DbTableSnapshot,
        idColumn: DbColumnSnapshot,
        deletedColumn: DbColumnSnapshot,
    ) {
        val idCapacity = numericCapacity(idColumn.dbType)
        val deletedCapacity = numericCapacity(deletedColumn.dbType)
        require(idCapacity != null && deletedCapacity != null && deletedCapacity.canStore(idCapacity)) {
            "soft delete column ${table.tableName}.${deletedColumn.name} cannot store id column ${idColumn.name} value for SELF_ID tombstone strategy"
        }
    }

    private fun numericCapacity(dbType: String): NumericCapacity? {
        val match = NUMERIC_DB_TYPE_REGEX.matchEntire(dbType.trim().uppercase(Locale.ROOT)) ?: return null
        val bits = when (match.groupValues[1]) {
            "TINYINT" -> 8
            "SMALLINT" -> 16
            "MEDIUMINT" -> 24
            "INT", "INTEGER" -> 32
            "BIGINT" -> 64
            else -> return null
        }
        return NumericCapacity(bits = bits, unsigned = match.groupValues[2].isNotEmpty())
    }

    private fun isDefaultZero(defaultValue: String?): Boolean {
        var value = defaultValue?.trim() ?: return false
        while (value.length >= 2 && value.first() == '(' && value.last() == ')') {
            value = value.substring(1, value.lastIndex).trim()
        }
        value = value.removeSurrounding("'").removeSurrounding("\"")
        return value == ActiveValue
    }

    private data class NumericCapacity(
        val bits: Int,
        val unsigned: Boolean,
    ) {
        fun canStore(source: NumericCapacity): Boolean = when {
            source.unsigned == unsigned -> bits >= source.bits
            !source.unsigned && unsigned -> false
            else -> bits > source.bits
        }
    }

    private val NUMERIC_DB_TYPE_REGEX = Regex(
        "^(TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT)\\s*(?:\\(\\s*\\d+\\s*\\))?\\s*(UNSIGNED)?\\s*$"
    )
}
