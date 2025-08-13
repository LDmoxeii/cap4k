package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.springframework.data.jpa.domain.Specification

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
    @DisplayName("测试包含单个ID的JpaPredicate的resumeId方法")
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
    @DisplayName("测试包含多个ID的JpaPredicate的resumeId方法返回第一个")
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
    @DisplayName("测试包含空ID的JpaPredicate的resumeId方法")
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
    @DisplayName("测试ID为null的JpaPredicate的resumeId方法")
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
    @DisplayName("测试非JpaPredicate的resumeId方法抛出异常")
    fun `test resumeId with non-JpaPredicate throws exception`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行和验证
        assertThrows<com.only4.cap4k.ddd.core.share.DomainException> {
            JpaPredicateSupport.resumeId<TestEntity, Long>(nonJpaPredicate)
        }
    }

    @Test
    @DisplayName("测试包含ID的JpaPredicate的resumeIds方法")
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
    @DisplayName("测试包含单个ID的JpaPredicate的resumeIds方法")
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
    @DisplayName("测试ID为null的JpaPredicate的resumeIds方法")
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
    @DisplayName("测试非JpaPredicate的resumeIds方法抛出异常")
    fun `test resumeIds with non-JpaPredicate throws exception`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行和验证
        assertThrows<com.only4.cap4k.ddd.core.share.DomainException> {
            JpaPredicateSupport.resumeIds<TestEntity, Long>(nonJpaPredicate)
        }
    }

    @Test
    @DisplayName("测试包含规范的JpaPredicate的resumeSpecification方法")
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
    @DisplayName("测试规范为null的JpaPredicate的resumeSpecification方法")
    fun `test resumeSpecification with JpaPredicate having null specification`() {
        // 准备
        val jpaPredicate = JpaPredicate.byId(TestEntity::class.java, 123L)

        // 执行
        val result = JpaPredicateSupport.resumeSpecification(jpaPredicate)

        // 验证
        assertNull(result)
    }

    @Test
    @DisplayName("测试非JpaPredicate的resumeSpecification方法抛出异常")
    fun `test resumeSpecification with non-JpaPredicate throws exception`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行和验证
        assertThrows<com.only4.cap4k.ddd.core.share.DomainException> {
            JpaPredicateSupport.resumeSpecification(nonJpaPredicate)
        }
    }

    @Test
    @DisplayName("测试JpaPredicate的reflectEntityClass方法")
    fun `test reflectEntityClass with JpaPredicate`() {
        // 准备
        val jpaPredicate = JpaPredicate.byId(TestEntity::class.java, 123L)

        // 执行
        val result = JpaPredicateSupport.reflectEntityClass(jpaPredicate)

        // 验证
        assertEquals(TestEntity::class.java, result)
    }

    @Test
    @DisplayName("测试非JpaPredicate的reflectEntityClass方法抛出异常")
    fun `test reflectEntityClass with non-JpaPredicate throws exception`() {
        // 准备
        val nonJpaPredicate = NonJpaPredicate()

        // 执行和验证
        assertThrows<com.only4.cap4k.ddd.core.share.DomainException> {
            JpaPredicateSupport.reflectEntityClass(nonJpaPredicate)
        }
    }

    @Test
    @DisplayName("测试基于值对象的JpaPredicate的resumeId方法")
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
    @DisplayName("测试基于值对象的JpaPredicate的resumeIds方法")
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
    @DisplayName("测试基于值对象的JpaPredicate的reflectEntityClass方法")
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
    @DisplayName("测试所有方法使用同一个JpaPredicate实例的协同工作")
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
    @DisplayName("测试不同实体类型的类型安全性")
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
    @DisplayName("测试混合类型ID集合的边界情况")
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
