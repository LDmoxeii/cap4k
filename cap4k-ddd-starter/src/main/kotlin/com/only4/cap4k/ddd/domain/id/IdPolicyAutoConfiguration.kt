package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.DefaultIdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class IdPolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["uuid7IdentifierStrategy"])
    fun uuid7IdentifierStrategy(): IdentifierStrategy =
        Uuid7IdentifierStrategy()

    @Bean
    @ConditionalOnBean(SnowflakeIdGenerator::class)
    @ConditionalOnMissingBean(name = ["snowflakeIdentifierStrategy"])
    fun snowflakeIdentifierStrategy(snowflakeIdGenerator: SnowflakeIdGenerator): IdentifierStrategy =
        SnowflakeIdentifierStrategy(snowflakeIdGenerator)

    @Bean
    @ConditionalOnMissingBean
    fun identifierStrategyRegistry(strategies: List<IdentifierStrategy>): IdentifierStrategyRegistry =
        MapBackedIdentifierStrategyRegistry(strategies)

    @Bean
    @ConditionalOnMissingBean
    fun identifierGenerator(identifierStrategyRegistry: IdentifierStrategyRegistry): IdentifierGenerator =
        DefaultIdentifierGenerator(identifierStrategyRegistry)
}
