package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class JpaPredicateSupportTest {

    // 测试实体类
    data class TestEntity(val id: Long, val name: String)

    // 测试值对象
    class TestValueObject(private val value: String) : ValueObject<String> {
        override fun hash(): String = value
    }

    // 非JPA断言类用于测试
    class NonJpaPredicate : Predicate<TestEntity>

    @Test
    fun `test resumeId with JpaPredicate containing single id`() {
        // 准备
        val expectedId = 123L
        val jpaPredicate = JpaPredicate.byId(TestEntity::class.java, expectedId)

        // 执行
        val result = JpaPredicateSupport.resumeId<TestEntity, Long>(jpaPredicate)

        // 验证
        assertEquals(expectedId, result)
    }

    @Test
    fun `test resumeId with JpaPredicate containing multiple ids returns first`() {
        // 准备
        val ids = listOf(123L, 456L, 789L)
        val jpaPredicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        // 执行
        val result = JpaPredicateSupport.resumeId<TestEntity, Long>(jpaPredicate)

        // 验证
        assertEquals(123L, result)
    }

    @Test
    fun `test resumeId with JpaPredicate containing empty ids`() {
        // 准备
        val emptyIds = emptyList<Long>()
        val jpaPredicate = JpaPredicate.byIds(TestEntity::class.java, emptyIds)

        // 执行
        val result = JpaPredicateSupport.resumeId<TestEntity, Long>(jpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test resumeId with JpaPredicate having null ids`() {
        // 准备
        val mockSpec = mockk<Specification<TestEntity>>()
        val jpaPredicate = JpaPredicate.bySpecification(TestEntity::class.java, mockSpec)

        // 执行
        val result = JpaPredicateSupport.resumeId<TestEntity, Long>(jpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test resumeId with non-JpaPredicate returns null`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行
        val result = JpaPredicateSupport.resumeId<TestEntity, Long>(nonJpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test resumeIds with JpaPredicate containing ids`() {
        // 准备
        val expectedIds = listOf(123L, 456L, 789L)
        val jpaPredicate = JpaPredicate.byIds(TestEntity::class.java, expectedIds)

        // 执行
        val result = JpaPredicateSupport.resumeIds<TestEntity, Long>(jpaPredicate)

        // 验证
        assertEquals(expectedIds, result)
    }

    @Test
    fun `test resumeIds with JpaPredicate containing single id`() {
        // 准备
        val singleId = 123L
        val jpaPredicate = JpaPredicate.byId(TestEntity::class.java, singleId)

        // 执行
        val result = JpaPredicateSupport.resumeIds<TestEntity, Long>(jpaPredicate)

        // 验证
        assertEquals(listOf(singleId), result?.toList())
    }

    @Test
    fun `test resumeIds with JpaPredicate having null ids`() {
        // 准备
        val mockSpec = mockk<Specification<TestEntity>>()
        val jpaPredicate = JpaPredicate.bySpecification(TestEntity::class.java, mockSpec)

        // 执行
        val result = JpaPredicateSupport.resumeIds<TestEntity, Long>(jpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test resumeIds with non-JpaPredicate returns null`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行
        val result = JpaPredicateSupport.resumeIds<TestEntity, Long>(nonJpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test resumeSpecification with JpaPredicate containing specification`() {
        // 准备
        val mockSpec = mockk<Specification<TestEntity>>()
        val jpaPredicate = JpaPredicate.bySpecification(TestEntity::class.java, mockSpec)

        // 执行
        val result = JpaPredicateSupport.resumeSpecification(jpaPredicate)

        // 验证
        assertSame(mockSpec, result)
    }

    @Test
    fun `test resumeSpecification with JpaPredicate having null specification`() {
        // 准备
        val jpaPredicate = JpaPredicate.byId(TestEntity::class.java, 123L)

        // 执行
        val result = JpaPredicateSupport.resumeSpecification(jpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test resumeSpecification with non-JpaPredicate returns null`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行
        val result = JpaPredicateSupport.resumeSpecification(nonJpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test reflectEntityClass with JpaPredicate`() {
        // 准备
        val jpaPredicate = JpaPredicate.byId(TestEntity::class.java, 123L)

        // 执行
        val result = JpaPredicateSupport.reflectEntityClass(jpaPredicate)

        // 验证
        assertEquals(TestEntity::class.java, result)
    }

    @Test
    fun `test reflectEntityClass with non-JpaPredicate returns null`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行
        val result = JpaPredicateSupport.reflectEntityClass(nonJpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    fun `test resumeId with ValueObject-based JpaPredicate`() {
        // 准备
        val valueObject = TestValueObject("test-value")
        val jpaPredicate = JpaPredicate.byValueObject(valueObject)

        // 执行
        val result = JpaPredicateSupport.resumeId<TestValueObject, String>(jpaPredicate)

        // 验证
        assertEquals("test-value", result)
    }

    @Test
    fun `test resumeIds with ValueObject-based JpaPredicate`() {
        // 准备
        val valueObject = TestValueObject("test-value")
        val jpaPredicate = JpaPredicate.byValueObject(valueObject)

        // 执行
        val result = JpaPredicateSupport.resumeIds<TestValueObject, String>(jpaPredicate)

        // 验证
        assertEquals(listOf("test-value"), result?.toList())
    }

    @Test
    fun `test reflectEntityClass with ValueObject-based JpaPredicate`() {
        // 准备
        val valueObject = TestValueObject("test-value")
        val jpaPredicate = JpaPredicate.byValueObject(valueObject)

        // 执行
        val result = JpaPredicateSupport.reflectEntityClass(jpaPredicate)

        // 验证
        assertEquals(TestValueObject::class.java, result)
    }

    @Test
    fun `test all methods work together with same JpaPredicate instance`() {
        // 准备
        val ids = listOf(123L, 456L)
        val jpaPredicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        // 执行
        val resumedId = JpaPredicateSupport.resumeId<TestEntity, Long>(jpaPredicate)
        val resumedIds = JpaPredicateSupport.resumeIds<TestEntity, Long>(jpaPredicate)
        val resumedSpec = JpaPredicateSupport.resumeSpecification(jpaPredicate)
        val entityClass = JpaPredicateSupport.reflectEntityClass(jpaPredicate)

        // 验证
        assertEquals(123L, resumedId) // 第一个ID
        assertEquals(ids, resumedIds?.toList()) // 所有ID
        assertNull(resumedSpec) // 没有Specification
        assertEquals(TestEntity::class.java, entityClass) // 实体类
    }

    @Test
    fun `test type safety with different entity types`() {
        // 准备
        data class AnotherEntity(val id: String, val value: Int)

        val jpaPredicate = JpaPredicate.byId(AnotherEntity::class.java, "test-id")

        // 执行
        val resumedId = JpaPredicateSupport.resumeId<AnotherEntity, String>(jpaPredicate)
        val entityClass = JpaPredicateSupport.reflectEntityClass(jpaPredicate)

        // 验证
        assertEquals("test-id", resumedId)
        assertEquals(AnotherEntity::class.java, entityClass)
    }

    @Test
    fun `test edge case with mixed type ids collection`() {
        // 准备 - 创建包含混合类型的ID集合
        val mixedIds = listOf<Any>(123L, "string-id", 456.0)
        val jpaPredicate = JpaPredicate.byIds(TestEntity::class.java, mixedIds)

        // 执行
        val resumedId = JpaPredicateSupport.resumeId<TestEntity, Any>(jpaPredicate)
        val resumedIds = JpaPredicateSupport.resumeIds<TestEntity, Any>(jpaPredicate)

        // 验证
        assertEquals(123L, resumedId) // 第一个元素
        assertEquals(mixedIds, resumedIds?.toList()) // 所有元素
    }
}
