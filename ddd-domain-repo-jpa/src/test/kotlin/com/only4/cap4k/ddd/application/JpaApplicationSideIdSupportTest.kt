package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
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

class JpaApplicationSideIdSupportTest {

    private val support = JpaApplicationSideIdSupport(
        MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy()))
    )

    @Test
    fun `assigns default root id`() {
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
    fun `rejects database side strategy`() {
        val databaseSideSupport = JpaApplicationSideIdSupport(
            MapBackedIdStrategyRegistry(listOf(DatabaseSideUuidStrategy()))
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            databaseSideSupport.assignMissingIds(RootEntity())
        }

        assertEquals("ID strategy uuid7 is not application-side", error.message)
    }

    @Test
    fun `rejects strategy output type mismatch before assignment`() {
        val mismatchSupport = JpaApplicationSideIdSupport(
            MapBackedIdStrategyRegistry(listOf(StringIdStrategy()))
        )
        val entity = StringStrategyEntity()

        val error = assertThrows(IllegalArgumentException::class.java) {
            mismatchSupport.assignMissingIds(entity)
        }

        assertEquals(
            "ID strategy string-id output type java.lang.String cannot be assigned to field com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest\$StringStrategyEntity.id of type java.util.UUID",
            error.message
        )
        assertEquals(UUID(0L, 0L), entity.id)
    }

    @Test
    fun `rejects null generated id before assignment`() {
        val nullSupport = JpaApplicationSideIdSupport(
            MapBackedIdStrategyRegistry(listOf(nullUuidStrategy()))
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
            MapBackedIdStrategyRegistry(listOf(DefaultUuidStrategy()))
        )
        val entity = DefaultStrategyEntity()

        val error = assertThrows(IllegalStateException::class.java) {
            defaultSupport.assignMissingIds(entity)
        }

        assertEquals("ID strategy default-uuid generated a default application-side ID value", error.message)
        assertEquals(UUID(0L, 0L), entity.id)
    }

    private class FixedUuidStrategy : IdStrategy {
        override val name: String = "uuid7"
        override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
        override val outputType = UUID::class
        override val preassignable: Boolean = true
        override fun isDefaultValue(value: Any?): Boolean = value == null || value == UUID(0L, 0L)
        override fun next(): Any = UUID(1L, 2L)
    }

    private class DatabaseSideUuidStrategy : IdStrategy {
        override val name: String = "uuid7"
        override val kind: IdGenerationKind = IdGenerationKind.DATABASE_SIDE
        override val outputType = UUID::class
        override val preassignable: Boolean = false
        override fun isDefaultValue(value: Any?): Boolean = value == null || value == UUID(0L, 0L)
        override fun next(): Any = UUID(1L, 2L)
    }

    private class StringIdStrategy : IdStrategy {
        override val name: String = "string-id"
        override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
        override val outputType = String::class
        override val preassignable: Boolean = true
        override fun isDefaultValue(value: Any?): Boolean = value == null || value == UUID(0L, 0L)
        override fun next(): Any = "not-a-uuid"
    }

    private class DefaultUuidStrategy : IdStrategy {
        override val name: String = "default-uuid"
        override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
        override val outputType = UUID::class
        override val preassignable: Boolean = true
        override fun isDefaultValue(value: Any?): Boolean = value == null || value == UUID(0L, 0L)
        override fun next(): Any = UUID(0L, 0L)
    }

    private fun nullUuidStrategy(): IdStrategy =
        Proxy.newProxyInstance(
            IdStrategy::class.java.classLoader,
            arrayOf(IdStrategy::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getName" -> "null-uuid"
                "getKind" -> IdGenerationKind.APPLICATION_SIDE
                "getOutputType" -> UUID::class
                "getPreassignable" -> true
                "isDefaultValue" -> args?.single() == null || args.single() == UUID(0L, 0L)
                "next" -> null
                else -> error("unexpected method: ${method.name}")
            }
        } as IdStrategy

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
        var id: UUID = UUID(0L, 0L)
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
