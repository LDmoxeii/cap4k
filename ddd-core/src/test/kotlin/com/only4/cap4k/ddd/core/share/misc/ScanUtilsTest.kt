package com.only4.cap4k.ddd.core.share.misc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScanUtilsTest {

    // 测试扫描类方法
    @Test
    fun `scanClass should find all classes in the specified package`() {
        // 使用测试包路径
        val testPackage = "com.only4.cap4k.ddd.core.share.misc.testdata"

        // 扫描所有类（包括接口和抽象类）
        val allClasses = scanClass(testPackage, false)

        // 验证是否找到了所有类
        assertTrue(allClasses.isNotEmpty(), "应该找到测试包中的类")
        assertTrue(allClasses.any { it.simpleName == "TestClass" }, "应该找到TestClass")
        assertTrue(allClasses.any { it.simpleName == "TestAbstractClass" }, "应该找到TestAbstractClass")
        assertTrue(allClasses.any { it.simpleName == "TestInterface" }, "应该找到TestInterface")
        assertTrue(
            allClasses.any { it.simpleName == "TestMultipleAnnotationsClass" },
            "应该找到TestMultipleAnnotationsClass"
        )
    }

    @Test
    fun `scanClass with concrete parameter true should only find concrete classes`() {
        // 使用测试包路径
        val testPackage = "com.only4.cap4k.ddd.core.share.misc.testdata"

        // 只扫描具体类（非接口和非抽象类）
        val concreteClasses = scanClass(testPackage, true)

        // 验证是否只找到了具体类
        assertTrue(concreteClasses.isNotEmpty(), "应该找到测试包中的具体类")
        assertTrue(concreteClasses.any { it.simpleName == "TestClass" }, "应该找到TestClass")
        assertTrue(concreteClasses.any { it.simpleName == "TestDomainEventClass" }, "应该找到TestDomainEventClass")
        assertTrue(
            concreteClasses.any { it.simpleName == "TestIntegrationEventClass" },
            "应该找到TestIntegrationEventClass"
        )
        assertTrue(concreteClasses.any { it.simpleName == "TestConcreteClass" }, "应该找到TestConcreteClass")
        assertTrue(
            concreteClasses.any { it.simpleName == "TestMultipleAnnotationsClass" },
            "应该找到TestMultipleAnnotationsClass"
        )

        // 验证是否排除了接口和抽象类
        assertFalse(concreteClasses.any { it.simpleName == "TestAbstractClass" }, "不应该找到抽象类TestAbstractClass")
        assertFalse(concreteClasses.any { it.simpleName == "TestInterface" }, "不应该找到接口TestInterface")
    }

    @Test
    fun `scanClass should handle non-existent package gracefully`() {
        // 使用不存在的包路径
        val nonExistentPackage = "com.only4.cap4k.ddd.core.share.misc.nonexistent"

        // 扫描不存在的包
        val classes = scanClass(nonExistentPackage, false)

        // 验证结果为空集合而不是抛出异常
        assertTrue(classes.isEmpty(), "不存在的包应该返回空集合")
    }

    // 测试查找领域事件类方法
    @Test
    fun `findDomainEventClasses should find classes with DomainEvent annotation`() {
        // 使用测试包路径
        val testPackage = "com.only4.cap4k.ddd.core.share.misc.testdata"

        // 查找领域事件类
        val domainEventClasses = findDomainEventClasses(testPackage)

        // 验证是否找到了带有@DomainEvent注解的类
        assertTrue(domainEventClasses.isNotEmpty(), "应该找到带有@DomainEvent注解的类")
        assertTrue(domainEventClasses.any { it.simpleName == "TestDomainEventClass" }, "应该找到TestDomainEventClass")
        assertTrue(
            domainEventClasses.any { it.simpleName == "TestMultipleAnnotationsClass" },
            "应该找到TestMultipleAnnotationsClass"
        )

        // 验证是否排除了没有@DomainEvent注解的类
        assertFalse(
            domainEventClasses.any { it.simpleName == "TestClass" },
            "不应该找到没有@DomainEvent注解的TestClass"
        )
        assertFalse(
            domainEventClasses.any { it.simpleName == "TestIntegrationEventClass" },
            "不应该找到只有@IntegrationEvent注解的TestIntegrationEventClass"
        )
    }

    // 测试查找集成事件类方法
    @Test
    fun `findIntegrationEventClasses should find classes with IntegrationEvent annotation`() {
        // 使用测试包路径
        val testPackage = "com.only4.cap4k.ddd.core.share.misc.testdata"

        // 查找集成事件类
        val integrationEventClasses = findIntegrationEventClasses(testPackage)

        // 验证是否找到了带有@IntegrationEvent注解的类
        assertTrue(integrationEventClasses.isNotEmpty(), "应该找到带有@IntegrationEvent注解的类")
        assertTrue(
            integrationEventClasses.any { it.simpleName == "TestIntegrationEventClass" },
            "应该找到TestIntegrationEventClass"
        )
        assertTrue(
            integrationEventClasses.any { it.simpleName == "TestMultipleAnnotationsClass" },
            "应该找到TestMultipleAnnotationsClass"
        )

        // 验证是否排除了没有@IntegrationEvent注解的类
        assertFalse(
            integrationEventClasses.any { it.simpleName == "TestClass" },
            "不应该找到没有@IntegrationEvent注解的TestClass"
        )
        assertFalse(
            integrationEventClasses.any { it.simpleName == "TestDomainEventClass" },
            "不应该找到只有@DomainEvent注解的TestDomainEventClass"
        )
    }

    @Test
    fun `scanClass should handle empty package path`() {
        // 测试空包路径
        val emptyPackage = ""

        // 当传入空包路径时
        val exception = assertThrows(NoClassDefFoundError::class.java) {
            scanClass(emptyPackage, false)
        }

        // 期望抛出异常
        assertNotNull(exception, "空包路径应该导致异常")
    }
}
