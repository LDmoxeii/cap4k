package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.domain.aggregate.Specification
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.Order

@DisplayName("DefaultSpecificationManager 测试")
class DefaultSpecificationManagerTest {

    private lateinit var manager: DefaultSpecificationManager

    @Test
    @DisplayName("应该按顺序验证实体规格并返回通过结果")
    fun `should validate entity specifications in order and return pass`() {
        // Given
        val spec1 = TestSpecification(true, false, "spec1")
        val spec2 = TestSpecification(true, false, "spec2")
        manager = DefaultSpecificationManager(listOf(spec1, spec2))

        val entity = TestEntity("test-id", "valid-data")

        // When
        val result = manager.specifyInTransaction(entity)

        // Then
        assertTrue(result.passed)
        assertEquals("", result.message)
        assertTrue(spec1.wasCalled)
        assertTrue(spec2.wasCalled)
    }

    @Test
    @DisplayName("当第一个规格验证失败时应该立即返回失败结果")
    fun `should return failure immediately when first specification fails`() {
        // Given
        val spec1 = TestSpecification(false, false, "First validation failed")
        val spec2 = TestSpecification(true, false, "spec2")
        manager = DefaultSpecificationManager(listOf(spec1, spec2))

        val entity = TestEntity("test-id", "invalid-data")

        // When
        val result = manager.specifyInTransaction(entity)

        // Then
        assertFalse(result.passed)
        assertEquals("First validation failed", result.message)
        assertTrue(spec1.wasCalled)
        assertFalse(spec2.wasCalled) // 第二个规格不应该被调用
    }

    @Test
    @DisplayName("应该跳过beforeTransaction=true的规格在事务内验证")
    fun `should skip specifications with beforeTransaction=true in transaction`() {
        // Given
        val spec1 = TestSpecification(true, true, "spec1") // beforeTransaction = true
        val spec2 = TestSpecification(true, false, "spec2") // beforeTransaction = false
        manager = DefaultSpecificationManager(listOf(spec1, spec2))

        val entity = TestEntity("test-id", "data")

        // When
        val result = manager.specifyInTransaction(entity)

        // Then
        assertTrue(result.passed)
        assertFalse(spec1.wasCalled) // 应该被跳过
        assertTrue(spec2.wasCalled)
    }

    @Test
    @DisplayName("应该只验证beforeTransaction=true的规格在事务前验证")
    fun `should only validate specifications with beforeTransaction=true before transaction`() {
        // Given
        val spec1 = TestSpecification(true, false, "spec1") // beforeTransaction = false
        val spec2 = TestSpecification(true, true, "spec2")  // beforeTransaction = true
        manager = DefaultSpecificationManager(listOf(spec1, spec2))

        val entity = TestEntity("test-id", "data")

        // When
        val result = manager.specifyBeforeTransaction(entity)

        // Then
        assertTrue(result.passed)
        assertFalse(spec1.wasCalled) // 应该被跳过
        assertTrue(spec2.wasCalled)
    }

    @Test
    @DisplayName("当beforeTransaction规格失败时应该返回失败结果")
    fun `should return failure when beforeTransaction specification fails`() {
        // Given
        val spec1 = TestSpecification(false, true, "Before transaction validation failed")
        manager = DefaultSpecificationManager(listOf(spec1))

        val entity = TestEntity("test-id", "invalid-data")

        // When
        val result = manager.specifyBeforeTransaction(entity)

        // Then
        assertFalse(result.passed)
        assertEquals("Before transaction validation failed", result.message)
        assertTrue(spec1.wasCalled)
    }

    @Test
    @DisplayName("当没有匹配的规格时应该返回通过结果")
    fun `should return pass when no matching specifications found`() {
        // Given
        val spec1 = TestSpecification(true, false, "spec1")
        manager = DefaultSpecificationManager(listOf(spec1))
        val entity = UnknownEntity("unknown-id") // 不匹配的实体类型

        // When
        val result = manager.specifyInTransaction(entity)

        // Then
        assertTrue(result.passed)
        assertEquals("", result.message)
        assertFalse(spec1.wasCalled) // 不应该被调用，因为类型不匹配
    }

    @Test
    @DisplayName("当没有beforeTransaction=true的规格时应该返回通过结果")
    fun `should return pass when no beforeTransaction specifications`() {
        // Given
        val spec1 = TestSpecification(true, false, "spec1") // beforeTransaction = false
        manager = DefaultSpecificationManager(listOf(spec1))
        val entity = TestEntity("test-id", "data")

        // When
        val result = manager.specifyBeforeTransaction(entity)

        // Then
        assertTrue(result.passed)
        assertEquals("", result.message)
        assertFalse(spec1.wasCalled) // 不应该被调用
    }

    @Test
    @DisplayName("应该按Order注解的顺序执行规格")
    fun `should execute specifications in order annotation order`() {
        // Given
        globalCallCounter = 0 // 重置计数器
        val entity = TestEntity("test-id", "data")
        val orderedSpec1 = OrderedTestSpecification1()
        val orderedSpec2 = OrderedTestSpecification2()
        val orderedSpec3 = OrderedTestSpecification0()

        val orderedManager = DefaultSpecificationManager(listOf(orderedSpec1, orderedSpec2, orderedSpec3))

        // When
        val result = orderedManager.specifyInTransaction(entity)

        // Then
        assertTrue(result.passed)

        // 验证按正确顺序执行：spec3(0), spec1(1), spec2(2)
        assertTrue(orderedSpec3.wasCalled)
        assertTrue(orderedSpec1.wasCalled)
        assertTrue(orderedSpec2.wasCalled)

        // 验证执行顺序
        assertTrue(orderedSpec3.callOrder < orderedSpec1.callOrder)
        assertTrue(orderedSpec1.callOrder < orderedSpec2.callOrder)
    }

