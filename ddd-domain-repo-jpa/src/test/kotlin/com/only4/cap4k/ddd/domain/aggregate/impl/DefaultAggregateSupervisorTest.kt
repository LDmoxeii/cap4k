package com.only4.cap4k.ddd.domain.aggregate.impl

import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.aggregate.*
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicateSupport
import io.mockk.*
import org.junit.jupiter.api.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultAggregateSupervisorTest {

    private lateinit var mockRepositorySupervisor: RepositorySupervisor
    private lateinit var mockAggregateFactorySupervisor: AggregateFactorySupervisor
    private lateinit var mockUnitOfWork: UnitOfWork
    private lateinit var supervisor: DefaultAggregateSupervisor

    // 测试实体类
    data class TestEntity(val id: Long, val name: String, val value: Int)

    // 测试聚合类
    class TestAggregate : Aggregate<TestEntity> {
        private lateinit var entity: TestEntity

        constructor() // 无参构造器

        constructor(payload: TestAggregatePayload) { // 载荷构造器
            this.entity = TestEntity(0L, payload.name, payload.value)
        }

        override fun _wrap(entity: TestEntity) {
            this.entity = entity
        }

        override fun _unwrap(): TestEntity = entity

        fun getId(): Long = entity.id
        fun getName(): String = entity.name
        fun getValue(): Int = entity.value
    }

    // 测试聚合载荷类
    class TestAggregatePayload(
        val name: String,
        val value: Int
    ) : AggregatePayload<TestEntity>

    // 测试ID类
    class TestId(value: Long) : Id<TestAggregate, Long> {
        override val value: Long = value
    }

    // 不可实例化的聚合类用于测试异常情况
    class NonInstantiableAggregate private constructor() : Aggregate<TestEntity> {
        override fun _wrap(entity: TestEntity) {}
        override fun _unwrap(): TestEntity = TestEntity(1L, "test", 1)
    }

    @BeforeEach
    fun setup() {
        mockRepositorySupervisor = mockk<RepositorySupervisor>(relaxed = true)
        mockAggregateFactorySupervisor = mockk<AggregateFactorySupervisor>(relaxed = true)
        mockUnitOfWork = mockk<UnitOfWork>(relaxed = true)

        supervisor = DefaultAggregateSupervisor(
            mockRepositorySupervisor,
            unitOfWork = mockUnitOfWork
        )

        // Mock静态方法
        mockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
        mockkObject(AggregateFactorySupervisor)
        mockkObject(JpaAggregatePredicateSupport)

        every { AggregateFactorySupervisor.instance } returns mockAggregateFactorySupervisor
    }

    @AfterEach
    fun teardown() {
        unmockkStatic("com.only4.cap4k.ddd.core.share.misc.ClassUtils")
        unmockkObject(AggregateFactorySupervisor)
        unmockkObject(JpaAggregatePredicateSupport)
    }

    @Test
    @DisplayName("创建聚合应该通过聚合工厂创建实体并包装为聚合")
    fun `create should create entity through aggregate factory and wrap in aggregate`() {
        val payload = TestAggregatePayload("test", 42)

        val result = supervisor.create(TestAggregate::class.java, payload)

        assertNotNull(result)
        assertEquals("test", result.getName())
        assertEquals(42, result.getValue())
        assertEquals(0L, result.getId()) // 载荷构造器设置的默认ID
    }

    @Test
    @DisplayName("通过ID列表获取聚合应该正确查找并包装实体")
    fun `getByIds should find entities and wrap them in aggregates`() {
        val ids = listOf(TestId(1L), TestId(2L))
        val entities = listOf(
            TestEntity(1L, "test1", 10),
            TestEntity(2L, "test2", 20)
        )

        every {
            resolveGenericTypeClass(
                any(), 0, Id::class.java, Id.Default::class.java
            )
        } returns TestAggregate::class.java

        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every {
            mockRepositorySupervisor.find(
                any<Predicate<TestEntity>>(),
                any<Collection<OrderInfo>>(),
                true
            )
        } returns entities

        val result = supervisor.getByIds(ids, true)

        assertEquals(2, result.size)
        assertEquals(1L, result[0].getId())
        assertEquals("test1", result[0].getName())
        assertEquals(2L, result[1].getId())
        assertEquals("test2", result[1].getName())
    }

    @Test
    @DisplayName("通过空ID列表获取聚合应该返回空列表")
    fun `getByIds with empty ids should return empty list`() {
        val emptyIds = emptyList<TestId>()

        val result = supervisor.getByIds(emptyIds, true)

        assertTrue(result.isEmpty())
        verify(exactly = 0) {
            mockRepositorySupervisor.find(
                any<Predicate<TestEntity>>(),
                any<Collection<OrderInfo>>(),
                any()
            )
        }
    }

    @Test
    @DisplayName("使用排序查找聚合应该正确处理并包装实体")
    fun `find with orders should find entities and wrap them in aggregates`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val orders = listOf(OrderInfo.asc("name"))
        val entities = listOf(TestEntity(1L, "test", 10))

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.find(any<Predicate<TestEntity>>(), orders, false) } returns entities

        val result = supervisor.find(predicate, orders, false)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].getId())
        assertEquals("test", result[0].getName())
        verify { mockRepositorySupervisor.find(any<Predicate<TestEntity>>(), orders, false) }
    }

    @Test
    @DisplayName("使用排序查找聚合时空排序应该正常处理")
    fun `find with null orders should handle gracefully`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val entities = listOf(TestEntity(1L, "test", 10))

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every {
            mockRepositorySupervisor.find(
                predicate = any<Predicate<TestEntity>>(),
                persist = false
            )
        } returns entities

        val result = supervisor.find(predicate = predicate, persist = false)

        assertEquals(1, result.size)
        verify { mockRepositorySupervisor.find(predicate = any<Predicate<TestEntity>>(), persist = false) }
    }

    @Test
    @DisplayName("使用分页参数查找聚合应该正确处理并包装实体")
    fun `find with pageParam should find entities and wrap them in aggregates`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val pageParam = PageParam.of(1, 10)
        val entities = listOf(TestEntity(1L, "test", 10))

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.find(any<Predicate<TestEntity>>(), pageParam, true) } returns entities

        val result = supervisor.find(predicate, pageParam, true)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].getId())
        verify { mockRepositorySupervisor.find(any<Predicate<TestEntity>>(), pageParam, true) }
    }

    @Test
    @DisplayName("查找单个聚合存在时应该返回包装的Optional聚合")
    fun `findOne should return wrapped optional aggregate when entity exists`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val entity = TestEntity(1L, "test", 10)

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), false) } returns Optional.of(entity)

        val result = supervisor.findOne(predicate, false)

        assertTrue(result.isPresent)
        assertEquals(1L, result.get().getId())
        assertEquals("test", result.get().getName())
        verify { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), false) }
    }

    @Test
    @DisplayName("查找单个聚合不存在时应该返回空Optional")
    fun `findOne should return empty optional when entity does not exist`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), false) } returns Optional.empty()

        val result = supervisor.findOne(predicate, false)

        assertFalse(result.isPresent)
        verify { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), false) }
    }

    @Test
    @DisplayName("查找第一个聚合存在时应该返回包装的Optional聚合")
    fun `findFirst should return wrapped optional aggregate when entity exists`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val orders = listOf(OrderInfo.desc("id"))
        val entity = TestEntity(1L, "test", 10)

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findFirst(any<Predicate<TestEntity>>(), orders, true) } returns Optional.of(
            entity
        )

        val result = supervisor.findFirst(predicate, orders, true)

        assertTrue(result.isPresent)
        assertEquals(1L, result.get().getId())
        verify { mockRepositorySupervisor.findFirst(any<Predicate<TestEntity>>(), orders, true) }
    }

    @Test
    @DisplayName("查找第一个聚合不存在时应该返回空Optional")
    fun `findFirst should return empty optional when entity does not exist`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val orders = listOf(OrderInfo.asc("name"))

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every {
            mockRepositorySupervisor.findFirst(
                any<Predicate<TestEntity>>(),
                orders,
                false
            )
        } returns Optional.empty()

        val result = supervisor.findFirst(predicate, orders, false)

        assertFalse(result.isPresent)
        verify { mockRepositorySupervisor.findFirst(any<Predicate<TestEntity>>(), orders, false) }
    }

    @Test
    @DisplayName("查找分页聚合应该返回转换后的分页数据")
    fun `findPage should return transformed page data`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val pageParam = PageParam.of(2, 5)
        val entities = listOf(
            TestEntity(1L, "test1", 10),
            TestEntity(2L, "test2", 20)
        )
        val pageData = PageData.create(pageParam, 10L, entities)

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findPage(any<Predicate<TestEntity>>(), pageParam, true) } returns pageData

        val result = supervisor.findPage(predicate, pageParam, true)

        assertEquals(2, result.list.size)
        assertEquals(1L, result.list[0].getId())
        assertEquals(2L, result.list[1].getId())
        verify { mockRepositorySupervisor.findPage(any<Predicate<TestEntity>>(), pageParam, true) }
    }

    @Test
    @DisplayName("通过ID列表删除聚合应该正确删除并返回包装的聚合")
    fun `removeByIds should remove entities and return wrapped aggregates`() {
        val ids = listOf(TestId(1L), TestId(2L))
        val entities = listOf(
            TestEntity(1L, "test1", 10),
            TestEntity(2L, "test2", 20)
        )

        every {
            resolveGenericTypeClass(
                any(), 0, Id::class.java, Id.Default::class.java
            )
        } returns TestAggregate::class.java

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<*, *>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<*, *>>()) } returns mockk()
        every { mockRepositorySupervisor.remove(any<Predicate<TestEntity>>()) } returns entities

        val result = supervisor.removeByIds(ids)

        assertEquals(2, result.size)
        assertEquals(1L, result[0].getId())
        assertEquals(2L, result[1].getId())
        verify { mockRepositorySupervisor.remove(any<Predicate<TestEntity>>()) }
    }

    @Test
    @DisplayName("通过空ID列表删除聚合应该返回空列表")
    fun `removeByIds with empty ids should return empty list`() {
        val emptyIds = emptyList<TestId>()

        val result = supervisor.removeByIds(emptyIds)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { mockRepositorySupervisor.remove(any<Predicate<TestEntity>>()) }
    }

    @Test
    @DisplayName("删除聚合应该正确删除并返回包装的聚合")
    fun `remove should remove entities and return wrapped aggregates`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val entities = listOf(TestEntity(1L, "test", 10))

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.remove(any<Predicate<TestEntity>>()) } returns entities

        val result = supervisor.remove(predicate)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].getId())
        verify { mockRepositorySupervisor.remove(any<Predicate<TestEntity>>()) }
    }

    @Test
    @DisplayName("限制删除聚合应该正确删除并返回包装的聚合")
    fun `remove with limit should remove entities and return wrapped aggregates`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val entities = listOf(TestEntity(1L, "test", 10))
        val limit = 5

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.remove(any<Predicate<TestEntity>>(), limit) } returns entities

        val result = supervisor.remove(predicate, limit)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].getId())
        verify { mockRepositorySupervisor.remove(any<Predicate<TestEntity>>(), limit) }
    }

    @Test
    @DisplayName("计数聚合应该返回正确的数量")
    fun `count should return correct count`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val expectedCount = 42L

        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.count(any<Predicate<TestEntity>>()) } returns expectedCount

        val result = supervisor.count(predicate)

        assertEquals(expectedCount, result)
        verify { mockRepositorySupervisor.count(any<Predicate<TestEntity>>()) }
    }

    @Test
    @DisplayName("检查聚合存在性应该返回true当聚合存在时")
    fun `exists should return true when aggregates exist`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()

        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.exists(any<Predicate<TestEntity>>()) } returns true

        val result = supervisor.exists(predicate)

        assertTrue(result)
        verify { mockRepositorySupervisor.exists(any<Predicate<TestEntity>>()) }
    }

    @Test
    @DisplayName("检查聚合存在性应该返回false当聚合不存在时")
    fun `exists should return false when aggregates do not exist`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()

        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.exists(any<Predicate<TestEntity>>()) } returns false

        val result = supervisor.exists(predicate)

        assertFalse(result)
        verify { mockRepositorySupervisor.exists(any<Predicate<TestEntity>>()) }
    }

    @Test
    @DisplayName("创建聚合时构造器不存在应该抛出异常")
    fun `create should throw exception when constructor does not exist`() {
        class InvalidPayload(val invalidField: String) : AggregatePayload<TestEntity>

        val payload = InvalidPayload("test")

        assertThrows<RuntimeException> {
            supervisor.create(TestAggregate::class.java, payload)
        }
    }

    @Test
    @DisplayName("实例化聚合失败时应该抛出运行时异常")
    fun `newInstance should throw RuntimeException when aggregate instantiation fails`() {
        val predicate = mockk<AggregatePredicate<NonInstantiableAggregate, TestEntity>>()
        val entity = TestEntity(1L, "test", 10)

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<NonInstantiableAggregate, TestEntity>>()) } returns NonInstantiableAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<NonInstantiableAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), false) } returns Optional.of(entity)

        assertThrows<RuntimeException> {
            supervisor.findOne(predicate, false)
        }
    }

    @Test
    @DisplayName("应该正确处理不同类型的聚合")
    fun `should handle different aggregate types correctly`() {
        // 另一个测试聚合类
        class AnotherAggregate : Aggregate<TestEntity> {
            private lateinit var entity: TestEntity

            constructor()

            override fun _wrap(entity: TestEntity) {
                this.entity = entity
            }

            override fun _unwrap(): TestEntity = entity
            fun getEntityId(): Long = entity.id
        }

        val predicate = mockk<AggregatePredicate<AnotherAggregate, TestEntity>>()
        val entity = TestEntity(1L, "test", 10)

        every { JpaAggregatePredicateSupport.reflectAggregateClass(predicate) } returns AnotherAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<AnotherAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), false) } returns Optional.of(entity)

        val result = supervisor.findOne(predicate, false)

        assertTrue(result.isPresent)
        assertEquals(1L, result.get().getEntityId())
    }

    @Test
    @DisplayName("应该正确处理复杂的分页场景")
    fun `should handle complex pagination scenarios correctly`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val pageParam = PageParam.of(3, 15)
        val entities = (1..15).map { TestEntity(it.toLong(), "test$it", it * 10) }
        val pageData = PageData.create(pageParam, 100L, entities)

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findPage(any<Predicate<TestEntity>>(), pageParam, false) } returns pageData

        val result = supervisor.findPage(predicate, pageParam, false)

        assertEquals(15, result.list.size)
        assertEquals(1L, result.list[0].getId())
        assertEquals(15L, result.list[14].getId())
    }

    @Test
    @DisplayName("应该正确处理空结果场景")
    fun `should handle empty results correctly`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val orders = listOf(OrderInfo.asc("name"))

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.find(any<Predicate<TestEntity>>(), orders, false) } returns emptyList()

        val result = supervisor.find(predicate, orders, false)

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("应该正确处理持久化标志")
    fun `should handle persist flag correctly`() {
        val predicate = mockk<AggregatePredicate<TestAggregate, TestEntity>>()
        val entity = TestEntity(1L, "test", 10)

        every { JpaAggregatePredicateSupport.reflectAggregateClass(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns TestAggregate::class.java
        every { JpaAggregatePredicateSupport.getPredicate(any<AggregatePredicate<TestAggregate, TestEntity>>()) } returns mockk()
        every { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), true) } returns Optional.of(entity)

        val result = supervisor.findOne(predicate, true)

        assertTrue(result.isPresent)
        verify { mockRepositorySupervisor.findOne(any<Predicate<TestEntity>>(), true) }
    }
}
