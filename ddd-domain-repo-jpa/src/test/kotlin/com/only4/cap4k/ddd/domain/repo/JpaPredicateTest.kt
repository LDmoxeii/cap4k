package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JpaPredicateTest {

    private data class TestEntity(val id: Long, val name: String)

    private class TestValueObject(private val value: String) : ValueObject<String> {
        override fun hash(): String = value.hashCode().toString()
    }

    private class TestAggregate : Aggregate<TestEntity> {
        private lateinit var entity: TestEntity

        override fun _unwrap(): TestEntity = entity
        override fun _wrap(root: TestEntity) {
            this.entity = root
        }
    }

    @Test
    @DisplayName("通过ID创建单个ID的谓词")
    fun `byId should create predicate with single id`() {
        val id = 123L
        val predicate = JpaPredicate.byId(TestEntity::class.java, id)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertNull(predicate.spec)
        assertEquals(listOf(id), predicate.ids?.toList())
        assertNull(predicate.valueObject)
    }

    @Test
    @DisplayName("通过多个ID创建谓词")
    fun `byIds should create predicate with multiple ids`() {
        val ids = listOf(1L, 2L, 3L)
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertNull(predicate.spec)
        assertEquals(ids, predicate.ids?.toList())
        assertNull(predicate.valueObject)
    }

    @Test
    @DisplayName("处理空ID集合")
    fun `byIds should handle empty id collection`() {
        val ids = emptyList<Long>()
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertNull(predicate.spec)
        assertEquals(emptyList<Long>(), predicate.ids?.toList())
        assertNull(predicate.valueObject)
    }

    @Test
    @DisplayName("通过值对象创建谓词")
    fun `byValueObject should create predicate with value object`() {
        val valueObject = TestValueObject("test-value")
        val predicate = JpaPredicate.byValueObject(valueObject)

        assertEquals(TestValueObject::class.java, predicate.entityClass)
        assertNull(predicate.spec)
        assertEquals(listOf(valueObject.hash()), predicate.ids?.toList())
        assertEquals(valueObject, predicate.valueObject)
    }

    @Test
    @DisplayName("通过规范创建谓词")
    fun `bySpecification should create predicate with specification`() {
        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaPredicate.bySpecification(TestEntity::class.java, specification)

        assertEquals(TestEntity::class.java, predicate.entityClass)
        assertEquals(specification, predicate.spec)
        assertNull(predicate.ids)
        assertNull(predicate.valueObject)
    }

    @Test
    @DisplayName("转换为聚合谓词")
    fun `toAggregatePredicate should convert to aggregate predicate`() {
        val id = 456L
        val predicate = JpaPredicate.byId(TestEntity::class.java, id)
        val aggregatePredicate = predicate.toAggregatePredicate(TestAggregate::class.java)

        assertNotNull(aggregatePredicate)
        assertEquals(TestAggregate::class.java, (aggregatePredicate as JpaAggregatePredicate).aggregateClass)
        assertEquals(predicate, aggregatePredicate.predicate)
    }

    @Test
    @DisplayName("多个工厂方法应该创建不同的谓词")
    fun `multiple factory methods should create distinct predicates`() {
        val id = 1L
        val specification = mockk<Specification<TestEntity>>()
        val valueObject = TestValueObject("test")

        val predicateById = JpaPredicate.byId(TestEntity::class.java, id)
        val predicateBySpec = JpaPredicate.bySpecification(TestEntity::class.java, specification)
        val predicateByValueObject = JpaPredicate.byValueObject(valueObject)

        // 验证每个断言都有不同的配置
        assertNotNull(predicateById.ids)
        assertNull(predicateById.spec)
        assertNull(predicateById.valueObject)

        assertNull(predicateBySpec.ids)
        assertNotNull(predicateBySpec.spec)
        assertNull(predicateBySpec.valueObject)

        assertNotNull(predicateByValueObject.ids)
        assertNull(predicateByValueObject.spec)
        assertNotNull(predicateByValueObject.valueObject)
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
    @DisplayName("通过值对象应该处理不同的值对象实现")
    fun `byValueObject should handle different value object implementations`() {
        class CustomValueObject(private val data: Int) : ValueObject<String> {
            override fun hash(): String = data.toString()
        }

        val valueObject = CustomValueObject(42)
        val predicate = JpaPredicate.byValueObject(valueObject)

        assertEquals(CustomValueObject::class.java, predicate.entityClass)
        assertEquals(listOf("42"), predicate.ids?.toList())
        assertEquals(valueObject, predicate.valueObject)
    }

    @Test
    @DisplayName("使用多个ID时应该保持ID的顺序")
    fun `byIds should preserve order of ids`() {
        val ids = listOf(3L, 1L, 4L, 1L, 5L) // 包含重复和无序
        val predicate = JpaPredicate.byIds(TestEntity::class.java, ids)

        assertEquals(ids, predicate.ids?.toList())
    }

    @Test
    @DisplayName("转换为聚合谓词应该适用于不同的聚合类型")
    fun `toAggregatePredicate should work with different aggregate types`() {
        class AnotherAggregate : Aggregate<TestEntity> {
            private lateinit var entity: TestEntity
            override fun _unwrap(): TestEntity = entity
            override fun _wrap(root: TestEntity) {
                this.entity = root
            }
        }

        val predicate = JpaPredicate.byId(TestEntity::class.java, 1L)
        val aggregatePredicate1 = predicate.toAggregatePredicate(TestAggregate::class.java)
        val aggregatePredicate2 = predicate.toAggregatePredicate(AnotherAggregate::class.java)

        assertEquals(TestAggregate::class.java, (aggregatePredicate1 as JpaAggregatePredicate).aggregateClass)
        assertEquals(AnotherAggregate::class.java, (aggregatePredicate2 as JpaAggregatePredicate).aggregateClass)
    }
}
