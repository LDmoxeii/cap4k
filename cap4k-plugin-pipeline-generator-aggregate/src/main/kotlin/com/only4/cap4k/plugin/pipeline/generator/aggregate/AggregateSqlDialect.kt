package com.only4.cap4k.plugin.pipeline.generator.aggregate

import java.util.Locale

internal enum class AggregateSqlDialect {
    MYSQL,
    MARIADB,
    H2,
    H2_MYSQL,
    POSTGRESQL,
}

internal object AggregateSqlDialectResolver {
    private const val SUPPORTED_JDBC_URLS =
        "jdbc:mysql:, jdbc:mariadb:, jdbc:h2:, jdbc:postgresql:"

    fun resolve(jdbcUrl: String): AggregateSqlDialect {
        val normalized = jdbcUrl.trim().lowercase(Locale.ROOT)
        require(normalized.isNotEmpty()) {
            "aggregate soft delete requires a JDBC URL; supported JDBC URLs: $SUPPORTED_JDBC_URLS"
        }

        return when {
            normalized.startsWith("jdbc:mysql:") -> AggregateSqlDialect.MYSQL
            normalized.startsWith("jdbc:mariadb:") -> AggregateSqlDialect.MARIADB
            normalized.startsWith("jdbc:h2:") -> if (hasMysqlMode(normalized)) {
                AggregateSqlDialect.H2_MYSQL
            } else {
                AggregateSqlDialect.H2
            }
            normalized.startsWith("jdbc:postgresql:") -> AggregateSqlDialect.POSTGRESQL
            else -> throw IllegalArgumentException(
                "unsupported JDBC URL for aggregate soft delete: $jdbcUrl; " +
                    "supported JDBC URLs: $SUPPORTED_JDBC_URLS"
            )
        }
    }

    private fun hasMysqlMode(normalizedJdbcUrl: String): Boolean =
        normalizedJdbcUrl
            .split(';')
            .drop(1)
            .any { setting ->
                val parts = setting.split('=', limit = 2)
                parts.size == 2 && parts[0].trim() == "mode" && parts[1].trim() == "mysql"
            }
}
