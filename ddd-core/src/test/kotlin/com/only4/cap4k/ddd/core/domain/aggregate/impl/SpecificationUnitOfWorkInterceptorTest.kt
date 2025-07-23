package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.domain.aggregate.Specification
import com.only4.cap4k.ddd.core.domain.aggregate.SpecificationManager
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("SpecificationUnitOfWorkInterceptor 测试")
class SpecificationUnitOfWorkInterceptorTest {

    private lateinit var interceptor: SpecificationUnitOfWorkInterceptor
    private lateinit var specificationManager: SpecificationManager

    @BeforeEach
    fun setup() {
        specificationManager = mockk()
        interceptor = SpecificationUnitOfWorkInterceptor(specificationManager)
    }

    @Nested
    @DisplayName("beforeTransaction方法测试")
    inner class BeforeTransactionTests {

        @Test
        @DisplayName("应该对所有待持久化的聚合调用规格验证")
        fun `should validate all aggregates to be persisted`() {
            // Given
            val aggregates = setOf(
                TestAggregate("1", "valid-data-1"),
                TestAggregate("2", "valid-data-2")
            )
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction<Any>(any()) } returns
                    Specification.Result.pass()

            // When
            interceptor.beforeTransaction(aggregates, emptySet)

            // Then
            verify(exactly = 2) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify { specificationManager.specifyBeforeTransaction(aggregates.first()) }
            verify { specificationManager.specifyBeforeTransaction(aggregates.last()) }
        }

