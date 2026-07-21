package com.only4.cap4k.ddd.core.domain.repo.impl

import com.only4.cap4k.ddd.core.domain.repo.PersistListener
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import com.only4.cap4k.ddd.core.share.misc.findDomainEventClasses
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.annotation.OrderUtils

@DisplayName("DefaultPersistListenerManager 测试")
class DefaultPersistListenerManagerTest {

    private lateinit var manager: DefaultPersistListenerManager

    @AfterEach
    fun tearDown() {
        // 清理所有Mock
        unmockkStatic(::findDomainEventClasses)
        clearAllMocks()
    }

    @BeforeEach
    fun setUp() {
        // 清理调用记录
        TestPersistListenerBase.callOrder.clear()

        // Mock扫描方法
        mockkStatic(::findDomainEventClasses)
        every { findDomainEventClasses(any()) } returns emptySet()
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTests {

        @Test
        @DisplayName("应该按Order注解排序持久化监听器")
        fun `should sort persist listeners by Order annotation`() {
            // given
            val listener1 = TestPersistListenerWithOrder3()
            val listener2 = TestPersistListenerWithOrder1()
            val listener3 = TestPersistListenerWithOrder2()

            // Mock Order解析
            mockkStatic(OrderUtils::class)
            every { OrderUtils.getOrder(listener1.javaClass, Ordered.LOWEST_PRECEDENCE) } returns 3
            every { OrderUtils.getOrder(listener2.javaClass, Ordered.LOWEST_PRECEDENCE) } returns 1
            every { OrderUtils.getOrder(listener3.javaClass, Ordered.LOWEST_PRECEDENCE) } returns 2

            // 清理之前的调用记录
            TestPersistListenerBase.callOrder.clear()

            manager = DefaultPersistListenerManager(listOf(listener1, listener2, listener3))

            val entity = TestEntity()

            // when
            manager.onChange(entity, PersistType.CREATE)

            // then
            // 验证调用顺序（应该按order 1,2,3的顺序调用）
            val expectedOrder = listOf(1, 2, 3)
            val actualOrder = TestPersistListenerBase.callOrder.take(3)
            assertEquals(expectedOrder, actualOrder)
        }

        @Test
        @DisplayName("初始化不应该扫描领域事件类")
        fun `initialization should not scan domain event classes`() {
            // given
            val listener = TestPersistListenerWithOrder1()
            manager = DefaultPersistListenerManager(listOf(listener))

            // when
            manager.init()
            manager.init()
            manager.init()

            // then
            verify(exactly = 0) { findDomainEventClasses(any()) }
        }
    }

    @Nested
    @DisplayName("持久化监听器管理测试")
    inner class PersistListenerManagementTests {

        @Test
        @DisplayName("应该根据实体类型调用对应的监听器")
        fun `should call listeners based on entity type`() {
            // given - 使用TestEntity类型的监听器，避免类型转换问题
            val listener1 = TestPersistListenerWithOrder1()
            val listener2 = TestPersistListenerWithOrder2()

            manager = DefaultPersistListenerManager(listOf(listener1, listener2))

            // when
            manager.onChange(TestEntity(), PersistType.CREATE)

            // then - 验证两个监听器都被调用
            assertEquals(2, TestPersistListenerBase.callOrder.size)
            assertEquals(listOf(1, 2), TestPersistListenerBase.callOrder)
        }

        @Test
        @DisplayName("应该调用Any类型的通用监听器")
        fun `should call Object type generic listeners`() {
            // given - 混合具体实体监听器和Any监听器，Any会注册到JVM Object/Any类型
            val concreteListener = TestPersistListenerWithOrder1()
            val genericCalls = mutableListOf<Class<*>>()
            val genericListener = object : PersistListener<Any> {
                override fun onChange(aggregate: Any, type: PersistType) {
                    genericCalls.add(aggregate.javaClass)
                }
            }

            manager = DefaultPersistListenerManager(listOf(concreteListener, genericListener))

            // when - TestEntity hits both the concrete listener and the Any/Object fallback.
            manager.onChange(TestEntity(), PersistType.CREATE)
            manager.onChange(OtherEntity(), PersistType.UPDATE)

            // then
            assertEquals(listOf(1), TestPersistListenerBase.callOrder)
            assertEquals(
                listOf(TestEntity::class.java, OtherEntity::class.java),
                genericCalls
            )
        }

        @Test
        @DisplayName("监听器异常应该调用onException方法")
        fun `listener exceptions should call onException method`() {
            // given
            val faultyListener = object : PersistListener<Any> {
                var exceptionCalled = false

                override fun onChange(aggregate: Any, type: PersistType) {
                    throw RuntimeException("Test exception")
                }

                override fun onException(aggregate: Any, type: PersistType, e: Exception) {
                    exceptionCalled = true
                }
            }

            manager = DefaultPersistListenerManager(listOf(faultyListener))

            // when
            manager.onChange(TestEntity(), PersistType.CREATE)

            // then
            assertEquals(true, faultyListener.exceptionCalled)
        }
    }

    @Nested
    @DisplayName("Lazy初始化测试")
    inner class LazyInitializationTests {

        @Test
        @DisplayName("多次调用onChange应该只初始化一次")
        fun `multiple onChange calls should initialize only once`() {
            // given
            val listener = TestPersistListenerWithOrder1()
            manager = DefaultPersistListenerManager(listOf(listener))

            // when
            manager.onChange(TestEntity(), PersistType.CREATE)
            manager.onChange(TestEntity(), PersistType.UPDATE)
            manager.onChange(TestEntity(), PersistType.DELETE)

            // then
            verify(exactly = 0) { findDomainEventClasses(any()) }
        }
    }

    // 测试用的类
    class TestEntity

    class OtherEntity

    class TestDomainEvent

    // 测试持久化监听器基类，用于记录调用顺序
    abstract class TestPersistListenerBase {
        companion object {
            @JvmStatic
            val callOrder = mutableListOf<Int>()
        }

        abstract val orderValue: Int

        fun recordCall() {
            callOrder.add(orderValue)
        }
    }

    // 使用@Order注解的测试监听器
    @Order(1)
    class TestPersistListenerWithOrder1 : TestPersistListenerBase(), PersistListener<TestEntity> {
        override val orderValue = 1

        override fun onChange(aggregate: TestEntity, type: PersistType) {
            recordCall()
        }
    }

    @Order(2)
    class TestPersistListenerWithOrder2 : TestPersistListenerBase(), PersistListener<TestEntity> {
        override val orderValue = 2

        override fun onChange(aggregate: TestEntity, type: PersistType) {
            recordCall()
        }
    }

    @Order(3)
    class TestPersistListenerWithOrder3 : TestPersistListenerBase(), PersistListener<TestEntity> {
        override val orderValue = 3

        override fun onChange(aggregate: TestEntity, type: PersistType) {
            recordCall()
        }
    }
}
