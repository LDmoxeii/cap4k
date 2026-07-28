package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.domain.id.DefaultIdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdCatalog
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class CoreIdAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = ["uuid7IdentifierStrategy"])
    fun uuid7IdentifierStrategy(): IdentifierStrategy = Uuid7IdentifierStrategy()

    @Bean
    @ConditionalOnMissingBean
    fun identifierStrategyRegistry(strategies: List<IdentifierStrategy>): IdentifierStrategyRegistry =
        MapBackedIdentifierStrategyRegistry(strategies)

    @Bean
    @ConditionalOnMissingBean
    fun identifierGenerator(registry: IdentifierStrategyRegistry): IdentifierGenerator =
        DefaultIdentifierGenerator(registry)

    @Bean
    @ConditionalOnMissingBean(GeneratedOwnIdRegistry::class)
    fun generatedOwnIdRegistry(catalogs: List<GeneratedOwnIdCatalog>): GeneratedOwnIdRegistry =
        MapBackedGeneratedOwnIdRegistry(catalogs)
}
