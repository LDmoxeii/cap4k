package com.only4.cap4k.ddd.domain.repo.impl

import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicate
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicateSupport
import com.only4.cap4k.ddd.domain.repo.JpaPredicate
import io.mockk.*
import org.junit.jupiter.api.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultRepositorySupervisorTest {

    private lateinit var mockUnitOfWork: UnitOfWork
    private lateinit var mockRepository: Repository<TestEntity>
    private lateinit var supervisor: DefaultRepositorySupervisor

    private data class TestEntity(val id: Long, val name: String)
    private data class AnotherEntity(val id: String, val value: Int)

    private class TestPredicate : Predicate<TestEntity>
    private class AnotherPredicate : Predicate<TestEntity>
    private class TestEntityPredicate : Predicate<AnotherEntity>

    @BeforeEach
    fun setup() {
        mockUnitOfWork = mockk<UnitOfWork>(relaxed = true)
        mockRepository = mockk<Repository<TestEntity>>(relaxed = true)

        every { mockRepository.supportPredicateClass() } returns TestPredicate::class.java

        // 注册实体类型反射器
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(TestPredicate::class.java) {
            TestEntity::class.java
        }
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(AnotherPredicate::class.java) {
            TestEntity::class.java
        }
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(TestEntityPredicate::class.java) {
            AnotherEntity::class.java
        }

        mockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, Repository::class.java)
        } returns TestEntity::class.java

        supervisor = DefaultRepositorySupervisor(
            repositories = listOf(mockRepository),
            unitOfWork = mockUnitOfWork
        )
    }

    @AfterEach
    fun teardown() {
        unmockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
    }

    @Test
    @DisplayName("查找应该从正确的仓储返回实体")
    fun `find should return entities from correct repository`() {
        val predicate = TestPredicate()
        val expectedEntities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))

        every { mockRepository.find(predicate, any<Collection<OrderInfo>>(), false) } returns expectedEntities

        val result = supervisor.find(predicate, emptyList(), false)

        assertEquals(expectedEntities, result)
        verify { mockRepository.find(predicate, any<Collection<OrderInfo>>(), false) }
    }

    @Test
    @DisplayName("查找单个实体应该返回Optional实体")
    fun `findOne should return optional entity`() {
        val predicate = TestPredicate()
        val expectedEntity = TestEntity(1L, "test")
        val optional = Optional.of(expectedEntity)

        every { mockRepository.findOne(predicate, false) } returns optional

        val result = supervisor.findOne(predicate, false)

        assertEquals(optional, result)
        verify { mockRepository.findOne(predicate, false) }
    }

    @Test
    @DisplayName("当实体存在时，使用持久化查找单个实体应该调用工作单元持久化")
    fun `findOne with persist should call unitOfWork persist when entity present`() {
        val predicate = TestPredicate()
        val expectedEntity = TestEntity(1L, "test")
        val optional = Optional.of(expectedEntity)

        every { mockRepository.findOne(predicate, true) } returns optional

        val result = supervisor.findOne(predicate, true)

        assertEquals(optional, result)
        verify { mockRepository.findOne(predicate, true) }
        verify { mockUnitOfWork.persist(expectedEntity) }
    }

    @Test
    @DisplayName("查找分页应该返回分页数据")
    fun `findPage should return page data`() {
        val predicate = TestPredicate()
        val pageParam = PageParam.of(1, 10)
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))
        val pageData = PageData.create(pageParam, 2L, entities)

        every { mockRepository.findPage(predicate, pageParam, false) } returns pageData

        val result = supervisor.findPage(predicate, pageParam, false)

        assertEquals(pageData, result)
        verify { mockRepository.findPage(predicate, pageParam, false) }
    }

    @Test
    @DisplayName("使用持久化查找分页应该为每个实体调用工作单元持久化")
    fun `findPage with persist should call unitOfWork persist for each entity`() {
        val predicate = TestPredicate()
        val pageParam = PageParam.of(1, 10)
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))
        val pageData = PageData.create(pageParam, 2L, entities)

        every { mockRepository.findPage(predicate, pageParam, true) } returns pageData

        val result = supervisor.findPage(predicate, pageParam, true)

        assertEquals(pageData, result)
        verify { mockRepository.findPage(predicate, pageParam, true) }
        entities.forEach { entity ->
            verify { mockUnitOfWork.persist(entity) }
        }
    }

    @Test
    @DisplayName("删除应该查找实体并为每个实体调用工作单元删除")
    fun `remove should find entities and call unitOfWork remove for each`() {
        val predicate = TestPredicate()
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))

        every { mockRepository.find(predicate, any<Collection<OrderInfo>>(), any()) } returns entities

        val result = supervisor.remove(predicate)

        assertEquals(entities, result)
        verify { mockRepository.find(predicate, any<Collection<OrderInfo>>(), any()) }
        entities.forEach { entity ->
            verify { mockUnitOfWork.remove(entity) }
        }
    }

    @Test
    @DisplayName("计数应该返回实体数量")
    fun `count should return entity count`() {
        val predicate = TestPredicate()
        val expectedCount = 42L

        every { mockRepository.count(predicate) } returns expectedCount

        val result = supervisor.count(predicate)

        assertEquals(expectedCount, result)
        verify { mockRepository.count(predicate) }
    }

    @Test
    @DisplayName("当实体存在时存在检查应该返回true")
    fun `exists should return true when entities exist`() {
        val predicate = TestPredicate()

        every { mockRepository.exists(predicate) } returns true

        val result = supervisor.exists(predicate)

        assertTrue(result)
        verify { mockRepository.exists(predicate) }
    }

    @Test
    @DisplayName("当实体不存在时存在检查应该返回false")
    fun `exists should return false when entities do not exist`() {
        val predicate = TestPredicate()

        every { mockRepository.exists(predicate) } returns false

        val result = supervisor.exists(predicate)

        assertFalse(result)
        verify { mockRepository.exists(predicate) }
    }

    @Test
    @DisplayName("当实体不存在仓储时应该抛出异常")
    fun `should throw exception when repository not found for entity`() {
        val predicate = TestEntityPredicate()

        assertThrows<DomainException> {
            supervisor.find(predicate)
        }
    }

    @Test
    @DisplayName("当谓词类型不支持时应该抛出异常")
    fun `should throw exception when predicate type not supported`() {
        class UnsupportedPredicate : Predicate<TestEntity>

        val predicate = UnsupportedPredicate()

        assertThrows<DomainException> {
            supervisor.find(predicate)
        }
    }

    @Test
    @DisplayName("应该正确处理JpaAggregatePredicate")
    fun `should handle JpaAggregatePredicate correctly`() {
        val jpaPredicate = mockk<JpaPredicate<TestEntity>>()
        val aggregatePredicate = mockk<JpaAggregatePredicate<*, TestEntity>>()

        // 使用 mockkObject 代替 mockkStatic
        mockkObject(JpaAggregatePredicateSupport)
        every { JpaAggregatePredicateSupport.getPredicate(aggregatePredicate) } returns jpaPredicate

        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(JpaAggregatePredicate::class.java) {
            TestEntity::class.java
        }

        // 创建支持JpaPredicate的仓储
        val jpaRepository = mockk<Repository<TestEntity>>(relaxed = true)
        every { jpaRepository.supportPredicateClass() } returns JpaPredicate::class.java
        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(jpaRepository, 0, Repository::class.java)
        } returns TestEntity::class.java

        val supervisorWithJpa = DefaultRepositorySupervisor(
            repositories = listOf(jpaRepository),
            unitOfWork = mockUnitOfWork
        )

        every { jpaRepository.find(aggregatePredicate, any<Collection<OrderInfo>>(), any()) } returns listOf(
            TestEntity(
                1L,
                "jpa-test"
            )
        )

        val result = supervisorWithJpa.remove(aggregatePredicate)

        assertEquals(listOf(TestEntity(1L, "jpa-test")), result)
        verify { JpaAggregatePredicateSupport.getPredicate(aggregatePredicate) }
        verify { jpaRepository.find(aggregatePredicate, any<Collection<OrderInfo>>(), any()) }

        unmockkObject(JpaAggregatePredicateSupport)
    }

    @Test
    @DisplayName("注册谓词实体类反射器不应该替换现有反射器")
    fun `registerPredicateEntityClassReflector should not replace existing reflector`() {
        val originalReflector: (Predicate<*>) -> Class<*> = { TestEntity::class.java }
        val newReflector: (Predicate<*>) -> Class<*> = { AnotherEntity::class.java }

        class NewPredicateType : Predicate<TestEntity>

        // 创建支持NewPredicateType的仓储
        val newRepository = mockk<Repository<TestEntity>>(relaxed = true)
        every { newRepository.supportPredicateClass() } returns NewPredicateType::class.java
        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(newRepository, 0, Repository::class.java)
        } returns TestEntity::class.java
        every { newRepository.count(any()) } returns 5L

        // 创建包含新仓储的supervisor
        val supervisorWithNewRepo = DefaultRepositorySupervisor(
            repositories = listOf(mockRepository, newRepository),
            unitOfWork = mockUnitOfWork
        )

        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(
            NewPredicateType::class.java,
            originalReflector
        )
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(NewPredicateType::class.java, newReflector)

        val predicate = NewPredicateType()

        // 原始反射器应该仍然有效，因为putIfAbsent不会替换现有值
        val result = supervisorWithNewRepo.count(predicate)

        // 验证使用了正确的仓储（基于原始反射器返回的TestEntity类型）
        assertEquals(5L, result)
        verify { newRepository.count(predicate) }
    }

    @Test
    @DisplayName("注册仓储实体类反射器应该正确注册反射器")
    fun `registerRepositoryEntityClassReflector should register reflector correctly`() {
        val reflector: (Repository<*>) -> Class<*>? = { TestEntity::class.java }

        class NewRepositoryType : Repository<TestEntity> {
            override fun supportPredicateClass(): Class<*> = TestPredicate::class.java
            override fun find(
                predicate: com.only4.cap4k.ddd.core.domain.repo.Predicate<TestEntity>,
                orders: Collection<OrderInfo>,
                persist: Boolean
            ): List<TestEntity> = emptyList()

            override fun find(
                predicate: com.only4.cap4k.ddd.core.domain.repo.Predicate<TestEntity>,
                pageParam: PageParam,
                persist: Boolean
            ): List<TestEntity> = emptyList()

            override fun findOne(
                predicate: com.only4.cap4k.ddd.core.domain.repo.Predicate<TestEntity>,
                persist: Boolean
            ): Optional<TestEntity> = Optional.empty()

            override fun findFirst(
                predicate: com.only4.cap4k.ddd.core.domain.repo.Predicate<TestEntity>,
                orders: Collection<OrderInfo>,
                persist: Boolean
            ): Optional<TestEntity> = Optional.empty()

            override fun findPage(
                predicate: com.only4.cap4k.ddd.core.domain.repo.Predicate<TestEntity>,
                pageParam: PageParam,
                persist: Boolean
            ): PageData<TestEntity> = PageData.create(PageParam.of(1, 10), 0L, emptyList())

            override fun count(predicate: com.only4.cap4k.ddd.core.domain.repo.Predicate<TestEntity>): Long = 0
            override fun exists(predicate: com.only4.cap4k.ddd.core.domain.repo.Predicate<TestEntity>): Boolean = false
        }

        DefaultRepositorySupervisor.registerRepositoryEntityClassReflector(NewRepositoryType::class.java) { TestEntity::class.java }

        // 验证注册器正确注册了反射器
        val reflectors =
            DefaultRepositorySupervisor::class.java.getDeclaredField("repositoryClass2EntityClassReflector")
        reflectors.isAccessible = true
        val reflectorMap = reflectors.get(null) as Map<*, *>

        assertTrue(reflectorMap.containsKey(NewRepositoryType::class.java))
    }
}
