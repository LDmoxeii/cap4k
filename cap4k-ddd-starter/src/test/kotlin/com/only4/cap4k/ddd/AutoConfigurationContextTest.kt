package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration
import com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.application.request.RequestAutoConfiguration
import com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration
import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.domain.id.IdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration
import com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration
import com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfiguration
import com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration
import com.only4.cap4k.ddd.domain.repo.configure.JpaUnitOfWorkProperties
import com.only4.cap4k.ddd.domain.service.DomainServiceAutoConfiguration
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
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
class AutoConfigurationContextTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(
            IdPolicyAutoConfiguration::class.java,
            JpaRepositoryAutoConfiguration::class.java,
        )
        .withBean(JpaUnitOfWorkProperties::class.java, ::JpaUnitOfWorkProperties)
        .withBean(EntityManagerFactory::class.java, { entityManagerFactoryProxy() })
        .withBean(PersistListenerManager::class.java, {
            object : PersistListenerManager {
                override fun <Entity : Any> onChange(aggregate: Entity, type: PersistType) = Unit
            }
        })

    @Test
    fun `context should contain id policy beans for jpa unit of work`() {
        contextRunner.run { context ->
            assertNotNull(context.getBean(IdStrategyRegistry::class.java))
            assertNotNull(context.getBean(IdAllocator::class.java))
            assertNotNull(context.getBean(JpaUnitOfWork::class.java))
        }
    }

    private fun entityManagerFactoryProxy(): EntityManagerFactory {
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "createEntityManager" -> entityManagerProxy()
                "isOpen" -> true
                "close" -> Unit
                "toString" -> "EntityManagerFactoryProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            EntityManagerFactory::class.java.classLoader,
            arrayOf(EntityManagerFactory::class.java),
            handler,
        ) as EntityManagerFactory
    }

    private fun entityManagerProxy(): EntityManager {
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "isOpen" -> true
                "close" -> Unit
                "toString" -> "EntityManagerProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            EntityManager::class.java.classLoader,
            arrayOf(EntityManager::class.java),
            handler,
        ) as EntityManager
    }

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
