package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.*

class AbstractJpaRepositoryTest {

    private lateinit var mockJpaSpecificationExecutor: JpaSpecificationExecutor<TestEntity>
    private lateinit var mockJpaRepository: JpaRepository<TestEntity, Long>
    private lateinit var mockEntityManager: EntityManager
    private lateinit var repository: AbstractJpaRepository<TestEntity, Long>

    private data class TestEntity(val id: Long, val name: String)

    @BeforeEach
    fun setup() {
        mockJpaSpecificationExecutor = mockk<JpaSpecificationExecutor<TestEntity>>(relaxed = true)
        mockJpaRepository = mockk<JpaRepository<TestEntity, Long>>(relaxed = true)
        mockEntityManager = mockk<EntityManager>(relaxed = true)

        repository = AbstractJpaRepository(mockJpaSpecificationExecutor, mockJpaRepository)
        repository.entityManager = mockEntityManager

        // Mock static methods
        mockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
        every {
            com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass(any(), 0, AbstractJpaRepository::class.java)
        } returns TestEntity::class.java

        mockkObject(JpaPredicateSupport)
        mockkStatic("com.only4.cap4k.ddd.domain.repo.JpaPageUtils")
        mockkStatic("com.only4.cap4k.ddd.domain.repo.JpaSortUtils")
    }

    @AfterEach
    fun teardown() {
        unmockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
        unmockkObject(JpaPredicateSupport)
        unmockkStatic("com.only4.cap4k.ddd.domain.repo.JpaPageUtils")
        unmockkStatic("com.only4.cap4k.ddd.domain.repo.JpaSortUtils")
    }

    @Test
    @DisplayName("初始化应该正确注册反射器")
    fun `init should register reflectors correctly`() {
        val mockSupervisor = mockkObject(DefaultRepositorySupervisor)

        repository.init()

        verify {
            DefaultRepositorySupervisor.registerPredicateEntityClassReflector(
                JpaPredicate::class.java,
                any()
            )
        }
        verify {
            DefaultRepositorySupervisor.registerRepositoryEntityClassReflector(
                AbstractJpaRepository::class.java,
                any()
            )
        }

        unmockkObject(DefaultRepositorySupervisor)
    }

    @Test
    @DisplayName("支持谓词类应该返回JpaPredicate类")
    fun `supportPredicateClass should return JpaPredicate class`() {
        val result = repository.supportPredicateClass()
        assertEquals(JpaPredicate::class.java, result)
    }

    @Test
    @DisplayName("使用ID谓词查找单个实体应该返回实体并默认持久化")
    fun `findOne with ID predicate should return entity and persist by default`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val entity = TestEntity(1L, "test")
        val optional = Optional.of(entity)

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns 1L
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null
        every { mockJpaRepository.findById(1L) } returns optional

        val result = repository.findOne(predicate, true)