    @Test
    @DisplayName("当规格列表为空时应该返回通过结果")
    fun `should return pass when specification list is empty`() {
        // Given
        val emptyManager = DefaultSpecificationManager(emptyList())
        val entity = TestEntity("test-id", "data")

        // When
        val inTransactionResult = emptyManager.specifyInTransaction(entity)
        val beforeTransactionResult = emptyManager.specifyBeforeTransaction(entity)

        // Then
        assertTrue(inTransactionResult.passed)
        assertTrue(beforeTransactionResult.passed)
    }

    @Test
    @DisplayName("应该正确处理链式验证失败场景")
    fun `should handle chained validation failures correctly`() {
        // Given
        val entity = TestEntity("test-id", "invalid-data")
        val spec1 = TestSpecification(true, false, "First spec passed")
        val spec2 = TestSpecification(false, false, "Second spec failed")
        val spec3 = TestSpecification(true, false, "Third spec passed")

        val complexManager = DefaultSpecificationManager(listOf(spec1, spec2, spec3))

        // When
        val result = complexManager.specifyInTransaction(entity)

        // Then
        assertFalse(result.passed)
        assertEquals("Second spec failed", result.message)
        assertTrue(spec1.wasCalled)
        assertTrue(spec2.wasCalled)
        // 第三个规格不应该被调用，因为第二个失败了
        assertFalse(spec3.wasCalled)
    }

    @Test
    @DisplayName("应该正确解析规格的泛型类型")
    fun `should correctly resolve specification generic types`() {
        // Given
        val testEntitySpec = TestSpecification(true, false, "test spec")
        val anotherEntitySpec = AnotherEntitySpecification(true, false, "another spec")

        val typedManager = DefaultSpecificationManager(listOf(testEntitySpec, anotherEntitySpec))

        val testEntity = TestEntity("test-id", "data")
        val anotherEntity = AnotherEntity("another-id", "data")

        // When
        val testResult = typedManager.specifyInTransaction(testEntity)
        val anotherResult = typedManager.specifyInTransaction(anotherEntity)

        // Then
        assertTrue(testResult.passed)
        assertTrue(anotherResult.passed)
        assertTrue(testEntitySpec.wasCalled)
        assertTrue(anotherEntitySpec.wasCalled)
    }

    @Test
    @DisplayName("应该处理规格抛出异常的情况")
    fun `should handle specifications throwing exceptions`() {
        // Given
        val throwingSpec = ThrowingSpecification()
        manager = DefaultSpecificationManager(listOf(throwingSpec))
        val entity = TestEntity("test-id", "data")

        // When & Then
        assertThrows(RuntimeException::class.java) {
            manager.specifyInTransaction(entity)
        }
    }

    @Test
    @DisplayName("当实体为null时应该抛出异常")
    fun `should throw exception when entity is null`() {
        // Given
        val spec = TestSpecification(true, false, "spec")
        manager = DefaultSpecificationManager(listOf(spec))

        // When & Then
        assertThrows(NullPointerException::class.java) {
            manager.specifyInTransaction<TestEntity>(null!!)
        }
    }

    // Test data classes and helper specifications
    data class TestEntity(val id: String, val data: String)
    data class AnotherEntity(val id: String, val data: String)
    data class UnknownEntity(val id: String)

    class TestSpecification(
        private val shouldPass: Boolean,
        private val beforeTransactionValue: Boolean = false,
        private val message: String
    ) : Specification<TestEntity> {
        var wasCalled: Boolean = false

        override fun specify(entity: TestEntity): Specification.Result {
            wasCalled = true
            return if (shouldPass) Specification.Result.pass() else Specification.Result.fail(message)
        }

        override fun beforeTransaction(): Boolean = beforeTransactionValue
    }

    class AnotherEntitySpecification(
        private val shouldPass: Boolean,
        private val beforeTransactionValue: Boolean = false,
        private val message: String
    ) : Specification<AnotherEntity> {
        var wasCalled: Boolean = false

        override fun specify(entity: AnotherEntity): Specification.Result {
            wasCalled = true
            return if (shouldPass) Specification.Result.pass() else Specification.Result.fail(message)
        }

        override fun beforeTransaction(): Boolean = beforeTransactionValue
    }

    companion object {
        private var globalCallCounter = 0
    }

    @Order(0)
    class OrderedTestSpecification0 : Specification<TestEntity> {
        var wasCalled: Boolean = false
        var callOrder: Int = 0

        override fun specify(entity: TestEntity): Specification.Result {
            wasCalled = true
            callOrder = ++DefaultSpecificationManagerTest.globalCallCounter
            return Specification.Result.pass()
        }
    }

    @Order(1)
    class OrderedTestSpecification1 : Specification<TestEntity> {
        var wasCalled: Boolean = false
        var callOrder: Int = 0

        override fun specify(entity: TestEntity): Specification.Result {
            wasCalled = true
            callOrder = ++DefaultSpecificationManagerTest.globalCallCounter
            return Specification.Result.pass()
        }
    }

    @Order(2)
    class OrderedTestSpecification2 : Specification<TestEntity> {
        var wasCalled: Boolean = false
        var callOrder: Int = 0

        override fun specify(entity: TestEntity): Specification.Result {
            wasCalled = true
            callOrder = ++DefaultSpecificationManagerTest.globalCallCounter
            return Specification.Result.pass()
        }
    }

    class ThrowingSpecification : Specification<TestEntity> {
        override fun specify(entity: TestEntity): Specification.Result {
            throw RuntimeException("Specification error")
        }
    }
}
