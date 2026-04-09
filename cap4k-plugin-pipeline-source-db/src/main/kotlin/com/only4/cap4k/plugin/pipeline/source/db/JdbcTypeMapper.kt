package com.only4.cap4k.plugin.pipeline.source.db

import java.sql.Types

internal object JdbcTypeMapper {
    fun toKotlinType(sqlType: Int): String = when (sqlType) {
        Types.BIGINT -> "Long"
        Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Int"
        Types.BOOLEAN, Types.BIT -> "Boolean"
        Types.DECIMAL, Types.NUMERIC -> "java.math.BigDecimal"
        Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "java.time.LocalDateTime"
        Types.DATE -> "java.time.LocalDate"
        else -> "String"
    }
}
