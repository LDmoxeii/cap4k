package com.only4.cap4k.ddd.core.share.misc.testdata

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

/**
 * 普通测试类
 */
class TestClass {
    fun doSomething() = "test"
}

/**
 * 测试抽象类
 */
abstract class TestAbstractClass {
    abstract fun abstractMethod()
}

/**
 * 测试接口
 */
interface TestInterface {
    fun interfaceMethod()
}

/**
 * 标记有领域事件注解的测试类
 */
@DomainEvent
class TestDomainEventClass {
    val eventId = "domain-event-1"
}

/**
 * 标记有集成事件注解的测试类
 */
@IntegrationEvent
class TestIntegrationEventClass {
    val eventId = "integration-event-1"
}

/**
 * 测试具体类，继承自抽象类
 */
class TestConcreteClass : TestAbstractClass() {
    override fun abstractMethod() {
        println("具体实现")
    }
}

/**
 * 同时标记有两种事件注解的类（一般不会这样用，但用于测试）
 */
@DomainEvent
@IntegrationEvent
class TestMultipleAnnotationsClass {
    val eventId = "multi-annotation-event"
} 