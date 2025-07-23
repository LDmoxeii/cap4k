package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.Order
import org.springframework.messaging.Message
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * DefaultEventInterceptorManager 详尽测试用例
 *
 * @author LD_moxeii
 * @date 2025/07/22
 */
@DisplayName("DefaultEventInterceptorManager Tests")
class DefaultEventInterceptorManagerTest {

    private lateinit var manager: DefaultEventInterceptorManager

    // Mock拦截器接口实现
    @Order(1)
    class TestEventMessageInterceptor1 : EventMessageInterceptor {
        override fun initPublish(message: Message<*>) {}
        override fun prePublish(message: Message<*>) {}
        override fun postPublish(message: Message<*>) {}
        override fun preSubscribe(message: Message<*>) {}
        override fun postSubscribe(message: Message<*>) {}
    }

    @Order(2)
    class TestEventMessageInterceptor2 : EventMessageInterceptor {
        override fun initPublish(message: Message<*>) {}
        override fun prePublish(message: Message<*>) {}
        override fun postPublish(message: Message<*>) {}
        override fun preSubscribe(message: Message<*>) {}
        override fun postSubscribe(message: Message<*>) {}
    }

    @Order(3)
    class TestEventMessageInterceptor3 : EventMessageInterceptor {
        override fun initPublish(message: Message<*>) {}
        override fun prePublish(message: Message<*>) {}
        override fun postPublish(message: Message<*>) {}
        override fun preSubscribe(message: Message<*>) {}
        override fun postSubscribe(message: Message<*>) {}
    }

    @Order(10)
    class TestDomainEventInterceptor1 : DomainEventInterceptor {
        override fun onAttach(eventPayload: Any, entity: Any, schedule: LocalDateTime) {}
        override fun onDetach(eventPayload: Any, entity: Any) {}
        override fun prePersist(event: EventRecord) {}
        override fun postPersist(event: EventRecord) {}
        override fun preRelease(event: EventRecord) {}
        override fun postRelease(event: EventRecord) {}
        override fun onException(throwable: Throwable, event: EventRecord) {}
    }

    @Order(20)
    class TestDomainEventInterceptor2 : DomainEventInterceptor {
        override fun onAttach(eventPayload: Any, entity: Any, schedule: LocalDateTime) {}
        override fun onDetach(eventPayload: Any, entity: Any) {}
        override fun prePersist(event: EventRecord) {}
        override fun postPersist(event: EventRecord) {}
        override fun preRelease(event: EventRecord) {}
        override fun postRelease(event: EventRecord) {}
        override fun onException(throwable: Throwable, event: EventRecord) {}
    }

    @Order(30)
    class TestIntegrationEventInterceptor1 : IntegrationEventInterceptor {
        override fun onAttach(eventPayload: Any, schedule: LocalDateTime) {}
        override fun onDetach(eventPayload: Any) {}
        override fun prePersist(event: EventRecord) {}
        override fun postPersist(event: EventRecord) {}
        override fun preRelease(event: EventRecord) {}
        override fun postRelease(event: EventRecord) {}
        override fun onException(throwable: Throwable, event: EventRecord) {}
    }

    @Order(40)
    class TestIntegrationEventInterceptor2 : IntegrationEventInterceptor {
        override fun onAttach(eventPayload: Any, schedule: LocalDateTime) {}
        override fun onDetach(eventPayload: Any) {}
        override fun prePersist(event: EventRecord) {}
        override fun postPersist(event: EventRecord) {}
        override fun preRelease(event: EventRecord) {}
        override fun postRelease(event: EventRecord) {}
        override fun onException(throwable: Throwable, event: EventRecord) {}
    }

    // 没有Order注解的拦截器
    class TestDomainEventInterceptorNoOrder : DomainEventInterceptor {
        override fun onAttach(eventPayload: Any, entity: Any, schedule: LocalDateTime) {}
        override fun onDetach(eventPayload: Any, entity: Any) {}
        override fun prePersist(event: EventRecord) {}
        override fun postPersist(event: EventRecord) {}
        override fun preRelease(event: EventRecord) {}
        override fun postRelease(event: EventRecord) {}
        override fun onException(throwable: Throwable, event: EventRecord) {}
    }

    // 通用EventInterceptor（既不是Domain也不是Integration）
    @Order(50)
    class TestGenericEventInterceptor : EventInterceptor {
        override fun prePersist(event: EventRecord) {}
        override fun postPersist(event: EventRecord) {}
        override fun preRelease(event: EventRecord) {}
        override fun postRelease(event: EventRecord) {}
        override fun onException(throwable: Throwable, event: EventRecord) {}
    }

