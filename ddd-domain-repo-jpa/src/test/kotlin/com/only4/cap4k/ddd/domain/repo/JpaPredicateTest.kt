package com.only4.cap4k.ddd.domain.repo

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.data.jpa.domain.Specification

class JpaPredicateTest {

    private data class TestEntity(val id: Long, val name: String)

    @Test
    @DisplayName("通过ID创建单个ID的谓词")
    fun `byId should create predicate with single id`() {
        val id = 123L
        val predicate = JpaPredicate.byId(TestEntity::class.java, id)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertNull(predicate.spec)
        assertEquals(listOf(id), predicate.ids?.toList())
    }

    @Test
    @DisplayName("通过多个ID创建谓词")
    fun `byIds should create predicate with multiple ids`() {
        val ids = listOf(1L, 2L, 3L)
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertNull(predicate.spec)
        assertEquals(ids, predicate.ids?.toList())
    }

    @Test
    @DisplayName("处理空ID集合")
    fun `byIds should handle empty id collection`() {
        val ids = emptyList<Long>()
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertNull(predicate.spec)
        assertEquals(emptyList<Long>(), predicate.ids?.toList())
    }

    @Test
    @DisplayName("通过规范创建谓词")
    fun `bySpecification should create predicate with specification`() {
        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaPredicate.bySpecification(TestEntity::class.java, specification)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertEquals(specification, predicate.spec)
        assertNull(predicate.ids)
    }

    @Test
    @DisplayName("多个工厂方法应该创建不同的谓词")
    fun `multiple factory methods should create distinct predicates`() {
        val id = 1L
        val specification = mockk<Specification<TestEntity>>()

        val predicateById = JpaPredicate.byId(TestEntity::class.java, id)
        val predicateBySpec = JpaPredicate.bySpecification(TestEntity::class.java, specification)

        assertNotNull(predicateById.ids)
        assertNull(predicateById.spec)

        assertNull(predicateBySpec.ids)
        assertNotNull(predicateBySpec.spec)
    }

    @Test
    @DisplayName("谓词应该处理不同的实体类型")
    fun `predicate should handle different entity types`() {
        data class AnotherEntity(val code: String)

        val id = "test-id"
        val predicate = JpaPredicate.byId(AnotherEntity::class.java, id)

        assertEquals(AnotherEntity::class.java, predicate.entityClass)
        assertEquals(listOf(id), predicate.ids?.toList())
    }

    @Test
    @DisplayName("使用多个ID时应该保持ID的顺序")
    fun `byIds should preserve order of ids`() {
        val ids = listOf(3L, 1L, 4L, 1L, 5L) // 包含重复和无序
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        assertEquals(ids, predicate.ids?.toList())
    }

}
