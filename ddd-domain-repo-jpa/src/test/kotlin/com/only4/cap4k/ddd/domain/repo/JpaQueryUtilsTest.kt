package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.NoResultException
import jakarta.persistence.NonUniqueResultException
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class JpaQueryUtilsTest {

    private lateinit var mockEntityManager: EntityManager
    private lateinit var mockJpaUnitOfWork: JpaUnitOfWork
    private lateinit var mockCriteriaBuilder: CriteriaBuilder
    private lateinit var mockCriteriaQuery: CriteriaQuery<TestEntity>
    private lateinit var mockCountQuery: CriteriaQuery<Long>
    private lateinit var mockRoot: Root<TestEntity>
    private lateinit var mockTypedQuery: TypedQuery<TestEntity>
    private lateinit var mockCountTypedQuery: TypedQuery<Long>

    data class TestEntity(val id: Long, val name: String)

    @BeforeEach
    fun setUp() {
        mockEntityManager = mockk(relaxed = true)
        mockJpaUnitOfWork = mockk(relaxed = true)
        mockCriteriaBuilder = mockk(relaxed = true)
        mockCriteriaQuery = mockk(relaxed = true)
        mockCountQuery = mockk(relaxed = true)
        mockRoot = mockk(relaxed = true)
        mockTypedQuery = mockk(relaxed = true)
        mockCountTypedQuery = mockk(relaxed = true)

        every { mockJpaUnitOfWork.entityManager } returns mockEntityManager
        every { mockEntityManager.criteriaBuilder } returns mockCriteriaBuilder
        every { mockCriteriaBuilder.createQuery(TestEntity::class.java) } returns mockCriteriaQuery
        every { mockCriteriaBuilder.createQuery(Long::class.java) } returns mockCountQuery
        every { mockCriteriaQuery.from(TestEntity::class.java) } returns mockRoot
        every { mockCountQuery.from(TestEntity::class.java) } returns mockRoot
        every { mockEntityManager.createQuery(mockCriteriaQuery) } returns mockTypedQuery
        every { mockEntityManager.createQuery(mockCountQuery) } returns mockCountTypedQuery

        // Configure JpaQueryUtils
        JpaQueryUtils.configure(mockJpaUnitOfWork, 100)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @DisplayName("queryOne应成功返回单条结果")
    fun `queryOne should return single result successfully`() {
        val expectedEntity = TestEntity(1L, "Test Entity")
        every { mockTypedQuery.singleResult } returns expectedEntity

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryOne(TestEntity::class.java, TestEntity::class.java, queryBuilder)

        assertEquals(expectedEntity, result)
        verify { mockTypedQuery.singleResult }
    }

    @Test
    @DisplayName("queryOne在没有找到结果时应抛出NoResultException")
    fun `queryOne should throw NoResultException when no result found`() {
        every { mockTypedQuery.singleResult } throws NoResultException()

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        assertThrows<NoResultException> {
            JpaQueryUtils.queryOne(TestEntity::class.java, TestEntity::class.java, queryBuilder)
        }
    }

    @Test
    @DisplayName("queryOne在找到多条结果时应抛出NonUniqueResultException")
    fun `queryOne should throw NonUniqueResultException when multiple results found`() {
        every { mockTypedQuery.singleResult } throws NonUniqueResultException()

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        assertThrows<NonUniqueResultException> {
            JpaQueryUtils.queryOne(TestEntity::class.java, TestEntity::class.java, queryBuilder)
        }
    }

    @Test
    @DisplayName("queryList应返回结果列表")
    fun `queryList should return list of results`() {
        val expectedEntities = listOf(
            TestEntity(1L, "Entity 1"),
            TestEntity(2L, "Entity 2"),
            TestEntity(3L, "Entity 3")
        )
        every { mockTypedQuery.resultList } returns expectedEntities

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryList(TestEntity::class.java, TestEntity::class.java, queryBuilder)

        assertEquals(expectedEntities, result)
        assertEquals(3, result.size)
        verify { mockTypedQuery.resultList }
    }

    @Test
    @DisplayName("queryList在没有找到结果时应返回空列表")
    fun `queryList should return empty list when no results found`() {
        every { mockTypedQuery.resultList } returns emptyList()

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryList(TestEntity::class.java, TestEntity::class.java, queryBuilder)

        assertTrue(result.isEmpty())
        verify { mockTypedQuery.resultList }
    }

    @Test
    @DisplayName("queryList应处理阈值内的大结果集")
    fun `queryList should work with large result sets within threshold`() {
        val normalResultList = (1..50).map { TestEntity(it.toLong(), "Entity $it") }
        every { mockTypedQuery.resultList } returns normalResultList

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryList(TestEntity::class.java, TestEntity::class.java, queryBuilder)

        assertEquals(50, result.size)
        assertEquals("Entity 1", result[0].name)
        assertEquals("Entity 50", result[49].name)
    }

    @Test
    @DisplayName("queryFirst在存在结果时应返回包含第一条记录的Optional")
    fun `queryFirst should return Optional with first result when results exist`() {
        val expectedEntities = listOf(
            TestEntity(1L, "First Entity"),
            TestEntity(2L, "Second Entity")
        )
        every { mockTypedQuery.setFirstResult(0) } returns mockTypedQuery
        every { mockTypedQuery.setMaxResults(1) } returns mockTypedQuery
        every { mockTypedQuery.resultList } returns expectedEntities

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryFirst(TestEntity::class.java, TestEntity::class.java, queryBuilder)

        assertTrue(result.isPresent)
        assertEquals(expectedEntities[0], result.get())
        verify { mockTypedQuery.firstResult = 0 }
        verify { mockTypedQuery.maxResults = 1 }
    }

    @Test
    @DisplayName("queryFirst在没有找到结果时应返回空Optional")
    fun `queryFirst should return empty Optional when no results found`() {
        every { mockTypedQuery.setFirstResult(0) } returns mockTypedQuery
        every { mockTypedQuery.setMaxResults(1) } returns mockTypedQuery
        every { mockTypedQuery.resultList } returns emptyList()

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryFirst(TestEntity::class.java, TestEntity::class.java, queryBuilder)

        assertFalse(result.isPresent)
        verify { mockTypedQuery.firstResult = 0 }
        verify { mockTypedQuery.maxResults = 1 }
    }

    @Test
    @DisplayName("queryPage应返回分页结果")
    fun `queryPage should return paginated results`() {
        val expectedEntities = listOf(
            TestEntity(6L, "Entity 6"),
            TestEntity(7L, "Entity 7"),
            TestEntity(8L, "Entity 8")
        )
        val pageIndex = 2
        val pageSize = 3

        every { mockTypedQuery.setFirstResult(pageSize * pageIndex) } returns mockTypedQuery
        every { mockTypedQuery.setMaxResults(pageSize) } returns mockTypedQuery
        every { mockTypedQuery.resultList } returns expectedEntities

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryPage(
            TestEntity::class.java,
            TestEntity::class.java,
            queryBuilder,
            pageIndex,
            pageSize
        )

        assertEquals(expectedEntities, result)
        verify { mockTypedQuery.firstResult = 6 } // pageSize * pageIndex = 3 * 2 = 6
        verify { mockTypedQuery.maxResults = 3 }
    }

    @Test
    @DisplayName("queryPage应正确处理第一页")
    fun `queryPage should handle first page correctly`() {
        val expectedEntities = listOf(
            TestEntity(1L, "Entity 1"),
            TestEntity(2L, "Entity 2")
        )
        val pageIndex = 0
        val pageSize = 5

        every { mockTypedQuery.setFirstResult(0) } returns mockTypedQuery
        every { mockTypedQuery.setMaxResults(pageSize) } returns mockTypedQuery
        every { mockTypedQuery.resultList } returns expectedEntities

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryPage(
            TestEntity::class.java,
            TestEntity::class.java,
            queryBuilder,
            pageIndex,
            pageSize
        )

        assertEquals(expectedEntities, result)
        verify { mockTypedQuery.firstResult = 0 }
        verify { mockTypedQuery.maxResults = 5 }
    }

    @Test
    @DisplayName("queryPage应处理空分页结果")
    fun `queryPage should handle empty page results`() {
        val pageIndex = 5
        val pageSize = 10

        every { mockTypedQuery.setFirstResult(50) } returns mockTypedQuery
        every { mockTypedQuery.setMaxResults(10) } returns mockTypedQuery
        every { mockTypedQuery.resultList } returns emptyList()

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { _, _, _ ->
            // Mock query building logic
        }

        val result = JpaQueryUtils.queryPage(
            TestEntity::class.java,
            TestEntity::class.java,
            queryBuilder,
            pageIndex,
            pageSize
        )

        assertTrue(result.isEmpty())
        verify { mockTypedQuery.firstResult = 50 }
        verify { mockTypedQuery.maxResults = 10 }
    }

    @Test
    @DisplayName("count应返回计数结果")
    fun `count should return count result`() {
        val expectedCount = 42L
        every { mockCountTypedQuery.singleResult } returns expectedCount

        val queryBuilder = JpaQueryUtils.QueryBuilder<Long, TestEntity> { _, _, _ ->
            // Mock count query building logic
        }

        val result = JpaQueryUtils.count(TestEntity::class.java, queryBuilder)

        assertEquals(expectedCount, result)
        verify { mockCountTypedQuery.singleResult }
    }

    @Test
    @DisplayName("count在没有匹配记录时应返回零")
    fun `count should return zero when no matching records`() {
        val expectedCount = 0L
        every { mockCountTypedQuery.singleResult } returns expectedCount

        val queryBuilder = JpaQueryUtils.QueryBuilder<Long, TestEntity> { _, _, _ ->
            // Mock count query building logic
        }

        val result = JpaQueryUtils.count(TestEntity::class.java, queryBuilder)

        assertEquals(0L, result)
        verify { mockCountTypedQuery.singleResult }
    }

    @Test
    @DisplayName("configure应允许设置新配置")
    fun `configure should allow setting new configuration`() {
        val newMockUnitOfWork = mockk<JpaUnitOfWork>(relaxed = true)
        val newMockEntityManager = mockk<EntityManager>(relaxed = true)
        val newThreshold = 200

        every { newMockUnitOfWork.entityManager } returns newMockEntityManager
        every { newMockEntityManager.criteriaBuilder } returns mockCriteriaBuilder

        // This test verifies that configure method works
        assertDoesNotThrow {
            JpaQueryUtils.configure(newMockUnitOfWork, newThreshold)
        }
    }

    @Test
    @DisplayName("QueryBuilder函数式接口应正确工作")
    fun `QueryBuilder functional interface should work correctly`() {
        var builderCalled = false
        var receivedCriteriaBuilder: CriteriaBuilder? = null
        var receivedCriteriaQuery: CriteriaQuery<TestEntity>? = null
        var receivedRoot: Root<TestEntity>? = null

        val queryBuilder = JpaQueryUtils.QueryBuilder { cb, cq, root ->
            builderCalled = true
            receivedCriteriaBuilder = cb
            receivedCriteriaQuery = cq
            receivedRoot = root
        }

        every { mockTypedQuery.singleResult } returns TestEntity(1L, "Test")

        JpaQueryUtils.queryOne(TestEntity::class.java, TestEntity::class.java, queryBuilder)

        assertTrue(builderCalled)
        assertEquals(mockCriteriaBuilder, receivedCriteriaBuilder)
        assertEquals(mockCriteriaQuery, receivedCriteriaQuery)
        assertEquals(mockRoot, receivedRoot)
    }

    @Test
    @DisplayName("应正确处理不同的结果类型和实体类型")
    fun `should handle different result and entity types correctly`() {
        data class ProjectionResult(val id: Long, val summary: String)

        val mockProjectionQuery = mockk<CriteriaQuery<ProjectionResult>>(relaxed = true)
        val mockProjectionTypedQuery = mockk<TypedQuery<ProjectionResult>>(relaxed = true)
        val expectedProjection = ProjectionResult(1L, "Summary")

        every { mockCriteriaBuilder.createQuery(ProjectionResult::class.java) } returns mockProjectionQuery
        every { mockProjectionQuery.from(TestEntity::class.java) } returns mockRoot
        every { mockEntityManager.createQuery(mockProjectionQuery) } returns mockProjectionTypedQuery
        every { mockProjectionTypedQuery.singleResult } returns expectedProjection

        val queryBuilder = JpaQueryUtils.QueryBuilder<ProjectionResult, TestEntity> { _, _, _ ->
            // Mock projection query building logic
        }

        val result = JpaQueryUtils.queryOne(ProjectionResult::class.java, TestEntity::class.java, queryBuilder)

        assertEquals(expectedProjection, result)
        verify { mockProjectionTypedQuery.singleResult }
    }

    @Test
    @DisplayName("queryBuilder应在所有查询方法中接收正确参数")
    fun `queryBuilder should receive correct parameters for all query methods`() {
        val testBuilders = mutableListOf<Triple<CriteriaBuilder, CriteriaQuery<*>, Root<TestEntity>>>()

        val queryBuilder = JpaQueryUtils.QueryBuilder<TestEntity, TestEntity> { cb, cq, root ->
            testBuilders.add(Triple(cb, cq, root))
        }

        every { mockTypedQuery.singleResult } returns TestEntity(1L, "Test")
        every { mockTypedQuery.resultList } returns listOf(TestEntity(1L, "Test"))
        every { mockTypedQuery.setFirstResult(any()) } returns mockTypedQuery
        every { mockTypedQuery.setMaxResults(any()) } returns mockTypedQuery

        // Test all query methods
        JpaQueryUtils.queryOne(TestEntity::class.java, TestEntity::class.java, queryBuilder)
        JpaQueryUtils.queryList(TestEntity::class.java, TestEntity::class.java, queryBuilder)
        JpaQueryUtils.queryFirst(TestEntity::class.java, TestEntity::class.java, queryBuilder)
        JpaQueryUtils.queryPage(TestEntity::class.java, TestEntity::class.java, queryBuilder, 0, 10)

        assertEquals(4, testBuilders.size)
        testBuilders.forEach { (cb, cq, root) ->
            assertEquals(mockCriteriaBuilder, cb)
            assertEquals(mockCriteriaQuery, cq)
            assertEquals(mockRoot, root)
        }
    }
}
