package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class GeneratedOwnIdRegistryTest {
    @Test
    fun `empty catalogs create an empty registry`() {
        assertNull(MapBackedGeneratedOwnIdRegistry(emptyList()).accessorFor(Entity::class))
    }

    @Test
    fun `registry flattens catalogs and returns exact accessor`() {
        val first = accessor(Entity::class, "Entity.id")
        val second = accessor(OtherEntity::class, "OtherEntity.id")
        val registry = MapBackedGeneratedOwnIdRegistry(
            listOf(catalog(first), catalog(second))
        )

        assertSame(first, registry.accessorFor(Entity::class))
        assertSame(second, registry.accessorFor(OtherEntity::class))
        assertNull(registry.accessorFor(UnknownEntity::class))
    }

    @Test
    fun `duplicate entity accessors fail immediately with labels`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedGeneratedOwnIdRegistry(
                listOf(
                    catalog(accessor(Entity::class, "first")),
                    catalog(accessor(Entity::class, "second")),
                )
            )
        }

        assertTrue(error.message!!.contains(Entity::class.qualifiedName!!))
        assertTrue(error.message!!.contains("first"))
        assertTrue(error.message!!.contains("second"))
    }

    @Test
    fun `accessor default assignment delegates to shared helper`() {
        val accessor = accessor(Entity::class, "Entity.id")
        val entity = Entity()

        assertEquals("ID-1", accessor.assignIfMissing(entity))
        assertEquals("ID-1", accessor.assignIfMissing(entity))
        assertEquals(1, entity.assignments)
    }

    private fun <E : Any> accessor(type: KClass<E>, label: String) =
        object : GeneratedOwnIdAccessor<E, String> {
            override val entityType: KClass<E> = type
            override val label: String = label
            override fun current(entity: E): String? = (entity as? Entity)?.id ?: (entity as? OtherEntity)?.id
            override fun assign(entity: E, id: String) {
                when (entity) {
                    is Entity -> { entity.id = id; entity.assignments++ }
                    is OtherEntity -> entity.id = id
                }
            }
            override fun next(): String = "ID-1"
        }

    private fun catalog(accessor: GeneratedOwnIdAccessor<*, *>) =
        object : GeneratedOwnIdCatalog {
            override val accessors: List<GeneratedOwnIdAccessor<*, *>> = listOf(accessor)
        }

    private class Entity(var id: String? = null, var assignments: Int = 0)
    private class OtherEntity(var id: String? = null)
    private class UnknownEntity
}
