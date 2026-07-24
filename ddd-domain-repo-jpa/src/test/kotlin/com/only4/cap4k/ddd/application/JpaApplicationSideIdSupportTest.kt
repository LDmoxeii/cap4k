package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import jakarta.persistence.CascadeType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.reflect.KClass

class JpaApplicationSideIdSupportTest {

    private val support = JpaApplicationSideIdSupport(
        MapBackedIdentifierStrategyRegistry(listOf(FixedUuidStrategy()))
    )

    @Test
    fun `assigns default root id`() {
        val root = RootEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.id)
    }

    @Test
    fun `application side compatibility support does not require strong id companion`() {
        val root = RootEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.id)
    }

    @Test
    fun `keeps preassigned root id`() {
        val assigned = UUID(9L, 9L)
        val root = RootEntity(id = assigned)

        support.assignMissingIds(root)

        assertEquals(assigned, root.id)
    }

    @Test
    fun `traverses owned one to many children`() {
        val root = RootEntity()
        root.children += ChildEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.children.single().id)
    }

    @Test
    fun `assigns owned child ids without changing root during update prep`() {
        val rootId = UUID.fromString("018f0000-0000-7000-8000-000000000099")
        val root = RootEntity(id = rootId)
        root.children += ChildEntity()

        support.assignMissingIdsToOwnedRelations(root)

        assertEquals(rootId, root.id)
        assertEquals(UUID(1L, 2L), root.children.single().id)
    }

    @Test
    fun `does not traverse many to one reverse navigation`() {
        val child = ChildWithParent()
        child.parent = RootEntity()

        support.assignMissingIds(child)

        assertEquals(UUID(1L, 2L), child.id)
        assertEquals(UUID(0L, 0L), child.parent!!.id)
    }

    @Test
    fun `rejects application side id combined with generated value`() {
        val error = assertThrows(IllegalStateException::class.java) {
            support.assignMissingIds(InvalidGeneratedEntity())
        }

        assertEquals(
            "Application-side ID field com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest\$InvalidGeneratedEntity.id must not also use @GeneratedValue",
            error.message
        )
    }

    @Test
    fun `finds no application side id on provider generated entity`() {
        assertNull(support.findApplicationSideId(ProviderGeneratedEntity()))
    }

    @Test
    fun `assigns getter targeted application side id`() {
        val entity = GetterAnnotatedEntity()

        support.assignMissingIds(entity)

        assertEquals(UUID(1L, 2L), entity.id)
    }

    @Test
    fun `rejects getter generated value combined with application side id`() {
        val error = assertThrows(IllegalStateException::class.java) {
            support.assignMissingIds(InvalidGetterGeneratedEntity())
        }

        assertEquals(
            "Application-side ID field com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest\$InvalidGetterGeneratedEntity.id must not also use @GeneratedValue",
            error.message
        )
    }

    @Test
    fun `does not traverse inverse one to many relation`() {
        val root = InverseOneToManyRoot()
        root.children += ChildEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.id)
        assertEquals(UUID(0L, 0L), root.children.single().id)
    }

    @Test
    fun `does not traverse non cascaded one to many relation`() {
        val root = NonCascadedOneToManyRoot()
        root.children += ChildEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.id)
        assertEquals(UUID(0L, 0L), root.children.single().id)
    }

    @Test
    fun `traverses owned one to one child`() {
        val root = OneToOneRoot()
        root.child = ChildEntity()

        support.assignMissingIds(root)

        assertEquals(UUID(1L, 2L), root.child!!.id)
    }

    @Test
    fun `rejects strategy without entity id preassignment capability`() {
        val nonPreassigningSupport = JpaApplicationSideIdSupport(
            MapBackedIdentifierStrategyRegistry(listOf(NonPreassigningUuidStrategy()))
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            nonPreassigningSupport.assignMissingIds(RootEntity())
        }

        assertEquals("identifier strategy uuid7 does not support entity ID preassignment", error.message)
    }

    @Test
    fun `rejects strategy output type mismatch before assignment`() {
        val mismatchSupport = JpaApplicationSideIdSupport(
            MapBackedIdentifierStrategyRegistry(listOf(StringIdStrategy()))
        )
        val entity = StringStrategyEntity()

        val error = assertThrows(IllegalArgumentException::class.java) {
            mismatchSupport.assignMissingIds(entity)
        }

        assertEquals(
            "identifier strategy string-id does not support output type java.util.UUID for field " +
                "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest\$StringStrategyEntity.id",
            error.message
        )
        assertNull(entity.id)
    }

    @Test
    fun `rejects null generated id before assignment`() {
        val nullSupport = JpaApplicationSideIdSupport(
            MapBackedIdentifierStrategyRegistry(listOf(nullUuidStrategy()))
        )
        val entity = NullStrategyEntity()

        val error = assertThrows(IllegalStateException::class.java) {
            nullSupport.assignMissingIds(entity)
        }

        assertEquals("ID strategy null-uuid generated null for application-side ID", error.message)
        assertEquals(UUID(0L, 0L), entity.id)
    }

    @Test
    fun `rejects default generated id before assignment`() {
        val defaultSupport = JpaApplicationSideIdSupport(
            MapBackedIdentifierStrategyRegistry(listOf(DefaultUuidStrategy()))
        )
        val entity = DefaultStrategyEntity()

        val error = assertThrows(IllegalStateException::class.java) {
            defaultSupport.assignMissingIds(entity)
        }

        assertEquals("ID strategy default-uuid generated a default application-side ID value", error.message)
        assertEquals(UUID(0L, 0L), entity.id)
    }

    private class FixedUuidStrategy : IdentifierStrategy {
        override val name: String = "uuid7"
        override val capabilities: Set<IdentifierCapability> = setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == UUID::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return UUID(1L, 2L) as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == UUID(0L, 0L)
    }

    private class NonPreassigningUuidStrategy : IdentifierStrategy {
        override val name: String = "uuid7"
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = type == UUID::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return UUID(1L, 2L) as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == UUID(0L, 0L)
    }

    private class StringIdStrategy : IdentifierStrategy {
        override val name: String = "string-id"
        override val capabilities: Set<IdentifierCapability> = setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == String::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return "not-a-uuid" as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == ""
    }

    private class DefaultUuidStrategy : IdentifierStrategy {
        override val name: String = "default-uuid"
        override val capabilities: Set<IdentifierCapability> = setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
        override fun supports(type: KClass<*>): Boolean = type == UUID::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return UUID(0L, 0L) as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == UUID(0L, 0L)
    }

    private fun nullUuidStrategy(): IdentifierStrategy =
        Proxy.newProxyInstance(
            IdentifierStrategy::class.java.classLoader,
            arrayOf(IdentifierStrategy::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getName" -> "null-uuid"
                "getCapabilities" -> setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)
                "supports" -> args?.single() == UUID::class
                "isDefaultValue" -> args?.get(0) == null || args?.get(0) == UUID(0L, 0L)
                "next" -> null
                else -> error("unexpected method: ${method.name}")
            }
        } as IdentifierStrategy

    private class RootEntity(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
        val children: MutableList<ChildEntity> = mutableListOf()
    }

    private class InverseOneToManyRoot(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @OneToMany(mappedBy = "parent", cascade = [CascadeType.PERSIST])
        val children: MutableList<ChildEntity> = mutableListOf()
    }

    private class NonCascadedOneToManyRoot(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @OneToMany
        val children: MutableList<ChildEntity> = mutableListOf()
    }

    private class OneToOneRoot(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @OneToOne(cascade = [CascadeType.PERSIST])
        var child: ChildEntity? = null
    }

    private class ChildEntity(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    )

    private class ChildWithParent(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @ManyToOne
        var parent: RootEntity? = null
    }

    private class ProviderGeneratedEntity {
        @Id
        @GeneratedValue
        var id: Long = 0L
    }

    private class GetterAnnotatedEntity {
        @get:Id
        @get:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    }

    private class InvalidGeneratedEntity {
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        @field:GeneratedValue
        var id: UUID = UUID(0L, 0L)
    }

    private class InvalidGetterGeneratedEntity {
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        @get:GeneratedValue
        var id: UUID = UUID(0L, 0L)
    }

    private class StringStrategyEntity {
        @field:Id
        @field:ApplicationSideId(strategy = "string-id")
        var id: UUID? = null
    }

    private class NullStrategyEntity {
        @field:Id
        @field:ApplicationSideId(strategy = "null-uuid")
        var id: UUID = UUID(0L, 0L)
    }

    private class DefaultStrategyEntity {
        @field:Id
        @field:ApplicationSideId(strategy = "default-uuid")
        var id: UUID = UUID(0L, 0L)
    }
}
