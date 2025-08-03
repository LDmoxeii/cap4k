package com.only4.cap4k.ddd.domain.distributed

import com.only4.cap4k.ddd.domain.distributed.configure.SnowflakeProperties
import com.only4.cap4k.ddd.domain.distributed.snowflake.DefaultSnowflakeWorkerIdDispatcher
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeWorkerIdDispatcher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Snowflake自动配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Service
@Configuration
@ConditionalOnProperty(
    prefix = "cap4k.ddd.distributed.idgenerator.snowflake",
    name = ["enable"],
    havingValue = "true",
    matchIfMissing = true
)
class SnowflakeAutoConfiguration(
    private val properties: SnowflakeProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(SnowflakeAutoConfiguration::class.java)
        private const val CONFIG_KEY_4_JPA_SHOW_SQL = "\${spring.jpa.show-sql:\${spring.jpa.showSql:false}}"
    }

    private var pongContinuousErrorCount = 0
    private lateinit var snowflakeWorkerIdDispatcher: SnowflakeWorkerIdDispatcher

    fun shutdown() {
        snowflakeWorkerIdDispatcher.release()
    }

    @Scheduled(cron = "0 */1 * * * ?")
    fun pong() {
        if (snowflakeWorkerIdDispatcher.pong()) {
            log.debug("SnowflakeWorkerIdDispatcher 心跳上报成功")
            pongContinuousErrorCount = 0
        } else {
            log.error("SnowflakeWorkerIdDispatcher 心跳上报失败")
            pongContinuousErrorCount++
            if (pongContinuousErrorCount > properties.maxPongContinuousErrorCount) {
                snowflakeWorkerIdDispatcher.remind()
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(SnowflakeIdGenerator::class)
    fun snowflakeIdGenerator(
        snowflakeWorkerIdDispatcher: SnowflakeWorkerIdDispatcher,
        properties: SnowflakeProperties
    ): SnowflakeIdGenerator {
        val workerId = snowflakeWorkerIdDispatcher.acquire(properties.workerId, properties.datacenterId)
        val snowflakeIdGenerator = SnowflakeIdGenerator(
            workerId % (1 shl SnowflakeIdGenerator.WORKER_ID_BITS.toInt()),
            workerId shr 5
        )
        SnowflakeIdentifierGenerator.configure(snowflakeIdGenerator)
        return snowflakeIdGenerator
    }

    @Bean
    @ConditionalOnMissingBean(SnowflakeWorkerIdDispatcher::class)
    fun defaultSnowflakeWorkerIdDispatcher(
        properties: SnowflakeProperties,
        jdbcTemplate: JdbcTemplate,
        @Value(CONFIG_KEY_4_JPA_SHOW_SQL) showSql: Boolean
    ): DefaultSnowflakeWorkerIdDispatcher {
        return DefaultSnowflakeWorkerIdDispatcher(
            jdbcTemplate,
            properties.table,
            properties.fieldDatacenterId,
            properties.fieldWorkerId,
            properties.fieldDispatchTo,
            properties.fieldDispatchAt,
            properties.fieldExpireAt,
            properties.expireMinutes,
            properties.localHostIdentify,
            showSql
        ).apply {
            init()
            snowflakeWorkerIdDispatcher = this
        }
    }
}
