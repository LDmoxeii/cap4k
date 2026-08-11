package com.only4.cap4k.ddd.runtime.repository

import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository
import com.only4.cap4k.ddd.domain.repo.JpaPredicate
import com.only4.cap4k.ddd.domain.repo.schema.Field
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.ddd.domain.repo.schema.or
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:runtime-repository-contract;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ],
)
@Import(RuntimeQueryRepository::class)
internal class AbstractJpaRepositoryH2RuntimeTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val repository: RuntimeQueryRepository,
) {
    private lateinit var rows: List<RuntimeQueryEntity>

    @BeforeEach
    fun seed() {
        rows = listOf(
            RuntimeQueryEntity("alpha", 1, active = true, note = null),
            RuntimeQueryEntity("alpha", 2, active = false, note = "x", tags = setOf("featured")),
            RuntimeQueryEntity("beta", 3, active = true, note = "x", tags = setOf("news")),
            RuntimeQueryEntity("gamma", 4, active = false, note = null),
            RuntimeQueryEntity("delta", 5, active = true, note = "z", tags = setOf("featured", "news")),
            RuntimeQueryEntity("epsilon", 6, active = false, note = "z"),
        )
        rows.forEach(entityManager::persist)
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `ID and Specification paths share stable sorting pages and total count`() {
        val ids = rows.map { it.id!! }.reversed() + rows.first().id!! + 999_999L
        val byIds = JpaPredicate.byIds(RuntimeQueryEntity::class.java, ids)
        val all = JpaPredicate.bySpecification(
            RuntimeQueryEntity::class.java,
            Specification { _, _, criteriaBuilder -> criteriaBuilder.conjunction() },
        )
        val firstPage = PageParam.of(1, 4).orderByAsc("name")
        val secondPage = PageParam.of(2, 4).orderByAsc("name")
        val beyond = PageParam.of(3, 4).orderByAsc("name")

        val idFirst = repository.findPage(byIds, firstPage)
        val specFirst = repository.findPage(all, firstPage)
        val idSecond = repository.findPage(byIds, secondPage)
        val specSecond = repository.findPage(all, secondPage)
        val idBeyond = repository.findPage(byIds, beyond)
        val specBeyond = repository.findPage(all, beyond)

        assertPageParity(idFirst, specFirst, pageNum = 1, pageSize = 4, totalCount = 6)
        assertPageParity(idSecond, specSecond, pageNum = 2, pageSize = 4, totalCount = 6)
        assertEquals(2, idSecond.list.size)
        assertPageParity(idBeyond, specBeyond, pageNum = 3, pageSize = 4, totalCount = 6)
        assertEquals(emptyList<RuntimeQueryEntity>(), idBeyond.list)

        val alphas = idFirst.list.filter { it.name == "alpha" }
        assertEquals(2, alphas.size)
        assertEquals(alphas.mapNotNull { it.id }.sorted(), alphas.mapNotNull { it.id })
    }

    @Test
    fun `ID pages preserve empty and offset-limit semantics`() {
        val ids = rows.map { it.id!! }.reversed()
        val pageParam = PageParam.of(2, 2).orderByAsc("rank")

        val items = repository.find(JpaPredicate.byIds(RuntimeQueryEntity::class.java, ids), pageParam)
        val empty = repository.findPage(
            JpaPredicate.byIds(RuntimeQueryEntity::class.java, emptyList<Long>()),
            PageParam.of(4, 3).orderByAsc("rank"),
        )

        assertEquals(listOf(3, 4), items.map { it.rank })
        assertEquals(4, empty.pageNum)
        assertEquals(3, empty.pageSize)
        assertEquals(0, empty.totalCount)
        assertEquals(emptyList<RuntimeQueryEntity>(), empty.list)
    }

    @Test
    fun `comparison and nullable predicate families execute through generated Specification surface`() {
        val comparison = query { root, _, criteriaBuilder ->
            val rank = Field(root.get<Int>("rank"), criteriaBuilder)
            and(
                rank.greaterThan(1),
                rank.greaterThanOrEqualTo(2),
                rank.lessThan(6),
                rank.lessThanOrEqualTo(5),
                rank.between(2, 5),
                rank.`in`(2, 3, 4, 5),
                rank.notIn(3),
            )
        }
        val nullAndBoolean = query { root, _, criteriaBuilder ->
            val active = Field(root.get<Boolean>("active"), criteriaBuilder)
            val note = Field(root.get<String>("note"), criteriaBuilder)
            or(
                and(active.isTrue(), note.isNull()),
                and(active.isFalse(), note.isNotNull()),
            )
        }
        val strings = query { root, _, criteriaBuilder ->
            val name = Field(root.get<String>("name"), criteriaBuilder)
            and(
                name.notEqual("gamma"),
                name like "%a%",
                name notLike "beta",
                name.`like?`("%a%"),
                name.`notLike?`("beta"),
                name.`in?`(listOf("alpha", "delta", "gamma")),
                name.`notIn?`(listOf("gamma")),
            )
        }

        assertEquals(listOf(2, 4, 5), comparison.map { it.rank })
        assertEquals(listOf(1, 2, 6), nullAndBoolean.map { it.rank })
        assertEquals(listOf(1, 2, 5), strings.map { it.rank })
    }

    @Test
    fun `collection and nested composition predicates execute together`() {
        val emptyTags = query { root, _, criteriaBuilder ->
            Field<Collection<String>>(root.get("tags"), criteriaBuilder).isEmpty()
        }
        val nonEmptyTags = query { root, _, criteriaBuilder ->
            Field<Collection<String>>(root.get("tags"), criteriaBuilder).isNotEmpty()
        }
        val nested = query { root, _, criteriaBuilder ->
            val name = Field(root.get<String>("name"), criteriaBuilder)
            val rank = Field(root.get<Int>("rank"), criteriaBuilder)
            val note = Field(root.get<String>("note"), criteriaBuilder)
            and(
                or(name.equal("alpha"), name.equal("beta")),
                rank gt 1,
                note.isNull().not(),
            )
        }

        assertEquals(listOf(1, 4, 6), emptyTags.map { it.rank })
        assertEquals(listOf(2, 3, 5), nonEmptyTags.map { it.rank })
        assertEquals(listOf(2, 3), nested.map { it.rank })
    }

    private fun query(specification: Specification<RuntimeQueryEntity>): List<RuntimeQueryEntity> =
        repository.find(
            JpaPredicate.bySpecification(RuntimeQueryEntity::class.java, specification),
            listOf(com.only4.cap4k.ddd.core.share.OrderInfo.asc("rank")),
        )

    private fun assertPageParity(
        idPage: PageData<RuntimeQueryEntity>,
        specificationPage: PageData<RuntimeQueryEntity>,
        pageNum: Int,
        pageSize: Int,
        totalCount: Long,
    ) {
        assertEquals(pageNum, idPage.pageNum)
        assertEquals(pageSize, idPage.pageSize)
        assertEquals(totalCount, idPage.totalCount)
        assertEquals(specificationPage.pageNum, idPage.pageNum)
        assertEquals(specificationPage.pageSize, idPage.pageSize)
        assertEquals(specificationPage.totalCount, idPage.totalCount)
        assertEquals(specificationPage.list.map { it.id }, idPage.list.map { it.id })
    }
}

@Repository
internal open class RuntimeQueryRepository(
    entityManager: EntityManager,
) : AbstractJpaRepository<RuntimeQueryEntity, Long>(
    RuntimeQueryEntity::class.java,
    entityManager,
)

@Entity
@Table(name = "runtime_query_entity")
internal open class RuntimeQueryEntity protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
        protected set

    @Column(nullable = false)
    open lateinit var name: String
        protected set

    @Column(nullable = false)
    open var rank: Int = 0
        protected set

    @Column(nullable = false)
    open var active: Boolean = false
        protected set

    @Column
    open var note: String? = null
        protected set

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "runtime_query_entity_tag",
        joinColumns = [JoinColumn(name = "entity_id")],
    )
    @Column(name = "tag", nullable = false)
    open var tags: MutableSet<String> = linkedSetOf()
        protected set

    constructor(
        name: String,
        rank: Int,
        active: Boolean,
        note: String?,
        tags: Set<String> = emptySet(),
    ) : this() {
        this.name = name
        this.rank = rank
        this.active = active
        this.note = note
        this.tags.addAll(tags)
    }
}

@SpringBootApplication
@EntityScan(basePackageClasses = [RuntimeQueryEntity::class])
internal open class RuntimeRepositoryTestApplication
