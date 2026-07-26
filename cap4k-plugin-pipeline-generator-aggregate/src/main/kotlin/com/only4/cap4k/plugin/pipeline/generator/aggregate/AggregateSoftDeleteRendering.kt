package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateIdStorageKind
import com.only4.cap4k.plugin.pipeline.api.AggregateSoftDeletePolicy
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteActiveSentinel

internal data class RenderedAggregateSoftDelete(
    val activeSqlLiteral: String,
    val propertyInitializer: String,
    val whereClause: String,
    val sqlDelete: String,
)

internal object AggregateSoftDeleteRendering {
    private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"

    fun render(
        policy: AggregateSoftDeletePolicy,
        dialect: AggregateSqlDialect,
        tableName: String,
        idColumnName: String,
        versionColumnName: String?,
        deletedKotlinType: String,
    ): RenderedAggregateSoftDelete {
        val activeSqlLiteral = renderActiveSqlLiteral(policy, dialect, deletedKotlinType)
        val propertyInitializer = renderPropertyInitializer(policy, deletedKotlinType)
        val quotedTable = quoteIdentifier(tableName, dialect)
        val quotedIdColumn = quoteIdentifier(idColumnName, dialect)
        val quotedDeletedColumn = quoteIdentifier(policy.columnName, dialect)
        val whereClause = "$quotedDeletedColumn = $activeSqlLiteral"
        val deleteAssignment = "$quotedDeletedColumn = $quotedIdColumn"
        val sqlDelete = if (versionColumnName == null) {
            "update $quotedTable set $deleteAssignment where $quotedIdColumn = ?"
        } else {
            val quotedVersionColumn = quoteIdentifier(versionColumnName, dialect)
            "update $quotedTable set $deleteAssignment where $quotedIdColumn = ? and $quotedVersionColumn = ?"
        }

        return RenderedAggregateSoftDelete(
            activeSqlLiteral = activeSqlLiteral,
            propertyInitializer = propertyInitializer,
            whereClause = whereClause,
            sqlDelete = sqlDelete,
        )
    }

    private fun renderActiveSqlLiteral(
        policy: AggregateSoftDeletePolicy,
        dialect: AggregateSqlDialect,
        deletedKotlinType: String,
    ): String =
        when (policy.storageKind) {
            AggregateIdStorageKind.INTEGRAL -> when (policy.activeSentinel) {
                SoftDeleteActiveSentinel.ZERO -> "0"
                SoftDeleteActiveSentinel.NIL_UUID -> unsupported(policy, deletedKotlinType)
            }
            AggregateIdStorageKind.CHARACTER -> when (policy.activeSentinel) {
                SoftDeleteActiveSentinel.ZERO -> "'0'"
                SoftDeleteActiveSentinel.NIL_UUID -> "'$NIL_UUID'"
            }
            AggregateIdStorageKind.NATIVE_UUID -> when (policy.activeSentinel) {
                SoftDeleteActiveSentinel.ZERO -> unsupported(policy, deletedKotlinType)
                SoftDeleteActiveSentinel.NIL_UUID -> {
                    require(
                        dialect == AggregateSqlDialect.H2 ||
                            dialect == AggregateSqlDialect.H2_MYSQL ||
                            dialect == AggregateSqlDialect.POSTGRESQL
                    ) {
                        "unsupported aggregate soft-delete rendering for storageKind=" +
                            policy.storageKind +
                            ", activeSentinel=" +
                            policy.activeSentinel +
                            ", deletedKotlinType=" +
                            deletedKotlinType +
                            ", dialect=" +
                            dialect
                    }
                    "CAST('$NIL_UUID' AS UUID)"
                }
            }
        }

    private fun renderPropertyInitializer(
        policy: AggregateSoftDeletePolicy,
        deletedKotlinType: String,
    ): String =
        when (policy.storageKind) {
            AggregateIdStorageKind.INTEGRAL -> when (policy.activeSentinel) {
                SoftDeleteActiveSentinel.ZERO -> when (deletedKotlinType) {
                    "Byte", "Short", "Int" -> "0"
                    "Long" -> "0L"
                    else -> unsupported(policy, deletedKotlinType)
                }
                SoftDeleteActiveSentinel.NIL_UUID -> unsupported(policy, deletedKotlinType)
            }
            AggregateIdStorageKind.CHARACTER -> {
                if (deletedKotlinType != "String") {
                    unsupported(policy, deletedKotlinType)
                }
                when (policy.activeSentinel) {
                    SoftDeleteActiveSentinel.ZERO -> "\"0\""
                    SoftDeleteActiveSentinel.NIL_UUID -> "\"$NIL_UUID\""
                }
            }
            AggregateIdStorageKind.NATIVE_UUID -> {
                if (
                    policy.activeSentinel != SoftDeleteActiveSentinel.NIL_UUID ||
                    deletedKotlinType != "UUID"
                ) {
                    unsupported(policy, deletedKotlinType)
                }
                "UUID(0L, 0L)"
            }
        }

    private fun quoteIdentifier(
        value: String,
        dialect: AggregateSqlDialect,
    ): String =
        when (dialect) {
            AggregateSqlDialect.MYSQL,
            AggregateSqlDialect.MARIADB,
            AggregateSqlDialect.H2_MYSQL,
            -> '\u0060' + value.replace("\u0060", "\u0060\u0060") + '\u0060'
            AggregateSqlDialect.H2,
            AggregateSqlDialect.POSTGRESQL,
            -> '"' + value.replace("\"", "\"\"") + '"'
        }

    private fun unsupported(
        policy: AggregateSoftDeletePolicy,
        deletedKotlinType: String,
    ): Nothing =
        throw IllegalArgumentException(
            "unsupported aggregate soft-delete rendering for storageKind=" +
                policy.storageKind +
                ", activeSentinel=" +
                policy.activeSentinel +
                ", deletedKotlinType=" +
                deletedKotlinType
        )
}
