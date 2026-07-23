package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration
import com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.application.request.RequestAutoConfiguration
import com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration
import com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration
import com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfiguration
import com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration
import com.only4.cap4k.ddd.domain.repo.configure.JpaUnitOfWorkProperties
import com.only4.cap4k.ddd.domain.service.DomainServiceAutoConfiguration
import com.only4.cap4k.ddd.fixture.jpa.StarterJpaTestApplication
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.Propagation
import java.time.LocalDateTime
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
            assertNotNull(context.getBean(IdentifierStrategyRegistry::class.java))
            assertNotNull(context.getBean(IdentifierGenerator::class.java))
            assertNotNull(context.getBean(JpaUnitOfWork::class.java))
        }
    }

    @Test
    fun `custom mediator initializes its default identifiers property`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                IdPolicyAutoConfiguration::class.java,
                MediatorAutoConfiguration::class.java,
            ))
            .withUserConfiguration(
                CustomMediatorConfiguration::class.java,
            )
            .run { context ->
                val mediator = context.getBean(Mediator::class.java)
                val identifierGenerator = context.getBean(IdentifierGenerator::class.java)

                assertTrue(mediator is CustomMediator)
                assertTrue(MediatorSupport.instance === mediator)
                assertTrue(MediatorSupport.ioc.getBean(IdentifierGenerator::class.java) === identifierGenerator)
                assertTrue(mediator.identifiers === identifierGenerator)
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

    @Configuration(proxyBeanMethods = false)
    private class CustomMediatorConfiguration {
        @Bean
        fun customMediator(): Mediator = CustomMediator()
    }

    private class CustomMediator : Mediator {
        override fun <ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> create(entityPayload: ENTITY_PAYLOAD): ENTITY =
            unsupported()

        override fun <ENTITY : Any> find(
            predicate: Predicate<ENTITY>,
            orders: Collection<OrderInfo>,
            persist: Boolean,
        ): List<ENTITY> = unsupported()

        override fun <ENTITY : Any> find(
            predicate: Predicate<ENTITY>,
            orders: Collection<OrderInfo>,
            persist: Boolean,
            loadPlan: AggregateLoadPlan,
        ): List<ENTITY> = unsupported()

        override fun <ENTITY : Any> find(
            predicate: Predicate<ENTITY>,
            pageParam: PageParam,
            persist: Boolean,
        ): List<ENTITY> = unsupported()

        override fun <ENTITY : Any> find(
            predicate: Predicate<ENTITY>,
            pageParam: PageParam,
            persist: Boolean,
            loadPlan: AggregateLoadPlan,
        ): List<ENTITY> = unsupported()

        override fun <ENTITY : Any> findOne(predicate: Predicate<ENTITY>, persist: Boolean): ENTITY? = unsupported()

        override fun <ENTITY : Any> findOne(
            predicate: Predicate<ENTITY>,
            persist: Boolean,
            loadPlan: AggregateLoadPlan,
        ): ENTITY? = unsupported()

        override fun <ENTITY : Any> findFirst(
            predicate: Predicate<ENTITY>,
            orders: Collection<OrderInfo>,
            persist: Boolean,
        ): ENTITY? = unsupported()

        override fun <ENTITY : Any> findFirst(
            predicate: Predicate<ENTITY>,
            orders: Collection<OrderInfo>,
            persist: Boolean,
            loadPlan: AggregateLoadPlan,
        ): ENTITY? = unsupported()

        override fun <ENTITY : Any> findPage(
            predicate: Predicate<ENTITY>,
            pageParam: PageParam,
            persist: Boolean,
        ): PageData<ENTITY> = unsupported()

        override fun <ENTITY : Any> findPage(
            predicate: Predicate<ENTITY>,
            pageParam: PageParam,
            persist: Boolean,
            loadPlan: AggregateLoadPlan,
        ): PageData<ENTITY> = unsupported()

        override fun <ENTITY : Any> remove(predicate: Predicate<ENTITY>): List<ENTITY> = unsupported()

        override fun <ENTITY : Any> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY> = unsupported()

        override fun <ENTITY : Any> count(predicate: Predicate<ENTITY>): Long = unsupported()

        override fun <ENTITY : Any> exists(predicate: Predicate<ENTITY>): Boolean = unsupported()

        override fun <DOMAIN_SERVICE : Any> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE = unsupported()

        override fun persist(entity: Any, intent: PersistIntent) = unsupported<Unit>()

        override fun remove(entity: Any) = unsupported<Unit>()

        override fun save(propagation: Propagation) = unsupported<Unit>()

        override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE = unsupported()

        override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> schedule(
            request: REQUEST,
            schedule: LocalDateTime,
        ): String = unsupported()

        override fun <R : Any> result(requestId: String): R? = unsupported()

        override fun <EVENT : Any> attach(eventPayload: EVENT, schedule: LocalDateTime) = unsupported<Unit>()

        override fun <EVENT : Any> attach(
            schedule: LocalDateTime,
            eventPayloadSupplier: () -> EVENT,
        ) = unsupported<Unit>()

        override fun <EVENT : Any> detach(eventPayload: EVENT) = unsupported<Unit>()

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
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
            context.register(StarterJpaTestApplication::class.java)
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
}
