package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface GeneratedOwnIdRegistry {
    fun accessorFor(entityType: KClass<*>): GeneratedOwnIdAccessor<Any, Any>?
}

class MapBackedGeneratedOwnIdRegistry(
    catalogs: Iterable<GeneratedOwnIdCatalog>,
) : GeneratedOwnIdRegistry {
    private val accessorsByEntityType: Map<KClass<*>, GeneratedOwnIdAccessor<*, *>> =
        catalogs.flatMap { it.accessors }.fold(linkedMapOf()) { result, accessor ->
            val previous = result.putIfAbsent(accessor.entityType, accessor)
            require(previous == null) {
                "duplicate generated own ID accessor for ${accessor.entityType.qualifiedName}: " +
                    "${previous?.label} and ${accessor.label}"
            }
            result
        }

    @Suppress("UNCHECKED_CAST")
    override fun accessorFor(entityType: KClass<*>): GeneratedOwnIdAccessor<Any, Any>? =
        accessorsByEntityType[entityType] as GeneratedOwnIdAccessor<Any, Any>?
}
