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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
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

    private class FixedUuidStrategy : IdStrategy {
        override val name: String = "uuid7"
        override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
        override val outputType = UUID::class
        override val preassignable: Boolean = true
        override fun isDefaultValue(value: Any?): Boolean = value == null || value == UUID(0L, 0L)
        override fun next(): Any = UUID(1L, 2L)
    }

    private class RootEntity(
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        var id: UUID = UUID(0L, 0L)
    ) {
        @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
        val children: MutableList<ChildEntity> = mutableListOf()
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

    private class InvalidGeneratedEntity {
        @field:Id
        @field:ApplicationSideId(strategy = "uuid7")
        @field:GeneratedValue
        var id: UUID = UUID(0L, 0L)
    }
}