    @Nested
    @DisplayName("Core Functionality Tests")
    inner class CoreFunctionalityTests {

        @BeforeEach
        fun setUp() {
            val eventMessageInterceptors = listOf(
                TestEventMessageInterceptor3(), // Order 3
                TestEventMessageInterceptor1(), // Order 1
                TestEventMessageInterceptor2()  // Order 2
            )

            val eventInterceptors = listOf(
                TestDomainEventInterceptor2(),        // Order 20
                TestIntegrationEventInterceptor1(),   // Order 30
                TestDomainEventInterceptor1(),        // Order 10
                TestIntegrationEventInterceptor2(),   // Order 40
                TestGenericEventInterceptor(),        // Order 50
                TestDomainEventInterceptorNoOrder()   // No order (LOWEST_PRECEDENCE)
            )

            manager = DefaultEventInterceptorManager(eventMessageInterceptors, eventInterceptors)
        }

        @Test
        @DisplayName("应该按Order注解正确排序EventMessageInterceptor")
        fun `should order EventMessageInterceptors by Order annotation`() {
            val orderedInterceptors = manager.orderedEventMessageInterceptors

            assertEquals(3, orderedInterceptors.size)

            val interceptorList = orderedInterceptors.toList()
            assertTrue(interceptorList[0] is TestEventMessageInterceptor1) // Order 1
            assertTrue(interceptorList[1] is TestEventMessageInterceptor2) // Order 2
            assertTrue(interceptorList[2] is TestEventMessageInterceptor3) // Order 3
        }

        @Test
        @DisplayName("应该只返回DomainEventInterceptor实例并按Order排序")
        fun `should return only DomainEventInterceptor instances ordered by Order annotation`() {
            val orderedInterceptors = manager.orderedDomainEventInterceptors

            assertEquals(3, orderedInterceptors.size)
            orderedInterceptors.forEach { assertTrue(it is DomainEventInterceptor) }

            val interceptorList = orderedInterceptors.toList()
            assertTrue(interceptorList[0] is TestDomainEventInterceptor1)     // Order 10
            assertTrue(interceptorList[1] is TestDomainEventInterceptor2)     // Order 20
            assertTrue(interceptorList[2] is TestDomainEventInterceptorNoOrder) // LOWEST_PRECEDENCE
        }

        @Test
        @DisplayName("应该只返回IntegrationEventInterceptor实例并按Order排序")
        fun `should return only IntegrationEventInterceptor instances ordered by Order annotation`() {
            val orderedInterceptors = manager.orderedIntegrationEventInterceptors

            assertEquals(2, orderedInterceptors.size)
            orderedInterceptors.forEach { assertTrue(it is IntegrationEventInterceptor) }

            val interceptorList = orderedInterceptors.toList()
            assertTrue(interceptorList[0] is TestIntegrationEventInterceptor1) // Order 30
            assertTrue(interceptorList[1] is TestIntegrationEventInterceptor2) // Order 40
        }

        @Test
        @DisplayName("应该为DomainEvent返回合适的EventInterceptor集合")
        fun `should return appropriate EventInterceptors for DomainEvent`() {
            val orderedInterceptors = manager.orderedEventInterceptors4DomainEvent

            // 应包含：DomainEventInterceptor + 通用EventInterceptor，排除IntegrationEventInterceptor
            assertEquals(4, orderedInterceptors.size)

            val interceptorList = orderedInterceptors.toList()
            assertTrue(interceptorList[0] is TestDomainEventInterceptor1)     // Order 10
            assertTrue(interceptorList[1] is TestDomainEventInterceptor2)     // Order 20
            assertTrue(interceptorList[2] is TestGenericEventInterceptor)     // Order 50
            assertTrue(interceptorList[3] is TestDomainEventInterceptorNoOrder) // LOWEST_PRECEDENCE
        }

        @Test
        @DisplayName("应该为IntegrationEvent返回合适的EventInterceptor集合")
        fun `should return appropriate EventInterceptors for IntegrationEvent`() {
            val orderedInterceptors = manager.orderedEventInterceptors4IntegrationEvent

            // 应包含：IntegrationEventInterceptor + 通用EventInterceptor，排除纯DomainEventInterceptor
            assertEquals(3, orderedInterceptors.size)

            val interceptorList = orderedInterceptors.toList()
            assertTrue(interceptorList[0] is TestIntegrationEventInterceptor1) // Order 30
            assertTrue(interceptorList[1] is TestIntegrationEventInterceptor2) // Order 40
            assertTrue(interceptorList[2] is TestGenericEventInterceptor)      // Order 50
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("应该正确处理空的拦截器列表")
        fun `should handle empty interceptor lists`() {
            val manager = DefaultEventInterceptorManager(emptyList(), emptyList())

            assertTrue(manager.orderedEventMessageInterceptors.isEmpty())
            assertTrue(manager.orderedDomainEventInterceptors.isEmpty())
            assertTrue(manager.orderedIntegrationEventInterceptors.isEmpty())
            assertTrue(manager.orderedEventInterceptors4DomainEvent.isEmpty())
            assertTrue(manager.orderedEventInterceptors4IntegrationEvent.isEmpty())
        }

        @Test
        @DisplayName("应该正确处理只有EventMessageInterceptor的情况")
        fun `should handle only EventMessageInterceptors`() {
            val eventMessageInterceptors = listOf(TestEventMessageInterceptor1())
            val manager = DefaultEventInterceptorManager(eventMessageInterceptors, emptyList())

            assertEquals(1, manager.orderedEventMessageInterceptors.size)
            assertTrue(manager.orderedDomainEventInterceptors.isEmpty())
            assertTrue(manager.orderedIntegrationEventInterceptors.isEmpty())
            assertTrue(manager.orderedEventInterceptors4DomainEvent.isEmpty())
            assertTrue(manager.orderedEventInterceptors4IntegrationEvent.isEmpty())
        }

        @Test
        @DisplayName("应该正确处理只有单一类型EventInterceptor的情况")
        fun `should handle single type of EventInterceptors`() {
            val eventInterceptors = listOf(TestDomainEventInterceptor1(), TestDomainEventInterceptor2())
            val manager = DefaultEventInterceptorManager(emptyList(), eventInterceptors)

            assertTrue(manager.orderedEventMessageInterceptors.isEmpty())
            assertEquals(2, manager.orderedDomainEventInterceptors.size)
            assertTrue(manager.orderedIntegrationEventInterceptors.isEmpty())
            assertEquals(2, manager.orderedEventInterceptors4DomainEvent.size)
            assertTrue(manager.orderedEventInterceptors4IntegrationEvent.isEmpty())
        }

        @Test
        @DisplayName("应该正确处理没有Order注解的拦截器")
        fun `should handle interceptors without Order annotation`() {
            val eventMessageInterceptors = listOf(TestEventMessageInterceptor1()) // No @Order
            val manager = DefaultEventInterceptorManager(eventMessageInterceptors, emptyList())

            assertEquals(1, manager.orderedEventMessageInterceptors.size)
        }

        @Test
        @DisplayName("应该正确处理相同Order值的拦截器")
        fun `should handle interceptors with same Order value`() {
            @Order(100)
            class SameOrderInterceptor1 : EventMessageInterceptor {
                override fun initPublish(message: Message<*>) {}
                override fun prePublish(message: Message<*>) {}
                override fun postPublish(message: Message<*>) {}
                override fun preSubscribe(message: Message<*>) {}
                override fun postSubscribe(message: Message<*>) {}
            }

            @Order(100)
            class SameOrderInterceptor2 : EventMessageInterceptor {
                override fun initPublish(message: Message<*>) {}
                override fun prePublish(message: Message<*>) {}
                override fun postPublish(message: Message<*>) {}
                override fun preSubscribe(message: Message<*>) {}
                override fun postSubscribe(message: Message<*>) {}
            }

            val eventMessageInterceptors = listOf(SameOrderInterceptor1(), SameOrderInterceptor2())
            val manager = DefaultEventInterceptorManager(eventMessageInterceptors, emptyList())

            assertEquals(2, manager.orderedEventMessageInterceptors.size)
        }
    }

    @Nested
    @DisplayName("Performance and Thread Safety Tests")
    inner class PerformanceAndThreadSafetyTests {

        @BeforeEach
        fun setUp() {
            val eventMessageInterceptors = List(100) { TestEventMessageInterceptor1() }
            val eventInterceptors = List(100) { TestDomainEventInterceptor1() }
            manager = DefaultEventInterceptorManager(eventMessageInterceptors, eventInterceptors)
        }

        @Test
        @DisplayName("延迟初始化应该只计算一次")
        fun `lazy initialization should compute only once`() {
            // 多次访问同一个属性
            val first = manager.orderedEventMessageInterceptors
            val second = manager.orderedEventMessageInterceptors
            val third = manager.orderedEventMessageInterceptors

            // 应该是同一个实例
            assertSame(first, second)
            assertSame(second, third)
        }

        @Test
        @DisplayName("并发访问应该是线程安全的")
        fun `concurrent access should be thread safe`() {
            val threadCount = 50
            val latch = CountDownLatch(threadCount)
            val results = mutableListOf<Set<EventMessageInterceptor>>()
            val executor = Executors.newFixedThreadPool(threadCount)

            repeat(threadCount) {
                executor.submit {
                    try {
                        val result = manager.orderedEventMessageInterceptors
                        synchronized(results) {
                            results.add(result)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))

            // 所有结果都应该是同一个实例
            val firstResult = results.first()
            results.forEach { assertSame(firstResult, it) }

            executor.shutdown()
        }

        @Test
        @DisplayName("大量拦截器的排序性能测试")
        fun `should handle large number of interceptors efficiently`() {
            val largeEventMessageInterceptors = List(1000) { TestEventMessageInterceptor1() }
            val largeEventInterceptors = List(1000) { TestDomainEventInterceptor1() }

            val startTime = System.currentTimeMillis()
            val largeManager = DefaultEventInterceptorManager(largeEventMessageInterceptors, largeEventInterceptors)

            // 访问所有属性
            largeManager.orderedEventMessageInterceptors
            largeManager.orderedDomainEventInterceptors
            largeManager.orderedIntegrationEventInterceptors
            largeManager.orderedEventInterceptors4DomainEvent
            largeManager.orderedEventInterceptors4IntegrationEvent

            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime

            // 应该在合理时间内完成（小于1秒）
            assertTrue(executionTime < 1000, "Execution time was ${executionTime}ms")
        }
    }

    @Nested
    @DisplayName("Data Consistency Tests")
    inner class DataConsistencyTests {

        @BeforeEach
        fun setUp() {
            val eventMessageInterceptors = listOf(
                TestEventMessageInterceptor1(),
                TestEventMessageInterceptor2()
            )

            val eventInterceptors = listOf(
                TestDomainEventInterceptor1(),
                TestIntegrationEventInterceptor1(),
                TestGenericEventInterceptor()
            )

            manager = DefaultEventInterceptorManager(eventMessageInterceptors, eventInterceptors)
        }

        @Test
        @DisplayName("返回的Set应该保持LinkedHashSet的顺序")
        fun `returned Sets should maintain LinkedHashSet ordering`() {
            val orderedInterceptors = manager.orderedEventMessageInterceptors

            assertTrue(orderedInterceptors is LinkedHashSet)

            // 检查迭代顺序是否一致
            val firstIteration = orderedInterceptors.toList()
            val secondIteration = orderedInterceptors.toList()

            assertEquals(firstIteration, secondIteration)
        }

        @Test
        @DisplayName("不同类型的拦截器集合不应该有交叉污染")
        fun `different interceptor sets should not cross-contaminate`() {
            val domainInterceptors = manager.orderedDomainEventInterceptors
            val integrationInterceptors = manager.orderedIntegrationEventInterceptors
            val messageInterceptors = manager.orderedEventMessageInterceptors

            // Domain和Integration拦截器不应该有交集
            val domainIntegrationIntersection = domainInterceptors.intersect(integrationInterceptors.toSet())
            assertTrue(domainIntegrationIntersection.isEmpty())

            // EventMessage拦截器应该与其他类型完全分离
            assertTrue(messageInterceptors.isNotEmpty())
            domainInterceptors.forEach { assertFalse(it is EventMessageInterceptor) }
            integrationInterceptors.forEach { assertFalse(it is EventMessageInterceptor) }
        }

        @Test
        @DisplayName("拦截器实例不应该被意外修改")
        fun `interceptor instances should not be accidentally modified`() {
            val originalEventInterceptors = listOf(TestDomainEventInterceptor1())
            val testManager = DefaultEventInterceptorManager(emptyList(), originalEventInterceptors)

            val retrievedInterceptors = testManager.orderedDomainEventInterceptors

            // 原始列表和返回的集合应该包含相同的实例
            assertTrue(retrievedInterceptors.containsAll(originalEventInterceptors))
            assertEquals(originalEventInterceptors.size, retrievedInterceptors.size)
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    inner class InterfaceImplementationTests {

        @Test
        @DisplayName("应该正确实现EventMessageInterceptorManager接口")
        fun `should correctly implement EventMessageInterceptorManager interface`() {
            val manager = DefaultEventInterceptorManager(emptyList(), emptyList())

            assertTrue(manager is EventMessageInterceptorManager)
            assertNotNull(manager.orderedEventMessageInterceptors)
        }

        @Test
        @DisplayName("应该正确实现DomainEventInterceptorManager接口")
        fun `should correctly implement DomainEventInterceptorManager interface`() {
            val manager = DefaultEventInterceptorManager(emptyList(), emptyList())

            assertTrue(manager is DomainEventInterceptorManager)
            assertNotNull(manager.orderedDomainEventInterceptors)
            assertNotNull(manager.orderedEventInterceptors4DomainEvent)
        }

        @Test
        @DisplayName("应该正确实现IntegrationEventInterceptorManager接口")
        fun `should correctly implement IntegrationEventInterceptorManager interface`() {
            val manager = DefaultEventInterceptorManager(emptyList(), emptyList())

            assertTrue(manager is IntegrationEventInterceptorManager)
            assertNotNull(manager.orderedIntegrationEventInterceptors)
            assertNotNull(manager.orderedEventInterceptors4IntegrationEvent)
        }
    }
}