        assertEquals(optional, result)
        verify { mockJpaRepository.findById(1L) }
        verify(exactly = 0) { mockEntityManager.detach(any()) }
    }

    @Test
    @DisplayName("使用ID谓词查找单个实体，当持久化为false时应该分离实体")
    fun `findOne with ID predicate should detach entity when persist is false`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val entity = TestEntity(1L, "test")
        val optional = Optional.of(entity)

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns 1L
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null
        every { mockJpaRepository.findById(1L) } returns optional

        val result = repository.findOne(predicate, false)

        assertEquals(optional, result)
        verify { mockJpaRepository.findById(1L) }
        verify { mockEntityManager.detach(entity) }
    }

    @Test
    @DisplayName("使用Specification谓词查找单个实体应该使用规范执行器")
    fun `findOne with Specification predicate should use specification executor`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val specification = mockk<Specification<TestEntity>>()
        val entity = TestEntity(1L, "test")
        val optional = Optional.of(entity)

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { mockJpaSpecificationExecutor.findOne(specification) } returns optional

        val result = repository.findOne(predicate, true)

        assertEquals(optional, result)
        verify { mockJpaSpecificationExecutor.findOne(specification) }
    }

    @Test
    @DisplayName("使用未知谓词查找单个实体应该返回空Optional")
    fun `findOne with unknown predicate should return empty optional`() {
        val predicate = mockk<Predicate<TestEntity>>()

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null

        val result = repository.findOne(predicate, true)

        assertTrue(result.isEmpty)
    }

    @Test
    @DisplayName("使用ID谓词查找第一个实体应该返回实体")
    fun `findFirst with ID predicate should return entity`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val orders = listOf(OrderInfo.asc("name"))
        val entity = TestEntity(1L, "test")
        val optional = Optional.of(entity)

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns 1L
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null
        every { mockJpaRepository.findById(1L) } returns optional

        val result = repository.findFirst(predicate, orders, true)

        assertEquals(optional, result)
        verify { mockJpaRepository.findById(1L) }
    }

    @Test
    @DisplayName("使用Specification谓词查找第一个实体应该使用带分页的规范执行器")
    fun `findFirst with Specification predicate should use specification executor with pagination`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val orders = listOf(OrderInfo.desc("name"))
        val specification = mockk<Specification<TestEntity>>()
        val entity = TestEntity(1L, "test")
        val page = PageImpl(listOf(entity))

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { toSpringData(any<PageParam>()) } returns PageRequest.of(
            0,
            1,
            Sort.by(Sort.Direction.DESC, "name")
        )
        every { mockJpaSpecificationExecutor.findAll(specification, any<PageRequest>()) } returns page

        val result = repository.findFirst(predicate, orders, true)

        assertTrue(result.isPresent)
        assertEquals(entity, result.get())
        verify { mockJpaSpecificationExecutor.findAll(specification, any<PageRequest>()) }
    }

    @Test
    @DisplayName("使用多个ID谓词查找分页应该正确处理分页")
    fun `findPage with IDs predicate should handle pagination correctly`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val pageParam = PageParam.of(2, 2)
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        val entities = listOf(
            TestEntity(1L, "test1"),
            TestEntity(2L, "test2"),
            TestEntity(3L, "test3"),
            TestEntity(4L, "test4"),
            TestEntity(5L, "test5")
        )

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns ids
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null
        every { mockJpaRepository.findAllById(ids) } returns entities

        val result = repository.findPage(predicate, pageParam, true)

        assertEquals(2, result.list.size)
        assertEquals(TestEntity(3L, "test3"), result.list[0])
        assertEquals(TestEntity(4L, "test4"), result.list[1])
    }

    @Test
    @DisplayName("使用空ID列表查找分页应该返回空页面")
    fun `findPage with empty IDs should return empty page`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val pageParam = PageParam.of(1, 10)
        val emptyIds = emptyList<Long>()

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns emptyIds
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null

        val result = repository.findPage(predicate, pageParam, true)

        assertTrue(result.list.isEmpty())
        assertEquals(0L, result.list.size.toLong())
    }

    @Test
    @DisplayName("使用Specification谓词查找分页应该使用规范执行器")
    fun `findPage with Specification predicate should use specification executor`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val pageParam = PageParam.of(1, 10)
        val specification = mockk<Specification<TestEntity>>()
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))
        val springPage = PageImpl(entities, PageRequest.of(0, 10), 2L)
        val expectedPageData = PageData.create(pageParam, 2L, entities)

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { toSpringData(pageParam) } returns PageRequest.of(0, 10)
        every { mockJpaSpecificationExecutor.findAll(specification, any<PageRequest>()) } returns springPage
        every { fromSpringData(springPage) } returns expectedPageData

        val result = repository.findPage(predicate, pageParam, true)

        assertEquals(expectedPageData, result)
        verify { mockJpaSpecificationExecutor.findAll(specification, any<PageRequest>()) }
    }

    @Test
    @DisplayName("查找分页时持久化为false应该分离所有实体")
    fun `findPage with persist false should detach all entities`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val pageParam = PageParam.of(1, 10)
        val specification = mockk<Specification<TestEntity>>()
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))
        val springPage = PageImpl(entities, PageRequest.of(0, 10), 2L)
        val expectedPageData = PageData.create(pageParam, 2L, entities)

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { toSpringData(pageParam) } returns PageRequest.of(0, 10)
        every { mockJpaSpecificationExecutor.findAll(specification, any<PageRequest>()) } returns springPage
        every { fromSpringData(springPage) } returns expectedPageData

        val result = repository.findPage(predicate, pageParam, false)

        assertEquals(expectedPageData, result)
        entities.forEach { entity ->
            verify { mockEntityManager.detach(entity) }
        }
    }

    @Test
    @DisplayName("使用排序查找多个ID谓词应该正确处理")
    fun `find with orders should handle IDs predicate correctly`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val orders = listOf(OrderInfo.asc("name"))
        val ids = listOf(1L, 2L)
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns ids
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null
        every { mockJpaRepository.findAllById(ids) } returns entities

        val result = repository.find(predicate, orders, true)

        assertEquals(entities, result)
        verify { mockJpaRepository.findAllById(ids) }
    }

    @Test
    @DisplayName("使用排序查找空ID列表应该正确处理")
    fun `find with orders should handle empty IDs correctly`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val orders = listOf(OrderInfo.asc("name"))
        val emptyIds = emptyList<Long>()

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns emptyIds
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null

        val result = repository.find(predicate, orders, true)

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("使用排序查找Specification谓词应该正确处理")
    fun `find with orders should handle Specification predicate correctly`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val orders = listOf(OrderInfo.desc("name"))
        val specification = mockk<Specification<TestEntity>>()
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))
        val sort = Sort.by(Sort.Direction.DESC, "name")

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { toSpringData(orders) } returns sort
        every { mockJpaSpecificationExecutor.findAll(specification, sort) } returns entities

        val result = repository.find(predicate, orders, true)

        assertEquals(entities, result)
        verify { mockJpaSpecificationExecutor.findAll(specification, sort) }
    }

    @Test
    @DisplayName("使用排序查找且持久化为false应该分离实体")
    fun `find with orders and persist false should detach entities`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val orders = listOf(OrderInfo.asc("name"))
        val specification = mockk<Specification<TestEntity>>()
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))
        val sort = Sort.by(Sort.Direction.ASC, "name")

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { toSpringData(orders) } returns sort
        every { mockJpaSpecificationExecutor.findAll(specification, sort) } returns entities

        val result = repository.find(predicate, orders, false)

        assertEquals(entities, result)
        entities.forEach { entity ->
            verify { mockEntityManager.detach(entity) }
        }
    }

    @Test
    @DisplayName("使用分页参数查找多个ID谓词应该正确处理")
    fun `find with pageParam should handle IDs predicate correctly`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val pageParam = PageParam.of(1, 10)
        val ids = listOf(1L, 2L)
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns ids
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns null
        every { mockJpaRepository.findAllById(ids) } returns entities

        val result = repository.find(predicate, pageParam, true)

        assertEquals(entities, result)
        verify { mockJpaRepository.findAllById(ids) }
    }

    @Test
    @DisplayName("使用分页参数查找Specification谓词应该正确处理")
    fun `find with pageParam should handle Specification predicate correctly`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val pageParam = PageParam.of(1, 10)
        val specification = mockk<Specification<TestEntity>>()
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))
        val springPage = PageImpl(entities, PageRequest.of(0, 10), 2L)

        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { toSpringData(pageParam) } returns PageRequest.of(0, 10)
        every { mockJpaSpecificationExecutor.findAll(specification, any<PageRequest>()) } returns springPage

        val result = repository.find(predicate, pageParam, true)

        assertEquals(entities, result)
        verify { mockJpaSpecificationExecutor.findAll(specification, any<PageRequest>()) }
    }

    @Test
    @DisplayName("使用ID谓词计数，如果实体存在应该返回1")
    fun `count with ID predicate should return 1 if entity exists`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val entity = TestEntity(1L, "test")

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns 1L
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { mockJpaRepository.findById(1L) } returns Optional.of(entity)

        val result = repository.count(predicate)

        assertEquals(1L, result)
        verify { mockJpaRepository.findById(1L) }
    }

    @Test
    @DisplayName("使用ID谓词计数，如果实体不存在应该返回0")
    fun `count with ID predicate should return 0 if entity does not exist`() {
        val predicate = mockk<Predicate<TestEntity>>()

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns 1L
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { mockJpaRepository.findById(1L) } returns Optional.empty()

        val result = repository.count(predicate)

        assertEquals(0L, result)
        verify { mockJpaRepository.findById(1L) }
    }

    @Test
    @DisplayName("使用多个ID谓词计数应该返回找到的实体数量")
    fun `count with IDs predicate should return count of found entities`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val ids = listOf(1L, 2L, 3L)
        val entities = listOf(TestEntity(1L, "test1"), TestEntity(2L, "test2"))

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns ids
        every { mockJpaRepository.findAllById(ids) } returns entities

        val result = repository.count(predicate)

        assertEquals(2L, result)
        verify { mockJpaRepository.findAllById(ids) }
    }

    @Test
    @DisplayName("使用空ID列表计数应该返回0")
    fun `count with empty IDs should return 0`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val emptyIds = emptyList<Long>()

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns emptyIds

        val result = repository.count(predicate)

        assertEquals(0L, result)
    }

    @Test
    @DisplayName("使用Specification谓词计数应该使用规范执行器")
    fun `count with Specification predicate should use specification executor`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val specification = mockk<Specification<TestEntity>>()

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { mockJpaSpecificationExecutor.count(specification) } returns 5L

        val result = repository.count(predicate)

        assertEquals(5L, result)
        verify { mockJpaSpecificationExecutor.count(specification) }
    }

    @Test
    @DisplayName("使用ID谓词检查存在性，如果实体存在应该返回true")
    fun `exists with ID predicate should return true if entity exists`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val entity = TestEntity(1L, "test")

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns 1L
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { mockJpaRepository.findById(1L) } returns Optional.of(entity)

        val result = repository.exists(predicate)

        assertTrue(result)
        verify { mockJpaRepository.findById(1L) }
    }

    @Test
    @DisplayName("使用ID谓词检查存在性，如果实体不存在应该返回false")
    fun `exists with ID predicate should return false if entity does not exist`() {
        val predicate = mockk<Predicate<TestEntity>>()

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns 1L
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { mockJpaRepository.findById(1L) } returns Optional.empty()

        val result = repository.exists(predicate)

        assertFalse(result)
        verify { mockJpaRepository.findById(1L) }
    }

    @Test
    @DisplayName("使用多个ID谓词检查存在性，如果任何实体存在应该返回true")
    fun `exists with IDs predicate should return true if any entities exist`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val ids = listOf(1L, 2L)
        val entities = listOf(TestEntity(1L, "test1"))

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns ids
        every { mockJpaRepository.findAllById(ids) } returns entities

        val result = repository.exists(predicate)

        assertTrue(result)
        verify { mockJpaRepository.findAllById(ids) }
    }

    @Test
    @DisplayName("使用空ID列表检查存在性应该返回false")
    fun `exists with empty IDs should return false`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val emptyIds = emptyList<Long>()

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns emptyIds

        val result = repository.exists(predicate)

        assertFalse(result)
    }

    @Test
    @DisplayName("使用Specification谓词检查存在性应该使用规范执行器")
    fun `exists with Specification predicate should use specification executor`() {
        val predicate = mockk<Predicate<TestEntity>>()
        val specification = mockk<Specification<TestEntity>>()

        every { JpaPredicateSupport.resumeId<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeIds<TestEntity, Long>(predicate) } returns null
        every { JpaPredicateSupport.resumeSpecification(predicate) } returns specification
        every { mockJpaSpecificationExecutor.exists(specification) } returns true

        val result = repository.exists(predicate)

        assertTrue(result)
        verify { mockJpaSpecificationExecutor.exists(specification) }
    }
}
