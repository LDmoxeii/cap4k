package com.only4.cap4k.ddd.application.distributed

import com.only4.cap4k.ddd.application.distributed.configure.JdbcLockerProperties
import com.only4.cap4k.ddd.application.distributed.locker.JdbcLocker
import com.only4.cap4k.ddd.core.application.distributed.Locker
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration
@EnableConfigurationProperties(JdbcLockerProperties::class)
class JdbcLockerAutoConfiguration(
    private val jdbcTemplate: JdbcTemplate,
) {
    companion object {
        private const val CONFIG_KEY_4_JPA_SHOW_SQL = "\${spring.jpa.show-sql:\${spring.jpa.showSql:false}}"
    }

    @Bean
    @ConditionalOnMissingBean(Locker::class)
    fun jdbcLocker(
        properties: JdbcLockerProperties,
        @Value(CONFIG_KEY_4_JPA_SHOW_SQL) showSql: Boolean,
    ): JdbcLocker = JdbcLocker(
        jdbcTemplate = jdbcTemplate,
        table = properties.table,
        fieldName = properties.fieldName,
        fieldPwd = properties.fieldPwd,
        fieldLockAt = properties.fieldLockAt,
        fieldUnlockAt = properties.fieldUnlockAt,
        showSql = showSql,
    )
}
