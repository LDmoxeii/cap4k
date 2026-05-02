package com.only4.cap4k.plugin.pipeline.source.db

import java.sql.Types
import java.util.Locale

internal object JdbcTypeMapper {
    fun toKotlinType(sqlType: Int, typeName: String? = null): String = when {
        (sqlType == Types.OTHER || sqlType == Types.BINARY) && typeName.isUuidTypeName() -> "UUID"
        else -> when (sqlType) {
            Types.BIGINT -> "Long"
            Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Int"
            Types.BOOLEAN, Types.BIT -> "Boolean"
            Types.DECIMAL, Types.NUMERIC -> "java.math.BigDecimal"
            Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "java.time.LocalDateTime"
            Types.DATE -> "java.time.LocalDate"
            else -> "String"
        }
    }

    private fun String?.isUuidTypeName(): Boolean =
        this?.trim()?.lowercase(Locale.ROOT) == "uuid"
}