        @Test
        @DisplayName("当规格验证失败时应该抛出DomainException")
        fun `should throw DomainException when validation fails`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "invalid-data"))
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction<Any>(any()) } returns
                    Specification.Result.fail("Validation failed for entity 1")

            // When & Then
            val exception = assertThrows(DomainException::class.java) {
                interceptor.beforeTransaction(aggregates, emptySet)
            }

            assertEquals("Validation failed for entity 1", exception.message)
        }

        @Test
        @DisplayName("当待持久化集合为空时不应该执行验证")
        fun `should not perform validation when persist set is empty`() {
            // Given
            val emptySet = emptySet<Any>()

            // When
            interceptor.beforeTransaction(emptySet, emptySet)

            // Then
            verify(exactly = 0) { specificationManager.specifyBeforeTransaction<Any>(any()) }
        }

        @Test
        @DisplayName("应该忽略待删除的聚合")
        fun `should ignore aggregates to be removed`() {
            // Given
            val persistSet = emptySet<Any>()
            val removeSet = setOf(TestAggregate("1", "data"))

            // When
            interceptor.beforeTransaction(persistSet, removeSet)

            // Then
            verify(exactly = 0) { specificationManager.specifyBeforeTransaction<Any>(any()) }
        }

        @Test
        @DisplayName("当第一个聚合验证失败时应该立即抛出异常")
        fun `should throw exception immediately when first aggregate validation fails`() {
            // Given
            val aggregates = setOf(
                TestAggregate("1", "invalid-data"),
                TestAggregate("2", "valid-data")
            )
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction(TestAggregate("1", "invalid-data")) } returns
                    Specification.Result.fail("First aggregate validation failed")
            every { specificationManager.specifyBeforeTransaction(TestAggregate("2", "valid-data")) } returns
                    Specification.Result.pass()

            // When & Then
            val exception = assertThrows(DomainException::class.java) {
                interceptor.beforeTransaction(aggregates, emptySet)
            }

            assertEquals("First aggregate validation failed", exception.message)
        }
    }

    @Nested
    @DisplayName("preInTransaction方法测试")
    inner class PreInTransactionTests {

        @Test
        @DisplayName("应该对所有待持久化的聚合调用事务内规格验证")
        fun `should validate all aggregates in transaction`() {
            // Given
            val aggregates = setOf(
                TestAggregate("1", "valid-data-1"),
                TestAggregate("2", "valid-data-2")
            )
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyInTransaction<Any>(any()) } returns
                    Specification.Result.pass()

            // When
            interceptor.preInTransaction(aggregates, emptySet)

            // Then
            verify(exactly = 2) { specificationManager.specifyInTransaction<Any>(any()) }
            verify { specificationManager.specifyInTransaction(aggregates.first()) }
            verify { specificationManager.specifyInTransaction(aggregates.last()) }
        }

        @Test
        @DisplayName("当事务内验证失败时应该抛出DomainException")
        fun `should throw DomainException when validation fails in transaction`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "invalid-data"))
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyInTransaction<Any>(any()) } returns
                    Specification.Result.fail("Validation failed in transaction")

            // When & Then
            val exception = assertThrows(DomainException::class.java) {
                interceptor.preInTransaction(aggregates, emptySet)
            }

            assertEquals("Validation failed in transaction", exception.message)
        }

        @Test
        @DisplayName("当待持久化集合为空时不应该执行验证")
        fun `should not perform validation when persist set is empty in transaction`() {
            // Given
            val emptySet = emptySet<Any>()

            // When
            interceptor.preInTransaction(emptySet, emptySet)

            // Then
            verify(exactly = 0) { specificationManager.specifyInTransaction<Any>(any()) }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 3, 5, 10])
        @DisplayName("应该处理不同大小的聚合集合")
        fun `should handle different sized aggregate sets`(size: Int) {
            // Given
            val aggregates = (1..size).map { TestAggregate(it.toString(), "data-$it") }.toSet()
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyInTransaction<Any>(any()) } returns
                    Specification.Result.pass()

            // When
            interceptor.preInTransaction(aggregates, emptySet)

            // Then
            verify(exactly = size) { specificationManager.specifyInTransaction<Any>(any()) }
        }
    }

    @Nested
    @DisplayName("后置钩子方法测试")
    inner class PostTransactionHooksTests {

        @Test
        @DisplayName("postInTransaction应该为空实现")
        fun `postInTransaction should be empty implementation`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "data"))
            val emptySet = emptySet<Any>()

            // When
            assertDoesNotThrow {
                interceptor.postInTransaction(aggregates, emptySet)
            }

            // Then - 验证没有调用SpecificationManager的任何方法
            verify(exactly = 0) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify(exactly = 0) { specificationManager.specifyInTransaction<Any>(any()) }
        }

        @Test
        @DisplayName("afterTransaction应该为空实现")
        fun `afterTransaction should be empty implementation`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "data"))
            val emptySet = emptySet<Any>()

            // When
            assertDoesNotThrow {
                interceptor.afterTransaction(aggregates, emptySet)
            }

            // Then - 验证没有调用SpecificationManager的任何方法
            verify(exactly = 0) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify(exactly = 0) { specificationManager.specifyInTransaction<Any>(any()) }
        }

        @Test
        @DisplayName("postEntitiesPersisted应该为空实现")
        fun `postEntitiesPersisted should be empty implementation`() {
            // Given
            val entities = setOf(TestAggregate("1", "data"))

            // When
            assertDoesNotThrow {
                interceptor.postEntitiesPersisted(entities)
            }

            // Then - 验证没有调用SpecificationManager的任何方法
            verify(exactly = 0) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify(exactly = 0) { specificationManager.specifyInTransaction<Any>(any()) }
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("应该正确处理所有方法的空集合参数")
        fun `should handle empty collections for all methods`() {
            // When & Then - 所有方法都应该正常执行，不抛出异常
            assertDoesNotThrow {
                interceptor.beforeTransaction(emptySet(), emptySet())
                interceptor.preInTransaction(emptySet(), emptySet())
                interceptor.postInTransaction(emptySet(), emptySet())
                interceptor.afterTransaction(emptySet(), emptySet())
                interceptor.postEntitiesPersisted(emptySet())
            }

            // 验证没有调用任何规格验证
            verify(exactly = 0) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify(exactly = 0) { specificationManager.specifyInTransaction<Any>(any()) }
        }

        @Test
        @DisplayName("应该处理混合类型的聚合集合")
        fun `should handle mixed type aggregate collections`() {
            // Given
            val mixedAggregates = setOf(
                TestAggregate("1", "data1"),
                AnotherEntity("2", "data2"),
                TestAggregate("3", "data3")
            )
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction<Any>(any()) } returns
                    Specification.Result.pass()
            every { specificationManager.specifyInTransaction<Any>(any()) } returns
                    Specification.Result.pass()

            // When
            interceptor.beforeTransaction(mixedAggregates, emptySet)
            interceptor.preInTransaction(mixedAggregates, emptySet)

            // Then
            verify(exactly = 3) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify(exactly = 3) { specificationManager.specifyInTransaction<Any>(any()) }
        }

        @Test
        @DisplayName("应该正确处理只有待删除聚合的情况")
        fun `should handle only remove aggregates correctly`() {
            // Given
            val persistSet = emptySet<Any>()
            val removeSet = setOf(TestAggregate("1", "data"), TestAggregate("2", "data"))

            // When
            interceptor.beforeTransaction(persistSet, removeSet)
            interceptor.preInTransaction(persistSet, removeSet)

            // Then
            verify(exactly = 0) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify(exactly = 0) { specificationManager.specifyInTransaction<Any>(any()) }
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("应该正确处理SpecificationManager抛出的运行时异常")
        fun `should properly handle runtime exceptions from SpecificationManager`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "data"))
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction<Any>(any()) } throws
                    RuntimeException("Unexpected specification error")

            // When & Then
            val exception = assertThrows(RuntimeException::class.java) {
                interceptor.beforeTransaction(aggregates, emptySet)
            }

            assertEquals("Unexpected specification error", exception.message)
        }

        @Test
        @DisplayName("应该按聚合顺序处理验证失败")
        fun `should handle validation failures in aggregate order`() {
            // Given
            val aggregate1 = TestAggregate("1", "valid-data")
            val aggregate2 = TestAggregate("2", "invalid-data")
            val aggregates = setOf(aggregate1, aggregate2)
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction(aggregate1) } returns
                    Specification.Result.pass()
            every { specificationManager.specifyBeforeTransaction(aggregate2) } returns
                    Specification.Result.fail("Aggregate 2 validation failed")

            // When & Then
            val exception = assertThrows(DomainException::class.java) {
                interceptor.beforeTransaction(aggregates, emptySet)
            }

            assertEquals("Aggregate 2 validation failed", exception.message)
            verify { specificationManager.specifyBeforeTransaction(aggregate1) }
            verify { specificationManager.specifyBeforeTransaction(aggregate2) }
        }

        @Test
        @DisplayName("事务内验证和事务前验证应该独立处理异常")
        fun `should handle exceptions independently for before and in transaction validation`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "data"))
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction<Any>(any()) } returns
                    Specification.Result.pass()
            every { specificationManager.specifyInTransaction<Any>(any()) } returns
                    Specification.Result.fail("In-transaction validation failed")

            // When & Then
            // beforeTransaction 应该成功
            assertDoesNotThrow {
                interceptor.beforeTransaction(aggregates, emptySet)
            }

            // preInTransaction 应该失败
            val exception = assertThrows(DomainException::class.java) {
                interceptor.preInTransaction(aggregates, emptySet)
            }

            assertEquals("In-transaction validation failed", exception.message)
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    inner class ConcurrencyTests {

        @Test
        @DisplayName("应该线程安全地处理并发验证请求")
        fun `should handle concurrent validation requests thread-safely`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "data"))
            val emptySet = emptySet<Any>()

            every { specificationManager.specifyBeforeTransaction<Any>(any()) } returns
                    Specification.Result.pass()
            every { specificationManager.specifyInTransaction<Any>(any()) } returns
                    Specification.Result.pass()

            // When
            val threads = List(10) {
                Thread {
                    interceptor.beforeTransaction(aggregates, emptySet)
                    interceptor.preInTransaction(aggregates, emptySet)
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Then
            verify(exactly = 10) { specificationManager.specifyBeforeTransaction<Any>(any()) }
            verify(exactly = 10) { specificationManager.specifyInTransaction<Any>(any()) }
        }
    }

    @Nested
    @DisplayName("接口契约测试")
    inner class InterfaceContractTests {

        @Test
        @DisplayName("应该正确实现UnitOfWorkInterceptor接口的所有方法")
        fun `should properly implement all UnitOfWorkInterceptor interface methods`() {
            // Given
            val interceptor = SpecificationUnitOfWorkInterceptor(specificationManager)

            // Then
            assertTrue(interceptor is com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor)

            // 验证所有方法都存在且可以调用
            assertDoesNotThrow {
                interceptor.beforeTransaction(emptySet(), emptySet())
                interceptor.preInTransaction(emptySet(), emptySet())
                interceptor.postInTransaction(emptySet(), emptySet())
                interceptor.afterTransaction(emptySet(), emptySet())
                interceptor.postEntitiesPersisted(emptySet())
            }
        }

        @Test
        @DisplayName("应该正确处理null参数")
        fun `should handle null parameters gracefully`() {
            // Given
            val aggregates = setOf(TestAggregate("1", "data"))

            every { specificationManager.specifyBeforeTransaction<Any>(any()) } returns
                    Specification.Result.pass()
            every { specificationManager.specifyInTransaction<Any>(any()) } returns
                    Specification.Result.pass()

            // When & Then - 不应该抛出异常
            assertDoesNotThrow {
                interceptor.beforeTransaction(aggregates, emptySet())
                interceptor.preInTransaction(aggregates, emptySet())
                interceptor.postInTransaction(aggregates, emptySet())
                interceptor.afterTransaction(aggregates, emptySet())
                interceptor.postEntitiesPersisted(aggregates)
            }
        }
    }

    // Test data classes
    data class TestAggregate(val id: String, val data: String)
    data class AnotherEntity(val id: String, val data: String)
}
