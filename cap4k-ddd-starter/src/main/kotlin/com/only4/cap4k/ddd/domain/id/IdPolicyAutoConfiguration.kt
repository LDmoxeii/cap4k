package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.DefaultIdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class IdPolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun idStrategyRegistry(
        snowflakeIdGenerator: ObjectProvider<SnowflakeIdGenerator>
    ): IdStrategyRegistry {
        val strategies = mutableListOf<IdStrategy>(Uuid7IdStrategy())
        snowflakeIdGenerator.ifAvailable { strategies += SnowflakeLongIdStrategy(it) }
        return MapBackedIdStrategyRegistry(strategies)
    }

    @Bean
    @ConditionalOnMissingBean
    fun idAllocator(idStrategyRegistry: IdStrategyRegistry): IdAllocator =
        DefaultIdAllocator(idStrategyRegistry)
}
