package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DefaultAggregateFactorySupervisor 测试")
class DefaultAggregateFactorySupervisorTest {

    private lateinit var supervisor: DefaultAggregateFactorySupervisor
    private lateinit var unitOfWork: UnitOfWork

    @BeforeEach
    fun setup() {
        unitOfWork = mockk(relaxed = true)
    }

    @Test
    @DisplayName("应该使用匹配的工厂创建聚合并调用UnitOfWork持久化")
    fun `should create aggregate with matching factory and persist`() {
        // Given
        val factory = TestAggregateFactory()
        supervisor = DefaultAggregateFactorySupervisor(listOf(factory), unitOfWork)
        val payload = TestPayload("test-data")

        // When
        val result = supervisor.create(payload)

        // Then
        assertNotNull(result)
        assertEquals("test-data", result!!.data)
        verify { unitOfWork.persist(result) }
    }

    @Test
    @DisplayName("当没有匹配的工厂时应该抛出异常")
    fun `should throw exception when no matching factory found`() {
        // Given
        supervisor = DefaultAggregateFactorySupervisor(emptyList(), unitOfWork)
        val payload = TestPayload("test-data")

        // When & Then
        val exception = assertThrows(com.only4.cap4k.ddd.core.share.DomainException::class.java) {
            supervisor.create(payload)
        }

        assertTrue(exception.message!!.contains("No factory found for payload"))
        verify(exactly = 0) { unitOfWork.persist(any()) }
    }

    @Test
    @DisplayName("应该正确匹配不同类型的payload到对应工厂")
    fun `should correctly match different payload types to factories`() {
        // Given
        val testFactory = TestAggregateFactory()
        val anotherFactory = AnotherAggregateFactory()
        supervisor = DefaultAggregateFactorySupervisor(listOf(testFactory, anotherFactory), unitOfWork)

        val testPayload = TestPayload("test-data")
        val anotherPayload = AnotherPayload("another-data")

        // When
        val result1 = supervisor.create(testPayload)
        val result2 = supervisor.create(anotherPayload)

        // Then
        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals("test-data", result1!!.data)
        assertEquals("another-data", result2!!.data)
        verify { unitOfWork.persist(result1) }
        verify { unitOfWork.persist(result2) }
    }

    @Test
    @DisplayName("当UnitOfWork持久化失败时应该抛出异常")
    fun `should throw exception when UnitOfWork persist fails`() {
        // Given
        val factory = TestAggregateFactory()
        supervisor = DefaultAggregateFactorySupervisor(listOf(factory), unitOfWork)
        val payload = TestPayload("test-data")

        every { unitOfWork.persist(any()) } throws RuntimeException("Database error")

        // When & Then
        val exception = assertThrows(RuntimeException::class.java) {
            supervisor.create(payload)
        }

        assertEquals("Database error", exception.message)
    }

    @Test
    @DisplayName("当工厂列表为空时应该抛出异常")
    fun `should throw exception when factory list is empty`() {
        // Given
        supervisor = DefaultAggregateFactorySupervisor(emptyList(), unitOfWork)
        val payload = TestPayload("test-data")

        // When & Then
        val exception = assertThrows(com.only4.cap4k.ddd.core.share.DomainException::class.java) {
            supervisor.create(payload)
        }

        assertTrue(exception.message!!.contains("No factory found for payload"))
        verify(exactly = 0) { unitOfWork.persist(any()) }
    }

    @Test
    @DisplayName("应该线程安全地处理并发创建请求")
    fun `should handle concurrent creation requests thread-safely`() {
        // Given
        val factory = TestAggregateFactory()
        supervisor = DefaultAggregateFactorySupervisor(listOf(factory), unitOfWork)
        val payload = TestPayload("concurrent-data")

        // When
        val threads = List(10) {
            Thread {
                supervisor.create(payload)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        verify(exactly = 10) { unitOfWork.persist(any()) }
    }

    // Test data classes and factories
    data class TestPayload(val data: String) : AggregatePayload<TestEntity>
    data class AnotherPayload(val data: String) : AggregatePayload<AnotherEntity>

    data class TestEntity(val data: String)
    data class AnotherEntity(val data: String)

    class TestAggregateFactory : AggregateFactory<TestPayload, TestEntity> {
        override fun create(entityPayload: TestPayload): TestEntity {
            return TestEntity(entityPayload.data)
        }
    }

    class AnotherAggregateFactory : AggregateFactory<AnotherPayload, AnotherEntity> {
        override fun create(entityPayload: AnotherPayload): AnotherEntity {
            return AnotherEntity(entityPayload.data)
        }
    }
}
