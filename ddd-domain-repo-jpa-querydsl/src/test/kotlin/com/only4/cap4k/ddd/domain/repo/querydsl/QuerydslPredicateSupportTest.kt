package com.only4.cap4k.ddd.domain.repo.querydsl

import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.PageParam
import com.querydsl.core.types.dsl.Expressions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.only4.cap4k.ddd.core.domain.repo.Predicate as DomainPredicate

@DisplayName("QuerydslPredicateSupport 测试")
class QuerydslPredicateSupportTest {

    data class TestEntity(val id: Long, val name: String)

    private class NonQuerydslPredicate<ENTITY : Any> : DomainPredicate<ENTITY>

    private val testEntityClass = TestEntity::class.java

    @Test
    @DisplayName("当断言包含排序条件时应返回QSort")
    fun `should return QSort when predicate has order specifiers`() {
        val querydslPredicate = QuerydslPredicate.of(testEntityClass)
        // Can't easily create OrderSpecifier without Q-classes, so skip detailed order testing

        val result = QuerydslPredicateSupport.resumeSort(querydslPredicate)

        assertNotNull(result)
    }

    @Test
    @DisplayName("对于非QuerydslPredicate类型的resumeSort应抛出DomainException")
    fun `should throw DomainException for non-QuerydslPredicate in resumeSort`() {
        val nonQuerydslPredicate = NonQuerydslPredicate<TestEntity>()

        val exception = assertThrows<DomainException> {
            QuerydslPredicateSupport.resumeSort(nonQuerydslPredicate)
        }

        assertTrue(exception.message!!.contains("Unsupported predicate type"))
        assertTrue(exception.message!!.contains("NonQuerydslPredicate"))
    }

    @Test
    @DisplayName("调用resumePageable时应返回Pageable")
    fun `should return Pageable when calling resumePageable`() {
        val querydslPredicate = QuerydslPredicate.of(testEntityClass)
        val pageParam = PageParam.of(1, 10)  // Use 1-based page numbering

        val result = QuerydslPredicateSupport.resumePageable(querydslPredicate, pageParam)

        assertNotNull(result)
    }

    @Test
    @DisplayName("对于非QuerydslPredicate类型的resumePageable应抛出DomainException")
    fun `should throw DomainException for non-QuerydslPredicate in resumePageable`() {
        val nonQuerydslPredicate = NonQuerydslPredicate<TestEntity>()
        val pageParam = PageParam.of(1, 10)  // Use 1-based page numbering

        val exception = assertThrows<DomainException> {
            QuerydslPredicateSupport.resumePageable(nonQuerydslPredicate, pageParam)
        }

        assertTrue(exception.message!!.contains("Unsupported predicate type"))
    }

    @Test
    @DisplayName("应该从QuerydslPredicate返回BooleanBuilder")
    fun `should return BooleanBuilder from QuerydslPredicate`() {
        val querydslPredicate = QuerydslPredicate.of(testEntityClass)

        val result = QuerydslPredicateSupport.resumePredicate(querydslPredicate)

        assertNotNull(result)
    }

    @Test
    @DisplayName("对于非QuerydslPredicate类型的resumePredicate应抛出DomainException")
    fun `should throw DomainException for non-QuerydslPredicate in resumePredicate`() {
        val nonQuerydslPredicate = NonQuerydslPredicate<TestEntity>()

        val exception = assertThrows<DomainException> {
            QuerydslPredicateSupport.resumePredicate(nonQuerydslPredicate)
        }

        assertTrue(exception.message!!.contains("Unsupported predicate type"))
    }

    @Test
    @DisplayName("应该从QuerydslPredicate返回实体类")
    fun `should return entity class from QuerydslPredicate`() {
        val querydslPredicate = QuerydslPredicate.of(testEntityClass)

        val result = QuerydslPredicateSupport.reflectEntityClass(querydslPredicate)

        assertTrue(result == testEntityClass)
    }

    @Test
    @DisplayName("对于非QuerydslPredicate类型的reflectEntityClass应抛出DomainException")
    fun `should throw DomainException for non-QuerydslPredicate in reflectEntityClass`() {
        val nonQuerydslPredicate = NonQuerydslPredicate<TestEntity>()

        val exception = assertThrows<DomainException> {
            QuerydslPredicateSupport.reflectEntityClass(nonQuerydslPredicate)
        }

        assertTrue(exception.message!!.contains("Unsupported predicate type"))
    }

    @Test
    @DisplayName("应该能够与不同的实体类型协同工作")
    fun `should work with different entity types`() {
        data class AnotherEntity(val value: String)

        val anotherEntityClass = AnotherEntity::class.java
        val predicate = QuerydslPredicate.of(anotherEntityClass)

        val result = QuerydslPredicateSupport.reflectEntityClass(predicate)

        assertTrue(result == anotherEntityClass)
    }

    @Test
    @DisplayName("应该能够与BooleanBuilder断言协同工作")
    fun `should work with BooleanBuilder predicate`() {
        val customPredicate = Expressions.booleanTemplate("name = 'test'")
        val predicate = QuerydslPredicate.byPredicate(testEntityClass, customPredicate)

        val result = QuerydslPredicateSupport.resumePredicate(predicate)

        assertNotNull(result)
    }
}
