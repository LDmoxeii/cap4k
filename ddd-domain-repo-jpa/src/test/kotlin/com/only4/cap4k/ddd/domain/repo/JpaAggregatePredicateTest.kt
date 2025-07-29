package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JpaAggregatePredicateTest {

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

    private class TestValueObjectAggregate : Aggregate<TestValueObject> {
        private lateinit var valueObject: TestValueObject

        override fun _unwrap(): TestValueObject = valueObject
        override fun _wrap(root: TestValueObject) {
            this.valueObject = root
        }
    }

    @BeforeEach
    fun setup() {
        mockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
    }

    @AfterEach
    fun teardown() {
        unmockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
    }

    @Test
    fun `byId should create aggregate predicate with single id`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val id = 123L
        val predicate = JpaAggregatePredicate.byId(TestAggregate::class.java, id)

        assertEquals(TestAggregate::class.java, (predicate as JpaAggregatePredicate).aggregateClass)

        val jpaPredicate = predicate.predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(listOf(id), jpaPredicate.ids?.toList())
    }

    @Test
    fun `byIds should create aggregate predicate with multiple ids`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val ids = listOf(1L, 2L, 3L)
        val predicate = JpaAggregatePredicate.byIds(TestAggregate::class.java, ids)

        assertEquals(TestAggregate::class.java, (predicate as JpaAggregatePredicate).aggregateClass)

        val jpaPredicate = predicate.predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(ids, jpaPredicate.ids?.toList())
    }

    @Test
    fun `byIds should handle empty id collection`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val ids = emptyList<Long>()
        val predicate = JpaAggregatePredicate.byIds(TestAggregate::class.java, ids)

        assertEquals(TestAggregate::class.java, (predicate as JpaAggregatePredicate).aggregateClass)

        val jpaPredicate = predicate.predicate as JpaPredicate<TestEntity>
        assertEquals(emptyList<Long>(), jpaPredicate.ids?.toList())
    }

    @Test
    fun `byValueObject should create aggregate predicate with value object`() {
        val valueObject = TestValueObject("test-value")
        val aggregate = TestValueObjectAggregate().apply {
            _wrap(valueObject)
        }

        every {
            resolveGenericTypeClass(
                TestValueObjectAggregate::class.java,
                0,
                Aggregate::class.java,
                Aggregate.Default::class.java
            )
        } returns TestValueObject::class.java

        val predicate = JpaAggregatePredicate.byValueObject(aggregate)

        assertEquals(TestValueObjectAggregate::class.java, (predicate as JpaAggregatePredicate).aggregateClass)

        val jpaPredicate = predicate.predicate as JpaPredicate<TestValueObject>
        assertEquals(TestValueObject::class.java, jpaPredicate.entityClass)
        assertEquals(listOf(valueObject.hash()), jpaPredicate.ids?.toList())
        assertEquals(valueObject, jpaPredicate.valueObject)
    }

    @Test
    fun `bySpecification should create aggregate predicate with specification`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val specification = mockk<Specification<TestEntity>>()
        val predicate = JpaAggregatePredicate.bySpecification(TestAggregate::class.java, specification)

        assertEquals(TestAggregate::class.java, (predicate as JpaAggregatePredicate).aggregateClass)

        val jpaPredicate = predicate.predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
        assertEquals(specification, jpaPredicate.spec)
    }

    @Test
    fun `byPredicate should create aggregate predicate with existing predicate`() {
        val existingPredicate = JpaPredicate.byId(TestEntity::class.java, 456L)
        val aggregatePredicate = JpaAggregatePredicate.byPredicate(TestAggregate::class.java, existingPredicate)

        assertEquals(TestAggregate::class.java, (aggregatePredicate as JpaAggregatePredicate).aggregateClass)
        assertEquals(existingPredicate, aggregatePredicate.predicate)
    }

    @Test
    fun `getEntityClass should resolve generic type correctly`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val predicate = JpaAggregatePredicate.byId(TestAggregate::class.java, 1L)

        val jpaPredicate = (predicate as JpaAggregatePredicate).predicate as JpaPredicate<TestEntity>
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
    }

    @Test
    fun `aggregate predicate should implement both AggregatePredicate and Predicate interfaces`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val predicate = JpaAggregatePredicate.byId(TestAggregate::class.java, 1L)

        assertTrue(predicate is AggregatePredicate<*, *>)
        assertTrue(predicate is com.only4.cap4k.ddd.core.domain.repo.Predicate<*>)
    }

    @Test
    fun `different factory methods should create distinct aggregate predicates`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val id = 1L
        val specification = mockk<Specification<TestEntity>>()
        val existingPredicate = JpaPredicate.byId(TestEntity::class.java, 2L)

        val predicateById = JpaAggregatePredicate.byId(TestAggregate::class.java, id) as JpaAggregatePredicate
        val predicateBySpec =
            JpaAggregatePredicate.bySpecification(TestAggregate::class.java, specification) as JpaAggregatePredicate
        val predicateByPredicate =
            JpaAggregatePredicate.byPredicate(TestAggregate::class.java, existingPredicate) as JpaAggregatePredicate

        // 验证每个断言都有不同的内部断言配置
        val jpaPredicateById = predicateById.predicate as JpaPredicate<TestEntity>
        val jpaPredicateBySpec = predicateBySpec.predicate as JpaPredicate<TestEntity>
        val jpaPredicateByPredicate = predicateByPredicate.predicate

        assertNotNull(jpaPredicateById.ids)
        assertEquals(specification, jpaPredicateBySpec.spec)
        assertEquals(existingPredicate, jpaPredicateByPredicate)
    }

    @Test
    fun `aggregate predicate should handle different aggregate types`() {
        class AnotherAggregate : Aggregate<TestEntity> {
            private lateinit var entity: TestEntity
            override fun _unwrap(): TestEntity = entity
            override fun _wrap(root: TestEntity) {
                this.entity = root
            }
        }

        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        every {
            resolveGenericTypeClass(
                AnotherAggregate::class.java,
                0,
                Aggregate::class.java,
                Aggregate.Default::class.java
            )
        } returns TestEntity::class.java

        val predicate1 = JpaAggregatePredicate.byId(TestAggregate::class.java, 1L) as JpaAggregatePredicate
        val predicate2 = JpaAggregatePredicate.byId(AnotherAggregate::class.java, 1L) as JpaAggregatePredicate

        assertEquals(TestAggregate::class.java, predicate1.aggregateClass)
        assertEquals(AnotherAggregate::class.java, predicate2.aggregateClass)
    }

    @Test
    fun `byIds should preserve order of ids in aggregate predicate`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val ids = listOf(3L, 1L, 4L, 1L, 5L)
        val predicate = JpaAggregatePredicate.byIds(TestAggregate::class.java, ids) as JpaAggregatePredicate

        val jpaPredicate = predicate.predicate as JpaPredicate<TestEntity>
        assertEquals(ids, jpaPredicate.ids?.toList())
    }

    @Test
    fun `aggregate predicate should handle complex specifications`() {
        every {
            resolveGenericTypeClass(TestAggregate::class.java, 0, Aggregate::class.java, Aggregate.Default::class.java)
        } returns TestEntity::class.java

        val specification = mockk<Specification<TestEntity>>()

        val predicate =
            JpaAggregatePredicate.bySpecification(TestAggregate::class.java, specification) as JpaAggregatePredicate

        val jpaPredicate = predicate.predicate as JpaPredicate<TestEntity>
        assertEquals(specification, jpaPredicate.spec)
        assertEquals(TestEntity::class.java, jpaPredicate.entityClass)
    }
}
