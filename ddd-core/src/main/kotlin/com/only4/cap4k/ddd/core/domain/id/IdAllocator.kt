package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass
import kotlin.jvm.javaObjectType

interface IdAllocator {
    fun <T : Any> next(strategy: String, type: KClass<T>): T
}

class DefaultIdAllocator(
    private val strategyRegistry: IdStrategyRegistry
) : IdAllocator {
    override fun <T : Any> next(strategy: String, type: KClass<T>): T {
        val resolved = strategyRegistry.get(strategy)
        require(resolved.kind == IdGenerationKind.APPLICATION_SIDE) {
            "ID strategy $strategy is not application-side"
        }
        require(type.javaObjectType.isAssignableFrom(resolved.outputType.javaObjectType)) {
            "ID strategy $strategy produces ${resolved.outputType.javaObjectType.name}, not ${type.qualifiedName}"
        }

        @Suppress("UNCHECKED_CAST")
        return resolved.next() as T
    }
}
