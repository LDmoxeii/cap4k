package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdCatalog
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
import jakarta.persistence.CascadeType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JpaGeneratedStrongIdSupportTest {

    @BeforeEach
    fun resetAccessors() {
        RootAccessor.nextCalls = 0
        ChildAccessor.nextCalls = 0
    }

    @Test
    fun `CREATE completes root and reachable child through registered accessors`() {
        val root = Root().also { it.children += Child() }
        val support = support(RootAccessor, ChildAccessor)

        support.completeCreate(root, JpaGeneratedOwnedRelationTraversal())

        assertEquals("ROOT-1", root.id)
        assertEquals("CHILD-1", root.children.single().id)
    }

    @Test
    fun `CREATE preserves preassigned ids`() {
        val root = Root().also { it.id = "ROOT-99" }

        support(RootAccessor).completeCreate(root, JpaGeneratedOwnedRelationTraversal())

        assertEquals("ROOT-99", root.id)
        assertEquals(0, RootAccessor.nextCalls)
    }

    @Test
    fun `unregistered entity is ignored without reflection`() {
        val entity = UnregisteredEntity()

        support().completeCreate(entity, JpaGeneratedOwnedRelationTraversal())

        assertEquals(null, entity.id)
    }

    @Test
    fun `EXISTING preserves observed root and child IDs`() {
        val observedChild = Child(id = "CHILD-99")
        val root = Root(id = "ROOT-99").also { it.children += observedChild }
        val baseline = baseline(root, observedChild)

        support(RootAccessor, ChildAccessor).completeExisting(
            root,
            JpaGeneratedOwnedRelationTraversal(),
            baseline,
        )

        assertEquals("ROOT-99", root.id)
        assertEquals("CHILD-99", observedChild.id)
    }

    @Test
    fun `EXISTING assigns id to a reachable unobserved child`() {
        val observedChild = Child(id = "CHILD-99")
        val newChild = Child()
        val root = Root(id = "ROOT-99").also {
            it.children += observedChild
            it.children += newChild
        }
        val baseline = baseline(root, observedChild)

        support(RootAccessor, ChildAccessor).completeExisting(
            root,
            JpaGeneratedOwnedRelationTraversal(),
            baseline,
        )

        assertEquals("CHILD-1", newChild.id)
    }

    @Test
    fun `EXISTING rejects a changed observed child ID before persistence`() {
        val changedChild = Child(id = "CHILD-CHANGED")
        val root = Root(id = "ROOT-99").also { it.children += changedChild }
        val baseline = baseline(root, changedChild, observedChildId = "CHILD-99")

        assertThrows(IllegalStateException::class.java) {
            support(RootAccessor, ChildAccessor).completeExisting(
                root,
                JpaGeneratedOwnedRelationTraversal(),
                baseline,
            )
        }
    }

    private fun support(vararg accessors: GeneratedOwnIdAccessor<*, *>): JpaGeneratedStrongIdSupport =
        JpaGeneratedStrongIdSupport(
            MapBackedGeneratedOwnIdRegistry(listOf(catalog(*accessors)))
        )

    private fun catalog(vararg accessors: GeneratedOwnIdAccessor<*, *>): GeneratedOwnIdCatalog =
        object : GeneratedOwnIdCatalog {
            override val accessors: List<GeneratedOwnIdAccessor<*, *>> = accessors.toList()
        }

    private fun baseline(
        root: Root,
        child: Child,
        observedChildId: String = requireNotNull(child.id),
    ): JpaRepositoryObservationBaseline =
        JpaRepositoryObservationBaseline().also {
            it.record(
                root,
                listOf(
                    JpaObservedEntity(
                        root,
                        JpaObservedIdentity(Root::class.java, requireNotNull(root.id)),
                    ),
                    JpaObservedEntity(child, JpaObservedIdentity(Child::class.java, observedChildId)),
                ),
            )
        }

    private object RootAccessor : GeneratedOwnIdAccessor<Root, String> {
        override val entityType = Root::class
        override val label = "Root.id"
        var nextCalls = 0

        override fun current(entity: Root): String? = entity.id

        override fun assign(entity: Root, id: String) {
            entity.id = id
        }

        override fun next(): String = "ROOT-${++nextCalls}"
    }

    private object ChildAccessor : GeneratedOwnIdAccessor<Child, String> {
        override val entityType = Child::class
        override val label = "Child.id"
        var nextCalls = 0

        override fun current(entity: Child): String? = entity.id

        override fun assign(entity: Child, id: String) {
            entity.id = id
        }

        override fun next(): String = "CHILD-${++nextCalls}"
    }

    private class Root(var id: String? = null) {
        @field:OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
        @field:JoinColumn(name = "root_id")
        val children: MutableList<Child> = mutableListOf()
    }

    private class Child(var id: String? = null)

    private class UnregisteredEntity(var id: String? = null)
}
