package com.only4.cap4k.ddd.domain.distributed

import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.domain.distributed.configure.SnowflakeProperties
import com.only4.cap4k.ddd.domain.distributed.snowflake.DefaultSnowflakeWorkerIdDispatcher
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeWorkerIdDispatcher
import com.only4.cap4k.ddd.domain.id.SnowflakeIdentifierStrategy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(SnowflakeProperties::class)
class SnowflakeAutoConfiguration {
    companion object {
        private const val CONFIG_KEY_4_JPA_SHOW_SQL = "\${spring.jpa.show-sql:\${spring.jpa.showSql:false}}"
    }

    @Bean
    @ConditionalOnMissingBean(SnowflakeWorkerIdDispatcher::class)
    fun defaultSnowflakeWorkerIdDispatcher(
        properties: SnowflakeProperties,
        jdbcTemplate: JdbcTemplate,
        @Value(CONFIG_KEY_4_JPA_SHOW_SQL) showSql: Boolean,
    ): DefaultSnowflakeWorkerIdDispatcher = DefaultSnowflakeWorkerIdDispatcher(
        jdbcTemplate,
        properties.table,
        properties.fieldDatacenterId,
        properties.fieldWorkerId,
        properties.fieldDispatchTo,
        properties.fieldDispatchAt,
        properties.fieldExpireAt,
        properties.expireMinutes,
        properties.localHostIdentify,
        showSql,
    ).apply { init() }

    @Bean
    @ConditionalOnMissingBean(SnowflakeIdGenerator::class)
    fun snowflakeIdGenerator(
        snowflakeWorkerIdDispatcher: SnowflakeWorkerIdDispatcher,
        properties: SnowflakeProperties,
    ): SnowflakeIdGenerator {
        val workerId = snowflakeWorkerIdDispatcher.acquire(properties.workerId, properties.datacenterId)
        return SnowflakeIdGenerator(
            workerId % (1 shl SnowflakeIdGenerator.WORKER_ID_BITS.toInt()),
            workerId shr 5,
        ).also(::configureHibernateIdentifierGeneratorIfPresent)
    }

    @Bean("snowflakeIdentifierStrategy")
    @ConditionalOnMissingBean(name = ["snowflakeIdentifierStrategy"])
    fun snowflakeIdentifierStrategy(
        snowflakeIdGenerator: SnowflakeIdGenerator,
    ): IdentifierStrategy = SnowflakeIdentifierStrategy(snowflakeIdGenerator)

    @Bean(destroyMethod = "shutdown")
    fun snowflakeWorkerLifecycle(
        snowflakeWorkerIdDispatcher: SnowflakeWorkerIdDispatcher,
        properties: SnowflakeProperties,
    ): SnowflakeWorkerLifecycle = SnowflakeWorkerLifecycle(
        snowflakeWorkerIdDispatcher,
        properties.maxPongContinuousErrorCount,
    )

    private fun configureHibernateIdentifierGeneratorIfPresent(generator: SnowflakeIdGenerator) {
        try {
            Class.forName("org.hibernate.id.IdentifierGenerator")
            Class.forName("com.only4.cap4k.ddd.domain.distributed.SnowflakeIdentifierGenerator")
                .getMethod("configure", SnowflakeIdGenerator::class.java)
                .invoke(null, generator)
        } catch (_: ClassNotFoundException) {
            // Core-only Snowflake usage must not require Hibernate.
        }
    }
}

class SnowflakeWorkerLifecycle(
    private val snowflakeWorkerIdDispatcher: SnowflakeWorkerIdDispatcher,
    private val maxPongContinuousErrorCount: Int,
) {
    private val log = LoggerFactory.getLogger(SnowflakeWorkerLifecycle::class.java)
    private var pongContinuousErrorCount = 0

    @Scheduled(cron = "0 */1 * * * ?")
    fun pong() {
        if (snowflakeWorkerIdDispatcher.pong()) {
            log.debug("SnowflakeWorkerIdDispatcher 心跳上报成功")
            pongContinuousErrorCount = 0
        } else {
            log.error("SnowflakeWorkerIdDispatcher 心跳上报失败")
            pongContinuousErrorCount++
            if (pongContinuousErrorCount > maxPongContinuousErrorCount) {
                snowflakeWorkerIdDispatcher.remind()
            }
        }
    }

    fun shutdown() = snowflakeWorkerIdDispatcher.release()
}
