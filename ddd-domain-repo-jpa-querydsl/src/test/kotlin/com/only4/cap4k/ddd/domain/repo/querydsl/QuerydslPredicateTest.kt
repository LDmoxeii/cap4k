package com.only4.cap4k.ddd.domain.repo.querydsl

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.Expressions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@DisplayName("QuerydslPredicate 测试")
class QuerydslPredicateTest {

    data class TestEntity(val id: Long, val name: String)

    private val testEntityClass = TestEntity::class.java

    @Test
    @DisplayName("应该能够仅使用实体类创建QuerydslPredicate")
    fun `should create QuerydslPredicate with entity class only`() {
        val predicate = QuerydslPredicate(testEntityClass)

        assertEquals(testEntityClass, predicate.entityClass)
        assertNotNull(predicate.predicate)
        assertTrue(predicate.orderSpecifiers.isEmpty())
    }

    @Test
    @DisplayName("应该能够使用实体类和BooleanBuilder创建QuerydslPredicate")
    fun `should create QuerydslPredicate with entity class and BooleanBuilder`() {
        val booleanBuilder = BooleanBuilder()
        val predicate = QuerydslPredicate(testEntityClass, booleanBuilder)

        assertEquals(testEntityClass, predicate.entityClass)
        assertSame(booleanBuilder, predicate.predicate)
        assertTrue(predicate.orderSpecifiers.isEmpty())
    }

    @Test
    @DisplayName("应该能够使用of()工厂方法创建QuerydslPredicate")
    fun `should create QuerydslPredicate using of factory method`() {
        val predicate = QuerydslPredicate.of(testEntityClass)

        assertEquals(testEntityClass, predicate.entityClass)
        assertNotNull(predicate.predicate)
        assertTrue(predicate.orderSpecifiers.isEmpty())
    }

    @Test
    @DisplayName("应该能够使用byPredicate()工厂方法创建QuerydslPredicate")
    fun `should create QuerydslPredicate using byPredicate factory method`() {
        val testPredicate = Expressions.booleanTemplate("1 = 1")
        val predicate = QuerydslPredicate.byPredicate(testEntityClass, testPredicate)

        assertEquals(testEntityClass, predicate.entityClass)
        assertNotNull(predicate.predicate)
        assertTrue(predicate.orderSpecifiers.isEmpty())
    }

    @Test
    @DisplayName("应该能够添加where条件并返回自身以支持链式调用")
    fun `should add where condition and return self for chaining`() {
        val predicate = QuerydslPredicate.of(testEntityClass)
        val filter = Expressions.booleanTemplate("name = 'test'")

        val result = predicate.where(filter)

        assertSame(predicate, result) // Should return self for chaining
    }

    @Test
    @DisplayName("应该能够链式添加多个where条件")
    fun `should chain multiple where conditions`() {
        val predicate = QuerydslPredicate.of(testEntityClass)
        val filter1 = Expressions.booleanTemplate("name = 'test'")
        val filter2 = Expressions.booleanTemplate("id > 0")

        val result = predicate
            .where(filter1)
            .where(filter2)

        assertSame(predicate, result)
    }

    @Test
    @DisplayName("应该能够处理空的orderBy调用")
    fun `should handle empty orderBy call`() {
        val predicate = QuerydslPredicate.of(testEntityClass)

        val result = predicate.orderBy()

        assertSame(predicate, result)
        assertTrue(predicate.orderSpecifiers.isEmpty())
    }

    @Test
    @DisplayName("应该支持流式接口链式调用")
    fun `should support fluent interface chaining`() {
        val filter = Expressions.booleanTemplate("name = 'test'")

        val result = QuerydslPredicate.of(testEntityClass)
            .where(filter)
            .where(Expressions.booleanTemplate("id > 0"))

        assertNotNull(result)
        assertEquals(testEntityClass, result.entityClass)
    }

    @Test
    @DisplayName("应该在整个操作过程中保持实体类型")
    fun `should maintain entity type throughout operations`() {
        val predicate = QuerydslPredicate.of(testEntityClass)
        val filter = Expressions.booleanTemplate("name = 'test'")

        val result: QuerydslPredicate<TestEntity> = predicate
            .where(filter)

        assertEquals(testEntityClass, result.entityClass)
    }

    @Test
    @DisplayName("应该能够与不同的实体类型协同工作")
    fun `should work with different entity types`() {
        data class AnotherEntity(val value: String)

        val anotherEntityClass = AnotherEntity::class.java

        val predicate = QuerydslPredicate.of(anotherEntityClass)

        assertEquals(anotherEntityClass, predicate.entityClass)
    }
}
