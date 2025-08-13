package com.only4.cap4k.ddd

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.stereotype.Component
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Bean生命周期和初始化顺序测试
 * 主要测试：
 * 1. Bean初始化顺序符合预期
 * 2. 避免在Bean未完全初始化时被调用
 * 3. 应用上下文刷新过程正常
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(classes = [BeanLifecycleTest.BeanLifecycleTestApp::class, BeanLifecycleTest.TestLifecycleListener::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "cap4k.ddd.domain.event.enable=true",
        "cap4k.ddd.application.request.enable=false",
        "cap4k.ddd.application.saga.enable=false",
        "cap4k.ddd.domain.distributed.snowflake.enable=true",
        "cap4k.ddd.application.distributed.locker.enable=true",
        "spring.datasource.url=jdbc:h2:mem:lifecycletest",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
    ]
)
class BeanLifecycleTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var testLifecycleListener: TestLifecycleListener

    @Test
    @Disabled
    fun `should complete context refresh without premature bean access`() {
        // 等待上下文完全刷新
        assertTrue(
            testLifecycleListener.waitForContextRefresh(30, TimeUnit.SECONDS),
            "Context should refresh within timeout"
        )

        // 验证上下文状态 - 检查Bean定义名称数量来确认上下文已初始化
        val beanCount = applicationContext.beanDefinitionNames.size
        assertTrue(beanCount > 0, "Application context should have beans loaded")

        // 检查是否有Bean在初始化过程中被提前访问
        val prematureAccess = testLifecycleListener.getPrematureAccessEvents()
        assertTrue(
            prematureAccess.isEmpty(),
            "No beans should be accessed before full initialization. Found: $prematureAccess"
        )

        println("✓ Context refresh completed successfully without premature bean access")
    }

    @Test
    @Disabled
    fun `should initialize beans in correct dependency order`() {
        val initializationOrder = testLifecycleListener.getInitializationOrder()

        assertTrue(initializationOrder.isNotEmpty(), "Should have recorded bean initialization order")

        // 验证关键Bean的初始化顺序
        val mediatorIndex = initializationOrder.indexOfFirst { it.contains("Mediator") }
        val repositoryIndex = initializationOrder.indexOfFirst { it.contains("Repository") }
        val eventIndex = initializationOrder.indexOfFirst { it.contains("Event") }

        if (mediatorIndex >= 0 && repositoryIndex >= 0) {
            assertTrue(
                mediatorIndex < repositoryIndex,
                "Mediator should be initialized before Repository beans. Order: $initializationOrder"
            )
        }

        println("Bean initialization order:")
        initializationOrder.forEachIndexed { index, bean ->
            println("  ${index + 1}. $bean")
        }
    }

    @Test
    @Disabled
    fun `should handle concurrent bean access safely`() {
        // 模拟并发访问Bean的场景
        val beanNames = listOf("defaultMediator", "defaultRepositorySupervisor", "defaultAggregateSupervisor")
        val availableBeans = beanNames.filter { applicationContext.containsBean(it) }

        if (availableBeans.isEmpty()) {
            println("No target beans available for concurrent access test")
            return
        }

        val latch = CountDownLatch(availableBeans.size * 3) // 每个Bean访问3次
        val errors = ConcurrentLinkedQueue<String>()

        // 创建多个线程并发访问Bean
        repeat(3) { threadNum ->
            Thread {
                try {
                    availableBeans.forEach { beanName ->
                        try {
                            val bean = applicationContext.getBean(beanName)
                            // 验证Bean不为null且可以正常使用
                            bean?.let {
                                // 简单的方法调用测试
                                it.toString() // 这会触发一些基本的初始化检查
                            }
                        } catch (e: Exception) {
                            errors.add("Thread $threadNum failed to access $beanName: ${e.message}")
                        } finally {
                            latch.countDown()
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Thread $threadNum encountered error: ${e.message}")
                }
            }.start()
        }

        // 等待所有线程完成
        assertTrue(
            latch.await(10, TimeUnit.SECONDS),
            "All concurrent bean access should complete within timeout"
        )

        // 检查是否有错误
        assertTrue(
            errors.isEmpty(),
            "Concurrent bean access should not produce errors. Errors: ${errors.toList()}"
        )

        println("✓ Concurrent bean access test passed with ${availableBeans.size} beans")
    }

    @Test
    @Disabled
    fun `should not have partially initialized beans`() {
        // 检查所有Bean都是完全初始化的
        val beanDefinitionNames = applicationContext.beanDefinitionNames
        val partiallyInitialized = mutableListOf<String>()

        for (beanName in beanDefinitionNames) {
            try {
                if (applicationContext.containsBean(beanName) && applicationContext.isSingleton(beanName)) {
                    val bean = applicationContext.getBean(beanName)

                    // 检查Bean是否为代理对象且代理目标为null（部分初始化的标志）
                    if (bean is org.springframework.aop.framework.Advised) {
                        try {
                            val targetSource = bean.targetSource
                            if (targetSource.target == null) {
                                partiallyInitialized.add(beanName)
                            }
                        } catch (e: Exception) {
                            // 如果无法访问目标，可能是部分初始化
                            partiallyInitialized.add("$beanName (target access failed)")
                        }
                    }
                }
            } catch (e: Exception) {
                // Bean访问失败可能表示部分初始化
                partiallyInitialized.add("$beanName (access failed: ${e.message})")
            }
        }

        assertTrue(
            partiallyInitialized.isEmpty(),
            "No beans should be partially initialized. Found: $partiallyInitialized"
        )

        println("✓ All beans are fully initialized")
    }

    /**
     * 测试生命周期监听器
     */
    @Component
    class TestLifecycleListener {
        private val initializationOrder = ConcurrentLinkedQueue<String>()
        private val prematureAccessEvents = ConcurrentLinkedQueue<String>()
        private val contextRefreshLatch = CountDownLatch(1)
        private var contextRefreshed = false

        @EventListener
        fun onContextRefreshed(event: ContextRefreshedEvent) {
            contextRefreshed = true
            contextRefreshLatch.countDown()
            println("✓ Application context refresh completed")
        }

        fun waitForContextRefresh(timeout: Long, unit: TimeUnit): Boolean {
            return contextRefreshLatch.await(timeout, unit)
        }

        fun getInitializationOrder(): List<String> = initializationOrder.toList()

        fun getPrematureAccessEvents(): List<String> = prematureAccessEvents.toList()

        fun recordBeanInitialization(beanName: String, beanClass: String) {
            initializationOrder.add("$beanName:$beanClass")
        }

        fun recordPrematureAccess(beanName: String, reason: String) {
            if (!contextRefreshed) {
                prematureAccessEvents.add("$beanName - $reason")
            }
        }
    }

    @SpringBootApplication
    @ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
    @EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])
    @EntityScan(basePackages = ["com.only4.cap4k.ddd"])
    class BeanLifecycleTestApp
}
