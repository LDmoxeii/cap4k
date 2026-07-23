package com.only4.cap4k.ddd.core.domain.id

interface IdentifierStrategyRegistry {
    fun get(name: String): IdentifierStrategy
}

class MapBackedIdentifierStrategyRegistry(
    strategies: Iterable<IdentifierStrategy>
) : IdentifierStrategyRegistry {
    private val strategiesByName: Map<String, IdentifierStrategy> = strategies
        .fold(linkedMapOf<String, IdentifierStrategy>()) { acc, strategy ->
            require(strategy.name.isNotBlank()) { "identifier strategy name must not be blank" }
            require(strategy.name !in acc) { "duplicate identifier strategy: ${strategy.name}" }
            acc[strategy.name] = strategy
            acc
        }

    override fun get(name: String): IdentifierStrategy =
        strategiesByName[name] ?: throw IllegalArgumentException("unknown identifier strategy: $name")
}
