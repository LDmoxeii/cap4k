package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.jpa.domain.Specification
import java.util.Optional

class AbstractJpaRepositoryTest {
    private val provider = mockk<JpaRepositoryProvider<TestEntity, Long>>()
    private val repository = AbstractJpaRepository(provider)

    private data class TestEntity(val id: Long, val name: String)

    @Test
    fun `supports JpaPredicate`() {
        assertEquals(JpaPredicate::class.java, repository.supportPredicateClass())
    }

    @Test
    fun `ID queries use the same explicit sort contract as specifications`() {
        val ids = listOf(3L, 1L, 2L)
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)
        val orders = listOf(OrderInfo.asc("name"))
        val sort = toSpringData(orders)
        val entities = listOf(TestEntity(1, "a"), TestEntity(2, "b"), TestEntity(3, "c"))
        every { provider.findAllById(ids, sort) } returns entities

        assertEquals(entities, repository.find(predicate, orders))

        verify(exactly = 1) { provider.findAllById(ids, sort) }
    }

    @Test
    fun `Specification queries retain the explicit sort contract`() {
        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaPredicate.bySpecification(TestEntity::class.java, specification)
        val orders = listOf(OrderInfo.desc("name"))
        val sort = toSpringData(orders)
        val entities = listOf(TestEntity(2, "b"), TestEntity(1, "a"))
        every { provider.findAll(specification, sort) } returns entities

        assertEquals(entities, repository.find(predicate, orders))

        verify(exactly = 1) { provider.findAll(specification, sort) }
    }

    @Test
    fun `ID list query honors page offset and limit`() {
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)
        val pageParam = PageParam.of(2, 2).orderByAsc("name")
        val pageable = toSpringData(pageParam)
        val expected = listOf(TestEntity(3, "c"), TestEntity(4, "d"))
        every { provider.findAllById(ids, pageable) } returns PageImpl(expected, pageable, 5)

        assertEquals(expected, repository.find(predicate, pageParam))

        verify(exactly = 1) { provider.findAllById(ids, pageable) }
    }

    @Test
    fun `Specification list query honors page offset and limit`() {
        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaPredicate.bySpecification(TestEntity::class.java, specification)
        val pageParam = PageParam.of(2, 2).orderByAsc("name")
        val pageable = toSpringData(pageParam)
        val expected = listOf(TestEntity(3, "c"), TestEntity(4, "d"))
        every { provider.findAll(specification, pageable) } returns PageImpl(expected, pageable, 5)

        assertEquals(expected, repository.find(predicate, pageParam))

        verify(exactly = 1) { provider.findAll(specification, pageable) }
    }

    @Test
    fun `findOne by ID uses the exact identifier`() {
        val entity = TestEntity(7, "seven")
        every { provider.findById(7L) } returns Optional.of(entity)

        assertEquals(entity, repository.findOne(JpaPredicate.byId(TestEntity::class.java, 7L)))

        verify(exactly = 1) { provider.findById(7L) }
    }

    @Test
    fun `findFirst by IDs applies ordering and limit`() {
        val ids = listOf(1L, 2L, 3L)
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)
        val pageParam = PageParam.limit(1).orderByDesc("name")
        val pageable = toSpringData(pageParam)
        val expected = TestEntity(3, "c")
        every { provider.findAllById(ids, pageable) } returns PageImpl(listOf(expected), pageable, 3)

        assertEquals(expected, repository.findFirst(predicate, listOf(OrderInfo.desc("name"))))

        verify(exactly = 1) { provider.findAllById(ids, pageable) }
    }

    @Test
    fun `findFirst by Specification applies ordering and limit`() {
        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaPredicate.bySpecification(TestEntity::class.java, specification)
        val pageParam = PageParam.limit(1).orderByDesc("name")
        val pageable = toSpringData(pageParam)
        val expected = TestEntity(3, "c")
        every { provider.findAll(specification, pageable) } returns PageImpl(listOf(expected), pageable, 3)

        assertEquals(expected, repository.findFirst(predicate, listOf(OrderInfo.desc("name"))))

        verify(exactly = 1) { provider.findAll(specification, pageable) }
    }

    @Test
    fun `ID PageData preserves requested page and total before slicing`() {
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)
        val pageParam = PageParam.of(2, 2).orderByAsc("name")
        val pageable = toSpringData(pageParam)
        val items = listOf(TestEntity(3, "c"), TestEntity(4, "d"))
        every { provider.findAllById(ids, pageable) } returns PageImpl(items, pageable, 5)

        val result = repository.findPage(predicate, pageParam)

        assertPage(result, pageNum = 2, pageSize = 2, totalCount = 5, items = items)
    }

    @Test
    fun `Specification PageData preserves out of range page and total`() {
        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaPredicate.bySpecification(TestEntity::class.java, specification)
        val pageParam = PageParam.of(4, 2).orderByAsc("name")
        val pageable = toSpringData(pageParam)
        every { provider.findAll(specification, pageable) } returns PageImpl(emptyList(), pageable, 5)

        val result = repository.findPage(predicate, pageParam)

        assertPage(result, pageNum = 4, pageSize = 2, totalCount = 5, items = emptyList())
    }

    @Test
    fun `empty unresolved predicate preserves requested PageData metadata`() {
        val pageParam = PageParam.of(3, 7)

        val result = repository.findPage(JpaPredicate(TestEntity::class.java), pageParam)

        assertPage(result, pageNum = 3, pageSize = 7, totalCount = 0, items = emptyList())
    }

    @Test
    fun `count and exists for IDs use database predicates`() {
        val ids = listOf(1L, 2L, 3L)
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)
        every { provider.countByIds(ids) } returns 2
        every { provider.existsByIds(ids) } returns true

        assertEquals(2, repository.count(predicate))
        assertTrue(repository.exists(predicate))

        verify(exactly = 1) { provider.countByIds(ids) }
        verify(exactly = 1) { provider.existsByIds(ids) }
    }

    @Test
    fun `empty IDs have zero count and do not exist`() {
        val ids = emptyList<Long>()
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)
        every { provider.countByIds(ids) } returns 0
        every { provider.existsByIds(ids) } returns false

        assertEquals(0, repository.count(predicate))
        assertFalse(repository.exists(predicate))
    }

    @Test
    fun `count and exists for Specification retain Specification semantics`() {
        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaPredicate.bySpecification(TestEntity::class.java, specification)
        every { provider.count(specification) } returns 4
        every { provider.exists(specification) } returns true

        assertEquals(4, repository.count(predicate))
        assertTrue(repository.exists(predicate))
    }

    @Test
    fun `unresolved predicate returns empty query results`() {
        val predicate = JpaPredicate(TestEntity::class.java)

        assertEquals(emptyList<TestEntity>(), repository.find(predicate, emptyList()))
        assertNull(repository.findOne(predicate))
        assertNull(repository.findFirst(predicate, emptyList()))
        assertEquals(0, repository.count(predicate))
        assertFalse(repository.exists(predicate))
    }

    private fun assertPage(
        actual: PageData<TestEntity>,
        pageNum: Int,
        pageSize: Int,
        totalCount: Long,
        items: List<TestEntity>,
    ) {
        assertEquals(pageNum, actual.pageNum)
        assertEquals(pageSize, actual.pageSize)
        assertEquals(totalCount, actual.totalCount)
        assertEquals(items, actual.list)
    }
}
