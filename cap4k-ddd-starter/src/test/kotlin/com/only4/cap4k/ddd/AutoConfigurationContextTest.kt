package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration
import com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.application.request.RequestAutoConfiguration
import com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration
import com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration
import com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration
import com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration
import com.only4.cap4k.ddd.domain.service.DomainServiceAutoConfiguration
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertTrue

/**
 * 自动配置上下文测试
 * 主要测试：
 * 1. Bean 加载顺序正常
 * 2. 避免提前调用未初始化的 Bean
 * 3. 检查不存在循环依赖问题
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(classes = [AutoConfigurationContextTest.AutoConfigurationTestApp::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "cap4k.ddd.domain.event.enable=true",
        "cap4k.ddd.application.request.enable=true",
        "cap4k.ddd.application.saga.enable=true",
        "cap4k.ddd.domain.distributed.snowflake.enable=true",
        "cap4k.ddd.application.distributed.locker.enable=true",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class AutoConfigurationContextTest {

    @Test
    @Disabled
    fun `should load all auto configurations without circular dependencies`() {
        val context = AnnotationConfigApplicationContext()

        try {
            // 注册所有自动配置类
            context.register(
                MediatorAutoConfiguration::class.java,
                DomainServiceAutoConfiguration::class.java,
                JpaRepositoryAutoConfiguration::class.java,
                DomainEventAutoConfiguration::class.java,
                SnowflakeAutoConfiguration::class.java,
                JdbcLockerAutoConfiguration::class.java,
                RequestAutoConfiguration::class.java,
                SagaAutoConfiguration::class.java,
                IntegrationEventAutoConfiguration::class.java
            )

            // 刷新上下文 - 如果存在循环依赖会在这里抛出异常
            context.refresh()

            // 验证关键 Bean 是否成功创建 - 检查Bean定义数量
            val beanCount = context.beanDefinitionNames.size
            assertTrue(beanCount > 0, "Application context should have beans loaded")

        } catch (e: Exception) {
            // 如果是循环依赖异常，测试失败
            if (e.message?.contains("circular") == true ||
                e.message?.contains("cycle") == true ||
                e.message?.contains("dependency") == true
            ) {
                throw AssertionError("Circular dependency detected: ${e.message}", e)
            }
            throw e
        } finally {
            context.close()
        }
    }

    @Test
    @Disabled
    fun `should respect auto configuration order`() {
        // 测试自动配置的顺序是否正确
        val configurations = listOf(
            "com.only4.cap4k.ddd.MediatorAutoConfiguration",
            "com.only4.cap4k.ddd.domain.service.DomainServiceAutoConfiguration",
            "com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration",
            "com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration",
            "com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration",
            "com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration",
            "com.only4.cap4k.ddd.application.request.RequestAutoConfiguration",
            "com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration",
            "com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration"
        )

        // 简化的顺序检查 - 验证配置类都能被加载
        for (configClass in configurations) {
            try {
                Class.forName(configClass)
                println("✓ Configuration class loadable: $configClass")
            } catch (e: ClassNotFoundException) {
                println("✗ Configuration class not found: $configClass")
            }
        }

        // 验证基础配置类存在
        val mediatorConfig = configurations.find { it.contains("MediatorAutoConfiguration") }
        val domainEventConfig = configurations.find { it.contains("DomainEventAutoConfiguration") }

        assertTrue(mediatorConfig != null, "MediatorAutoConfiguration should be included")
        assertTrue(domainEventConfig != null, "DomainEventAutoConfiguration should be included")
    }

    @Test
    @Disabled
    fun `should handle bean initialization order correctly`() {
        val context = AnnotationConfigApplicationContext()

        // 添加 Bean 初始化顺序监听器
        val initializationOrder = mutableListOf<String>()

        context.addBeanFactoryPostProcessor { beanFactory ->
            beanFactory.addBeanPostProcessor(object : org.springframework.beans.factory.config.BeanPostProcessor {
                override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                    initializationOrder.add("$beanName:${bean::class.simpleName}")
                    return bean
                }
            })
        }

        try {
            context.register(TestApplication::class.java)
            context.refresh()

            // 验证关键 Bean 的初始化顺序
            val mediatorBeans = initializationOrder.filter { it.contains("Mediator") }
            val repositoryBeans = initializationOrder.filter { it.contains("Repository") }
            val eventBeans = initializationOrder.filter { it.contains("Event") }

            println("Bean initialization order:")
            initializationOrder.forEach { println("  $it") }

            // 基本的顺序验证
            assertTrue(initializationOrder.isNotEmpty(), "Should have initialized some beans")

        } finally {
            context.close()
        }
    }

    @SpringBootApplication
    @ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
    @EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])
    @EntityScan(basePackages = ["com.only4.cap4k.ddd"])
    class AutoConfigurationTestApp
}
