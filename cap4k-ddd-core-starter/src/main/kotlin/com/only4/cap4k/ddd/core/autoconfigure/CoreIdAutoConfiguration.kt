package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.domain.id.DefaultIdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.managed.DefaultManagedFieldRegistry
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityInitializer
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldAccessor
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldCatalog
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldRegistry
import com.only4.cap4k.ddd.core.domain.managed.ManagedValueAdapter
import com.only4.cap4k.ddd.core.domain.managed.StandardManagedEntityInitializer
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
    @ConditionalOnMissingBean
    fun standardManagedEntityInitializer(): StandardManagedEntityInitializer =
        StandardManagedEntityInitializer()

    @Bean
    @ConditionalOnMissingBean(ManagedFieldRegistry::class)
    fun managedFieldRegistry(
        catalogs: List<ManagedFieldCatalog>,
        initializers: List<ManagedEntityInitializer>,
        adapters: List<ManagedValueAdapter>,
        accessors: List<ManagedFieldAccessor>,
    ): ManagedFieldRegistry = DefaultManagedFieldRegistry(catalogs, initializers, adapters, accessors)
}
