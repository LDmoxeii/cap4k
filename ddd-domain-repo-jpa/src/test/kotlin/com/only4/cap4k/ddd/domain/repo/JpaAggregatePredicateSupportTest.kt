package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.jpa.domain.Specification
import kotlin.test.assertEquals
import kotlin.test.assertSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaAggregatePredicateSupportTest {

    // 测试实体类
    data class TestEntity(val id: Long, val name: String)

    // 测试聚合类
    class TestAggregate : Aggregate<TestEntity> {
        private lateinit var entity: TestEntity

        constructor() // 无参构造器

        fun getId(): Long = entity.id
        fun getName(): String = entity.name

        override fun _wrap(entity: TestEntity) {
            this.entity = entity
        }

        override fun _unwrap(): TestEntity = entity
    }

    // 测试值对象
    class TestValueObject(private val value: String) : ValueObject<String> {
        override fun hash(): String = value
    }

    // 测试值对象聚合
    class TestValueObjectAggregate : Aggregate<TestValueObject> {
        private lateinit var valueObject: TestValueObject

        constructor() // 无参构造器

        override fun _wrap(entity: TestValueObject) {
            this.valueObject = entity
        }

        override fun _unwrap(): TestValueObject = valueObject

        fun getValue(): String = valueObject.hash()
    }

    // 非JPA聚合断言类用于测试
    class NonJpaAggregatePredicate : AggregatePredicate<TestAggregate, TestEntity>

    @Test
    fun `test getPredicate with JpaAggregatePredicate containing ID-based predicate`() {
        // 准备
        val testId = 123L
        val jpaAggregatePredicate = JpaAggregatePredicate.byId(TestAggregate::class.java, testId)

        // 执行
        val result = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)

        // 验证
        assert(result is JpaPredicate<*>)
        val jpaPredicate = result as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(listOf(testId), jpaPredicate.ids?.toList())
    }

    @Test
    fun `test getPredicate with JpaAggregatePredicate containing IDs-based predicate`() {
        // 准备
        val testIds = listOf(123L, 456L, 789L)
        val jpaAggregatePredicate = JpaAggregatePredicate.byIds(TestAggregate::class.java, testIds)

        // 执行
        val result = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)

        // 验证
        assert(result is JpaPredicate<*>)
        val jpaPredicate = result as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(testIds, jpaPredicate.ids?.toList())
    }

    @Test
    fun `test getPredicate with JpaAggregatePredicate containing Specification-based predicate`() {
        // 准备
        val mockSpec = mockk<Specification<TestEntity>>()
        val jpaAggregatePredicate = JpaAggregatePredicate.bySpecification(TestAggregate::class.java, mockSpec)

        // 执行
        val result = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)

        // 验证
        assert(result is JpaPredicate<*>)
        val jpaPredicate = result as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertSame(mockSpec, jpaPredicate.spec)
    }

    @Test
    fun `test getPredicate with JpaAggregatePredicate containing existing Predicate`() {
        // 准备
        val existingPredicate = JpaPredicate.byId(TestEntity::class.java, 999L)
        val jpaAggregatePredicate = JpaAggregatePredicate.byPredicate(TestAggregate::class.java, existingPredicate)

        // 执行
        val result = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)

        // 验证
        assertSame(existingPredicate, result)
    }

    @Test
    fun `test getPredicate with ValueObject-based JpaAggregatePredicate`() {
        // 准备
        val valueObject = TestValueObject("test-value")
        val valueObjectAggregate = TestValueObjectAggregate()
        valueObjectAggregate._wrap(valueObject)

        val jpaAggregatePredicate = JpaAggregatePredicate.byValueObject(valueObjectAggregate)

        // 执行
        val result = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)

        // 验证
        assert(result is JpaPredicate<*>)
        val jpaPredicate = result as JpaPredicate<TestValueObject>
        assertEquals(TestValueObject::class.java, jpaPredicate.entityClass)
        assertEquals(listOf("test-value"), jpaPredicate.ids?.toList())
        assertEquals(valueObject, jpaPredicate.valueObject)
    }

    @Test
    fun `test reflectAggregateClass with JpaAggregatePredicate`() {
        // 准备
        val jpaAggregatePredicate = JpaAggregatePredicate.byId(TestAggregate::class.java, 123L)

        // 执行
        val result = JpaAggregatePredicateSupport.reflectAggregateClass(jpaAggregatePredicate)

        // 验证
        assertEquals(TestAggregate::class.java, result)
    }

    @Test
    fun `test reflectAggregateClass with different aggregate types`() {
        // 准备
        class AnotherAggregate : Aggregate<TestEntity> {
            override fun _wrap(entity: TestEntity) {}
            override fun _unwrap(): TestEntity = TestEntity(1, "test")
        }

        val jpaAggregatePredicate = JpaAggregatePredicate.byId(AnotherAggregate::class.java, 456L)

        // 执行
        val result = JpaAggregatePredicateSupport.reflectAggregateClass(jpaAggregatePredicate)

        // 验证
        assertEquals(AnotherAggregate::class.java, result)
    }

    @Test
    fun `test reflectAggregateClass with ValueObject-based aggregate`() {
        // 准备
        val valueObject = TestValueObject("test-value")
        val valueObjectAggregate = TestValueObjectAggregate()
        valueObjectAggregate._wrap(valueObject)

        val jpaAggregatePredicate = JpaAggregatePredicate.byValueObject(valueObjectAggregate)

        // 执行
        val result = JpaAggregatePredicateSupport.reflectAggregateClass(jpaAggregatePredicate)

        // 验证
        assertEquals(TestValueObjectAggregate::class.java, result)
    }

    @Test
    fun `test both methods work with same JpaAggregatePredicate instance`() {
        // 准备
        val testIds = listOf(111L, 222L, 333L)
        val jpaAggregatePredicate = JpaAggregatePredicate.byIds(TestAggregate::class.java, testIds)

        // 执行
        val predicate = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)
        val aggregateClass = JpaAggregatePredicateSupport.reflectAggregateClass(jpaAggregatePredicate)

        // 验证
        assertEquals(TestAggregate::class.java, aggregateClass)
        assert(predicate is JpaPredicate<*>)
        val jpaPredicate = predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(testIds, jpaPredicate.ids?.toList())
    }

    @Test
    fun `test getPredicate type casting with complex scenario`() {
        // 准备
        val mockSpec = mockk<Specification<TestEntity>>()
        val jpaAggregatePredicate = JpaAggregatePredicate.bySpecification(TestAggregate::class.java, mockSpec)

        // 执行
        val predicate = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)
        val aggregateClass = JpaAggregatePredicateSupport.reflectAggregateClass(jpaAggregatePredicate)

        // 验证聚合类
        assertEquals(TestAggregate::class.java, aggregateClass)

        // 验证断言
        assert(predicate is JpaPredicate<*>)
        val jpaPredicate = predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertSame(mockSpec, jpaPredicate.spec)
        assertEquals(null, jpaPredicate.ids)
        assertEquals(null, jpaPredicate.valueObject)
    }

    @Test
    fun `test methods consistency with different creation methods`() {
        // 准备多种创建方式的JpaAggregatePredicate
        val byIdPredicate = JpaAggregatePredicate.byId(TestAggregate::class.java, 100L)
        val byIdsPredicate = JpaAggregatePredicate.byIds(TestAggregate::class.java, listOf(200L, 300L))
        val bySpecPredicate = JpaAggregatePredicate.bySpecification(TestAggregate::class.java, mockk())
        val byPredicatePredicate = JpaAggregatePredicate.byPredicate(
            TestAggregate::class.java,
            JpaPredicate.byId(TestEntity::class.java, 400L)
        )

        // 执行和验证 - 所有方式创建的断言都应该返回相同的聚合类
        assertEquals(TestAggregate::class.java, JpaAggregatePredicateSupport.reflectAggregateClass(byIdPredicate))
        assertEquals(TestAggregate::class.java, JpaAggregatePredicateSupport.reflectAggregateClass(byIdsPredicate))
        assertEquals(TestAggregate::class.java, JpaAggregatePredicateSupport.reflectAggregateClass(bySpecPredicate))
        assertEquals(
            TestAggregate::class.java,
            JpaAggregatePredicateSupport.reflectAggregateClass(byPredicatePredicate)
        )

        // 验证所有方式都能正确获取到断言
        assert(JpaAggregatePredicateSupport.getPredicate(byIdPredicate) is JpaPredicate<*>)
        assert(JpaAggregatePredicateSupport.getPredicate(byIdsPredicate) is JpaPredicate<*>)
        assert(JpaAggregatePredicateSupport.getPredicate(bySpecPredicate) is JpaPredicate<*>)
        assert(JpaAggregatePredicateSupport.getPredicate(byPredicatePredicate) is JpaPredicate<*>)
    }

    @Test
    fun `test type safety across different entity types`() {
        // 准备不同的聚合和实体类型
        data class AnotherEntity(val code: String, val description: String)

        // 移动到类外部以避免泛型签名问题
        val jpaAggregatePredicate = JpaAggregatePredicate.byId(TestAggregate::class.java, "test-code")

        // 执行
        val predicate = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)
        val aggregateClass = JpaAggregatePredicateSupport.reflectAggregateClass(jpaAggregatePredicate)

        // 验证类型正确性
        assertEquals(TestAggregate::class.java, aggregateClass)
        assert(predicate is JpaPredicate<*>)
        val jpaPredicate = predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(listOf("test-code"), jpaPredicate.ids?.toList())
    }

    @Test
    fun `test edge case with empty ids collection`() {
        // 准备
        val emptyIds = emptyList<Any>()
        val jpaAggregatePredicate = JpaAggregatePredicate.byIds(TestAggregate::class.java, emptyIds)

        // 执行
        val predicate = JpaAggregatePredicateSupport.getPredicate(jpaAggregatePredicate)
        val aggregateClass = JpaAggregatePredicateSupport.reflectAggregateClass(jpaAggregatePredicate)

        // 验证
        assertEquals(TestAggregate::class.java, aggregateClass)
        assert(predicate is JpaPredicate<*>)
        val jpaPredicate = predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(emptyList<Any>(), jpaPredicate.ids?.toList())
    }
}
