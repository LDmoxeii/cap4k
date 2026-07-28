package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListFile
import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListItem
import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListRoot
import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListRootRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:owned-entity-list-jpa-runtime;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ]
)
class OwnedEntityListJpaRuntimeTest {
    @Autowired
    private lateinit var repository: OwnedEntityListRootRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `hibernate persists reloads and orphan removes private owned facade backing collections`() {
        val root = OwnedEntityListRoot("root")
        root.items.add(OwnedEntityListItem("item"))
        root.file = OwnedEntityListFile("file")

        val id = repository.saveAndFlush(root).id!!
        entityManager.clear()

        val loaded = repository.findById(id).orElseThrow()

        assertEquals(listOf("item"), loaded.items.map { it.name })
        assertEquals("file", loaded.file?.name)
        assertEquals(1L, rowCount("owned_entity_list_item"))
        assertEquals(1L, rowCount("owned_entity_list_file"))

        loaded.items.remove(loaded.items.single())
        loaded.file = null
        entityManager.flush()
        entityManager.clear()

        assertEquals(0L, rowCount("owned_entity_list_item"))
        assertEquals(0L, rowCount("owned_entity_list_file"))
        val afterRemoval = repository.findById(id).orElseThrow()
        assertEquals(emptyList<String>(), afterRemoval.items.map { it.name })
        assertNull(afterRemoval.file)
    }

    @Test
    fun `owned relation facades assign generated ids before mutation`() {
        val accessor = TimingChildGeneratedOwnIdAccessor()
        val parent = TimingParent(accessor)

        val child = parent.addChild("line")
        assertTrue(child.hasAssignedId())

        val replacement = TimingChild.unassigned("replacement")
        parent.primaryChild = replacement
        assertTrue(replacement.hasAssignedId())
    }

    @Test
    fun `failed generated id allocation preserves owned relation backing values`() {
        val accessor = TimingChildGeneratedOwnIdAccessor()
        val parent = TimingParent(accessor)
        val oldChild = TimingChild.preassigned("old-line", "line-existing")
        val oldPrimaryChild = TimingChild.preassigned("old-primary", "primary-existing")
        parent.children.add(oldChild)
        parent.primaryChild = oldPrimaryChild
        accessor.failAllocation = true

        assertThrows(IllegalStateException::class.java) {
            parent.children.add(TimingChild.unassigned("failed-line"))
        }
        assertEquals(listOf(oldChild), parent.childrenBackingSnapshot())

        assertThrows(IllegalStateException::class.java) {
            parent.primaryChild = TimingChild.unassigned("failed-primary")
        }
        assertEquals(listOf(oldPrimaryChild), parent.primaryBackingSnapshot())
        assertSame(oldPrimaryChild, parent.primaryChild)
    }

    @Test
    fun `owned relation facades preserve preassigned ids`() {
        val accessor = TimingChildGeneratedOwnIdAccessor()
        val parent = TimingParent(accessor)
        val child = TimingChild.preassigned("line", "line-existing")
        val primaryChild = TimingChild.preassigned("primary", "primary-existing")

        parent.children.add(child)
        parent.primaryChild = primaryChild

        assertEquals("line-existing", child.assignedId())
        assertEquals("primary-existing", primaryChild.assignedId())
        assertEquals(0, accessor.nextCalls)
    }

    private fun rowCount(tableName: String): Long =
        jdbcTemplate.queryForObject("""select count(*) from "$tableName"""", Long::class.java)!!

    @SpringBootApplication
    @EntityScan(basePackageClasses = [OwnedEntityListRoot::class])
    @EnableJpaRepositories(basePackageClasses = [OwnedEntityListRootRepository::class])
    class TestApplication
}

private class TimingParent(
    private val accessor: TimingChildGeneratedOwnIdAccessor,
) {
    private val childrenBacking = mutableListOf<TimingChild>()
    private val primaryBacking = mutableListOf<TimingChild>()

    val children: OwnedEntityList<TimingChild>
        get() = OwnedEntityList.of(childrenBacking, TimingChild::class, "TimingParent.children") { child ->
            accessor.assignIfMissing(child)
        }

    var primaryChild: TimingChild?
        get() = OwnedEntityList.of(primaryBacking, TimingChild::class, "TimingParent.primaryChild") { child ->
            accessor.assignIfMissing(child)
        }.singleOrNull()
        set(value) {
            OwnedEntityList.of(primaryBacking, TimingChild::class, "TimingParent.primaryChild") { child ->
                accessor.assignIfMissing(child)
            }.replace(value)
        }

    fun addChild(name: String): TimingChild =
        TimingChild.unassigned(name).also(children::add)

    fun childrenBackingSnapshot(): List<TimingChild> = childrenBacking.toList()

    fun primaryBackingSnapshot(): List<TimingChild> = primaryBacking.toList()
}

private class TimingChild private constructor(
    val name: String,
    initialId: String?,
) {
    private lateinit var id: String

    init {
        if (initialId != null) id = initialId
    }

    fun hasAssignedId(): Boolean = currentId() != null

    fun assignedId(): String = id

    fun currentId(): String? =
        try {
            id
        } catch (_: UninitializedPropertyAccessException) {
            null
        }

    fun assignId(value: String) {
        id = value
    }

    companion object {
        fun unassigned(name: String): TimingChild = TimingChild(name, null)

        fun preassigned(name: String, id: String): TimingChild = TimingChild(name, id)
    }
}

private class TimingChildGeneratedOwnIdAccessor : GeneratedOwnIdAccessor<TimingChild, String> {
    override val entityType = TimingChild::class
    override val label: String = "TimingChild.id"
    var failAllocation: Boolean = false
    var nextCalls: Int = 0
        private set

    override fun current(entity: TimingChild): String? = entity.currentId()

    override fun assign(entity: TimingChild, id: String) = entity.assignId(id)

    override fun next(): String {
        nextCalls++
        check(!failAllocation) { "allocation failed" }
        return "timing-child-$nextCalls"
    }
}
