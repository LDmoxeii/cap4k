package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListFile
import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListItem
import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListRoot
import com.only4.cap4k.ddd.runtime.ownedentitylistfixture.OwnedEntityListRootRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun rowCount(tableName: String): Long =
        jdbcTemplate.queryForObject("""select count(*) from "$tableName"""", Long::class.java)!!

    @SpringBootApplication
    @EntityScan(basePackageClasses = [OwnedEntityListRoot::class])
    @EnableJpaRepositories(basePackageClasses = [OwnedEntityListRootRepository::class])
    class TestApplication
}
