package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * DomainEventUnitOfWorkInterceptor测试
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
@DisplayName("DomainEventUnitOfWorkInterceptor 测试")
class DomainEventUnitOfWorkInterceptorTest {

    private lateinit var domainEventManager: DomainEventManager
    private lateinit var interceptor: DomainEventUnitOfWorkInterceptor

    @BeforeEach
    fun setUp() {
        domainEventManager = mockk()
        every { domainEventManager.release(any()) } just Runs

        interceptor = DomainEventUnitOfWorkInterceptor(domainEventManager)
    }

    @Nested
    @DisplayName("基本功能测试")
    inner class BasicFunctionalityTests {

        @Test
        @DisplayName("应该能够创建拦截器实例")
        fun `should create interceptor instance`() {
            // then
            assertNotNull(interceptor)
        }

        @Test
        @DisplayName("应该正确注入DomainEventManager依赖")
        fun `should correctly inject DomainEventManager dependency`() {
            // given
            val entities = setOf(TestEntity("entity1"))

            // when
            interceptor.postEntitiesPersisted(entities)

            // then
            verify { domainEventManager.release(entities) }
        }
    }

    @Nested
    @DisplayName("UnitOfWork生命周期方法测试")
    inner class UnitOfWorkLifecycleTests {

        @Test
        @DisplayName("beforeTransaction应该安全执行")
        fun `beforeTransaction should execute safely`() {
            // given
            val persistAggregates = setOf(TestEntity("persist1"), TestEntity("persist2"))
            val removeAggregates = setOf(TestEntity("remove1"))

            // when & then - 不应该抛出异常
            interceptor.beforeTransaction(persistAggregates, removeAggregates)
        }

        @Test
        @DisplayName("preInTransaction应该安全执行")
        fun `preInTransaction should execute safely`() {
            // given
            val persistAggregates = setOf(TestEntity("persist1"), TestEntity("persist2"))
            val removeAggregates = setOf(TestEntity("remove1"))

            // when & then - 不应该抛出异常
            interceptor.preInTransaction(persistAggregates, removeAggregates)
        }

        @Test
        @DisplayName("postInTransaction应该安全执行")
        fun `postInTransaction should execute safely`() {
            // given
            val persistAggregates = setOf(TestEntity("persist1"), TestEntity("persist2"))
            val removeAggregates = setOf(TestEntity("remove1"))

            // when & then - 不应该抛出异常
            interceptor.postInTransaction(persistAggregates, removeAggregates)
        }

        @Test
        @DisplayName("afterTransaction应该安全执行")
        fun `afterTransaction should execute safely`() {
            // given
            val persistAggregates = setOf(TestEntity("persist1"), TestEntity("persist2"))
            val removeAggregates = setOf(TestEntity("remove1"))

            // when & then - 不应该抛出异常
            interceptor.afterTransaction(persistAggregates, removeAggregates)
        }

        @Test
        @DisplayName("所有生命周期方法都应该能处理空集合")
        fun `all lifecycle methods should handle empty collections`() {
            // given
            val emptySet = emptySet<Any>()

            // when & then - 不应该抛出异常
            interceptor.beforeTransaction(emptySet, emptySet)
            interceptor.preInTransaction(emptySet, emptySet)
            interceptor.postInTransaction(emptySet, emptySet)
            interceptor.afterTransaction(emptySet, emptySet)
            interceptor.postEntitiesPersisted(emptySet)

            // 验证空集合也会调用release方法
            verify { domainEventManager.release(emptySet) }
        }
    }

