package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface IdentifierGenerator {
    fun <T : Any> next(strategy: String, type: KClass<T>): T

    fun <T : Any> next(strategy: String, type: Class<T>): T =
        next(strategy, type.kotlin)
}

class DefaultIdentifierGenerator(
    private val strategyRegistry: IdentifierStrategyRegistry
) : IdentifierGenerator {
    override fun <T : Any> next(strategy: String, type: KClass<T>): T {
        val resolved = strategyRegistry.get(strategy)
        require(resolved.supports(type)) {
            "identifier strategy $strategy does not support output type ${type.qualifiedName}"
        }
        return resolved.next(type)
    }
}
