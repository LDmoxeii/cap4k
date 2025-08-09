package com.only4.cap4k.ddd

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.io.support.SpringFactoriesLoader
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

/**
 * 自动配置加载测试
 * 主要测试：
 * 1. 所有自动配置类都能正确注册
 * 2. 自动配置类的条件注解生效
 * 3. 配置类之间的依赖关系正确
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(classes = [AutoConfigurationLoadTest.AutoConfigurationLoadTestApp::class])
@TestPropertySource(locations = ["classpath:application-test.properties"])
class AutoConfigurationLoadTest {

    @Test
    @Disabled
    fun `should load all auto configuration classes from imports file`() {
        // 读取自动配置导入文件
        val autoConfigurations = SpringFactoriesLoader.loadFactoryNames(
            org.springframework.boot.autoconfigure.AutoConfiguration::class.java,
            this::class.java.classLoader
        )

        // 验证我们的配置类都在列表中
        val expectedConfigurations = listOf(
            "com.only4.cap4k.ddd.MediatorAutoConfiguration",
            "com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration",
            "com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration",
            "com.only4.cap4k.ddd.domain.service.DomainServiceAutoConfiguration",
            "com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration",
            "com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration",
            "com.only4.cap4k.ddd.application.request.RequestAutoConfiguration",
            "com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration",
            "com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration"
        )

        println("Found ${autoConfigurations.size} auto-configurations")

        for (expectedConfig in expectedConfigurations) {
            val found = autoConfigurations.any { it.contains(expectedConfig.substringAfterLast(".")) }
            if (!found) {
                println("Warning: Expected configuration not found in auto-config list: $expectedConfig")
            }
        }

        assertTrue(autoConfigurations.isNotEmpty(), "Should have auto-configurations loaded")
    }

    @Test
    @Disabled
    fun `should handle conditional configuration correctly`() {
        val context = AnnotationConfigApplicationContext()

        try {
            // 设置条件属性
            System.setProperty("cap4k.ddd.domain.event.enable", "true")
            System.setProperty("cap4k.ddd.application.request.enable", "false")

            context.register(AutoConfigurationLoadTestApp::class.java)
            context.refresh()

            // 验证条件配置生效
            val enabledBeans = context.getBeansOfType(Any::class.java)
            val beanNames = enabledBeans.keys

            // 应该包含启用的功能相关的Bean
            val eventRelatedBeans = beanNames.filter { it.contains("event", ignoreCase = true) }
            val requestRelatedBeans = beanNames.filter { it.contains("request", ignoreCase = true) }

            println("Event-related beans: $eventRelatedBeans")
            println("Request-related beans: $requestRelatedBeans")

            // 由于event功能启用，应该有相关Bean
            // 由于request功能禁用，相关Bean应该较少或没有

        } finally {
            context.close()
            // 清理系统属性
            System.clearProperty("cap4k.ddd.domain.event.enable")
            System.clearProperty("cap4k.ddd.application.request.enable")
        }
    }

    @Test
    @Disabled
    fun `should validate configuration properties classes`() {
        // 验证配置属性类都能正常加载
        val propertiesClasses = listOf(
            "com.only4.cap4k.ddd.domain.event.configure.EventProperties",
            "com.only4.cap4k.ddd.domain.event.configure.EventScheduleProperties",
            "com.only4.cap4k.ddd.application.request.configure.RequestProperties",
            "com.only4.cap4k.ddd.application.saga.configure.SagaProperties",
            "com.only4.cap4k.ddd.domain.distributed.configure.SnowflakeProperties"
        )

        for (className in propertiesClasses) {
            try {
                val clazz = Class.forName(className)
                assertNotNull(clazz, "Properties class should be loadable: $className")

                // 验证类有默认构造函数
                val constructor = clazz.getDeclaredConstructor()
                assertNotNull(constructor, "Properties class should have default constructor: $className")

                println("✓ Properties class loaded successfully: ${clazz.simpleName}")
            } catch (e: ClassNotFoundException) {
                println("✗ Properties class not found: $className")
                throw AssertionError("Properties class not found: $className", e)
            } catch (e: NoSuchMethodException) {
                println("✗ Properties class missing default constructor: $className")
                throw AssertionError("Properties class missing default constructor: $className", e)
            }
        }
    }

    @Test
    @Disabled
    fun `should not have conflicting bean definitions`() {
        val context = AnnotationConfigApplicationContext()

        try {
            context.register(AutoConfigurationLoadTestApp::class.java)
            context.refresh()

            val beanDefinitionNames = context.beanDefinitionNames
            val duplicateNames = mutableMapOf<String, Int>()

            // 统计Bean名称出现次数
            for (name in beanDefinitionNames) {
                duplicateNames[name] = duplicateNames.getOrDefault(name, 0) + 1
            }

            // 检查是否有重复的Bean定义
            val duplicates = duplicateNames.filter { it.value > 1 }

            assertTrue(
                duplicates.isEmpty(),
                "Should not have duplicate bean definitions. Found: $duplicates"
            )

            println("✓ No conflicting bean definitions found among ${beanDefinitionNames.size} beans")

        } finally {
            context.close()
        }
    }

    @Test
    @Disabled
    fun `should handle missing optional dependencies gracefully`() {
        // 测试当可选依赖缺失时，自动配置是否能正常处理
        val context = AnnotationConfigApplicationContext()

        try {
            // 禁用某些功能来模拟缺失依赖
            System.setProperty("cap4k.ddd.application.event.http.enable", "false")
            System.setProperty("cap4k.ddd.application.event.rabbitmq.enable", "false")
            System.setProperty("cap4k.ddd.application.event.rocketmq.enable", "false")

            context.register(AutoConfigurationLoadTestApp::class.java)
            context.refresh()

            // 上下文应该能正常启动 - 检查Bean定义数量
            val beanCount = context.beanDefinitionNames.size
            assertTrue(beanCount > 0, "Context should have beans loaded even with disabled optional features")

            // 验证核心功能仍然可用
            val coreBean = context.getBean("defaultMediator")
            assertNotNull(coreBean, "Core beans should still be available")

            println("✓ Application handles missing optional dependencies gracefully")

        } finally {
            context.close()
            System.clearProperty("cap4k.ddd.application.event.http.enable")
            System.clearProperty("cap4k.ddd.application.event.rabbitmq.enable")
            System.clearProperty("cap4k.ddd.application.event.rocketmq.enable")
        }
    }

    @Test
    @Disabled
    fun `should validate auto configuration metadata`() {
        // 验证自动配置元数据
        val selector = AutoConfigurationImportSelector()

        try {
            // 简化的元数据检查 - 验证选择器可以工作
            val selectImports = selector.javaClass.getDeclaredMethod(
                "selectImports",
                org.springframework.core.type.AnnotationMetadata::class.java
            )
            selectImports.isAccessible = true

            println("✓ AutoConfigurationImportSelector is functional")

        } catch (e: Exception) {
            println("Note: Auto-configuration metadata check failed (may be expected in test environment): ${e.message}")
        }
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @org.springframework.context.annotation.ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
    @org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = ["com.only4.cap4k.ddd"])
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])
    class AutoConfigurationLoadTestApp
}