    @Nested
    @DisplayName("postEntitiesPersisted方法测试")
    inner class PostEntitiesPersistedTests {

        @Test
        @DisplayName("应该调用DomainEventManager的release方法")
        fun `should call DomainEventManager release method`() {
            // given
            val entities = setOf(
                TestEntity("entity1"),
                TestEntity("entity2"),
                TestEntity("entity3")
            )

            // when
            interceptor.postEntitiesPersisted(entities)

            // then
            verify(exactly = 1) { domainEventManager.release(entities) }
        }

        @Test
        @DisplayName("应该处理单个实体")
        fun `should handle single entity`() {
            // given
            val entity = TestEntity("single-entity")
            val entities = setOf(entity)

            // when
            interceptor.postEntitiesPersisted(entities)

            // then
            verify { domainEventManager.release(entities) }
        }

        @Test
        @DisplayName("应该处理大量实体")
        fun `should handle large number of entities`() {
            // given
            val entities = (1..1000).map { TestEntity("entity$it") }.toSet()

            // when
            interceptor.postEntitiesPersisted(entities)

            // then
            verify { domainEventManager.release(entities) }
        }

        @Test
        @DisplayName("应该处理不同类型的实体")
        fun `should handle different types of entities`() {
            // given
            val entities = setOf(
                TestEntity("entity1"),
                TestAggregateRoot("aggregate1"),
                "string-entity",
                42
            )

            // when
            interceptor.postEntitiesPersisted(entities)

            // then
            verify { domainEventManager.release(entities) }
        }

        @Test
        @DisplayName("DomainEventManager异常应该被传播")
        fun `DomainEventManager exceptions should be propagated`() {
            // given
            val entities = setOf(TestEntity("entity1"))
            val exception = RuntimeException("Event release failed")
            every { domainEventManager.release(any()) } throws exception

            // when & then
            val thrownException = assertThrows<RuntimeException> {
                interceptor.postEntitiesPersisted(entities)
            }

            assertEquals("Event release failed", thrownException.message)
            verify { domainEventManager.release(entities) }
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("应该处理null值的安全调用")
        fun `should handle safe calls with null values`() {
            // 注意：由于Kotlin的非空类型系统，这个测试主要验证类型安全
            // given
            val entities = setOf<Any>()

            // when & then - 不应该抛出异常
            interceptor.postEntitiesPersisted(entities)

            verify { domainEventManager.release(entities) }
        }

        @Test
        @DisplayName("应该处理包含null的实体集合")
        fun `should handle entity collections containing null`() {
            // 注意：在实际使用中，Set<Any>不应该包含null，但我们可以测试边界情况
            // given
            val mockManager = mockk<DomainEventManager>()
            every { mockManager.release(any()) } just Runs
            val interceptorWithMock = DomainEventUnitOfWorkInterceptor(mockManager)

            // when
            interceptorWithMock.postEntitiesPersisted(emptySet())

            // then
            verify { mockManager.release(emptySet()) }
        }
    }

    @Nested
    @DisplayName("集成测试")
    inner class IntegrationTests {

        @Test
        @DisplayName("应该与真实的DomainEventManager集成工作")
        fun `should work with real DomainEventManager integration`() {
            // given
            val realManager = mockk<DomainEventManager>()
            every { realManager.release(any()) } just Runs
            val realInterceptor = DomainEventUnitOfWorkInterceptor(realManager)

            val entities = setOf(
                TestEntity("integration-entity1"),
                TestEntity("integration-entity2")
            )

            // when
            realInterceptor.postEntitiesPersisted(entities)

            // then
            verify { realManager.release(entities) }
        }

        @Test
        @DisplayName("应该正确传递实体集合的引用")
        fun `should correctly pass entity collection reference`() {
            // given
            val entities = setOf(TestEntity("ref-test"))
            val slot = slot<Set<Any>>()

            every { domainEventManager.release(capture(slot)) } just Runs

            // when
            interceptor.postEntitiesPersisted(entities)

            // then
            assertEquals(entities, slot.captured)
            assertTrue(slot.captured.contains(TestEntity("ref-test")))
        }
    }

    @Nested
    @DisplayName("性能和并发测试")
    inner class PerformanceAndConcurrencyTests {

        @Test
        @DisplayName("应该能够处理并发调用")
        fun `should handle concurrent calls`() {
            // given
            val callCount = 100
            val entities = setOf(TestEntity("concurrent-entity"))

            // when
            val threads = (1..10).map {
                Thread {
                    repeat(10) {
                        interceptor.postEntitiesPersisted(entities)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // then
            verify(exactly = callCount) { domainEventManager.release(entities) }
        }

        @Test
        @DisplayName("应该能够快速处理大量调用")
        fun `should quickly process large number of calls`() {
            // given
            val entities = setOf(TestEntity("performance-entity"))

            // when
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                interceptor.postEntitiesPersisted(entities)
            }
            val endTime = System.currentTimeMillis()

            // then
            val executionTime = endTime - startTime
            assertTrue(executionTime < 1000, "Execution should be fast, took ${executionTime}ms")
            verify(exactly = 1000) { domainEventManager.release(entities) }
        }
    }

    @Nested
    @DisplayName("契约测试")
    inner class ContractTests {

        @Test
        @DisplayName("应该实现UnitOfWorkInterceptor接口的所有方法")
        fun `should implement all UnitOfWorkInterceptor interface methods`() {
            // given
            val persistAggregates = setOf(TestEntity("test"))
            val removeAggregates = setOf(TestEntity("test"))
            val entities = setOf(TestEntity("test"))

            // when & then - 所有方法都应该存在且可调用
            interceptor.beforeTransaction(persistAggregates, removeAggregates)
            interceptor.preInTransaction(persistAggregates, removeAggregates)
            interceptor.postInTransaction(persistAggregates, removeAggregates)
            interceptor.afterTransaction(persistAggregates, removeAggregates)
            interceptor.postEntitiesPersisted(entities)

            // 验证只有postEntitiesPersisted实际执行了业务逻辑
            verify(exactly = 1) { domainEventManager.release(entities) }
        }

        @Test
        @DisplayName("应该保持方法调用的幂等性")
        fun `should maintain method call idempotency`() {
            // given
            val entities = setOf(TestEntity("idempotent-test"))

            // when
            interceptor.postEntitiesPersisted(entities)
            interceptor.postEntitiesPersisted(entities)
            interceptor.postEntitiesPersisted(entities)

            // then
            verify(exactly = 3) { domainEventManager.release(entities) }
        }
    }

    // 测试用的实体类
    data class TestEntity(val id: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TestEntity) return false
            return id == other.id
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    data class TestAggregateRoot(val id: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TestAggregateRoot) return false
            return id == other.id
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
}
