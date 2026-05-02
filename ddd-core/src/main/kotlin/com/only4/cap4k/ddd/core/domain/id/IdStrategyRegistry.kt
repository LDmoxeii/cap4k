package com.only4.cap4k.ddd.core.domain.id

interface IdStrategyRegistry {
    fun get(name: String): IdStrategy
}

class MapBackedIdStrategyRegistry(
    strategies: Iterable<IdStrategy>
) : IdStrategyRegistry {
    private val strategiesByName: Map<String, IdStrategy> = strategies
        .fold(linkedMapOf<String, IdStrategy>()) { acc, strategy ->
            require(strategy.name.isNotBlank()) { "ID strategy name must not be blank" }
            require(strategy.name !in acc) { "duplicate ID strategy: ${strategy.name}" }
            acc[strategy.name] = strategy
            acc
        }

    override fun get(name: String): IdStrategy =
        strategiesByName[name] ?: throw IllegalArgumentException("unknown ID strategy: $name")
}
