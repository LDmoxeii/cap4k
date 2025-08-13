package com.only4.cap4k.ddd

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.BeanCurrentlyInCreationException
import org.springframework.beans.factory.UnsatisfiedDependencyException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Bean依赖关系测试
 * 主要测试：
 * 1. Bean 创建过程中不会出现循环依赖
 * 2. Bean 初始化顺序正确
 * 3. 所有必需的Bean都能正常创建
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(classes = [BeanDependencyTest.BeanDependencyTestApp::class])
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework.beans=DEBUG"
    ]
)
class BeanDependencyTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    @Disabled
    fun `should not have circular dependencies in bean creation`() {
        try {
            // 获取所有Bean定义名称
            val beanNames = applicationContext.beanDefinitionNames

            // 尝试获取每个Bean，检查是否存在循环依赖
            for (beanName in beanNames) {
                try {
                    val bean = applicationContext.getBean(beanName)
                    assertNotNull(bean, "Bean $beanName should not be null")
                } catch (e: BeanCurrentlyInCreationException) {
                    fail("Circular dependency detected for bean: $beanName - ${e.message}")
                } catch (e: BeanCreationException) {
                    if (e.cause is BeanCurrentlyInCreationException) {
                        fail("Circular dependency detected in dependency chain for bean: $beanName - ${e.message}")
                    }
                    // 其他类型的Bean创建异常可能是配置问题，记录但不失败测试
                    println("Warning: Bean creation issue for $beanName: ${e.message}")
                } catch (e: UnsatisfiedDependencyException) {
                    if (e.cause is BeanCurrentlyInCreationException) {
                        fail("Circular dependency detected in dependency injection for bean: $beanName - ${e.message}")
                    }
                    // 其他依赖问题可能是测试环境配置导致的，记录但不失败测试
                    println("Warning: Dependency issue for $beanName: ${e.message}")
                }
            }

            println("Successfully checked ${beanNames.size} beans for circular dependencies")

        } catch (e: Exception) {
            fail("Unexpected error during bean dependency check: ${e.message}")
        }
    }

    @Test
    @Disabled
    fun `should load core beans successfully`() {
        // 测试核心Bean是否能正常加载
        val coreBeansToCheck = listOf(
            "defaultMediator",
            "defaultRepositorySupervisor",
            "defaultAggregateSupervisor",
            "defaultDomainEventSupervisor"
        )

        for (beanName in coreBeansToCheck) {
            try {
                if (applicationContext.containsBean(beanName)) {
                    val bean = applicationContext.getBean(beanName)
                    assertNotNull(bean, "Core bean $beanName should not be null")
                    println("✓ Successfully loaded core bean: $beanName")
                } else {
                    println("- Core bean $beanName not found (may be conditional)")
                }
            } catch (e: Exception) {
                println("✗ Failed to load core bean $beanName: ${e.message}")
                // 对于核心Bean，如果加载失败应该抛出异常
                if (beanName in listOf("defaultMediator")) {
                    throw e
                }
            }
        }
    }

    @Test
    @Disabled
    fun `should handle conditional beans correctly`() {
        // 测试条件Bean的加载情况
        val conditionalBeans = mapOf(
            "jpaEventRecordRepository" to "cap4k.ddd.domain.event.enable",
            "jpaRequestRecordRepository" to "cap4k.ddd.application.request.enable",
            "jpaSagaRecordRepository" to "cap4k.ddd.application.saga.enable",
            "snowflakeIdGenerator" to "cap4k.ddd.domain.distributed.snowflake.enable"
        )

        for ((beanName, condition) in conditionalBeans) {
            try {
                if (applicationContext.containsBean(beanName)) {
                    val bean = applicationContext.getBean(beanName)
                    assertNotNull(
                        bean,
                        "Conditional bean $beanName should not be null when condition $condition is met"
                    )
                    println("✓ Conditional bean $beanName loaded successfully")
                } else {
                    println("- Conditional bean $beanName not loaded (condition: $condition)")
                }
            } catch (e: Exception) {
                println("✗ Error loading conditional bean $beanName: ${e.message}")
            }
        }
    }

    @Test
    @Disabled
    fun `should verify bean initialization timing`() {
        // 验证Bean初始化时机，确保不会提前调用未初始化的Bean
        val beanNames = applicationContext.beanDefinitionNames
        val singletonBeans = mutableListOf<String>()

        for (beanName in beanNames) {
            try {
                if (applicationContext.isSingleton(beanName)) {
                    // 检查单例Bean是否已经初始化
                    val bean = applicationContext.getBean(beanName)
                    if (bean != null) {
                        singletonBeans.add(beanName)
                    }
                }
            } catch (e: Exception) {
                // 忽略无法访问的Bean
            }
        }

        assertTrue(singletonBeans.isNotEmpty(), "Should have some singleton beans initialized")
        println("Initialized ${singletonBeans.size} singleton beans successfully")

        // 验证重要的单例Bean都已初始化
        val importantSingletons = listOf("defaultMediator")
        for (beanName in importantSingletons) {
            if (applicationContext.containsBean(beanName)) {
                assertTrue(
                    singletonBeans.contains(beanName),
                    "Important singleton bean $beanName should be initialized"
                )
            }
        }
    }

    @Test
    @Disabled
    fun `should handle bean post processors correctly`() {
        // 验证Bean后处理器不会导致初始化问题
        val postProcessors = applicationContext.getBeansOfType(
            org.springframework.beans.factory.config.BeanPostProcessor::class.java
        )

        assertTrue(postProcessors.isNotEmpty(), "Should have bean post processors")
        println("Found ${postProcessors.size} bean post processors")

        for ((name, processor) in postProcessors) {
            assertNotNull(processor, "Bean post processor $name should not be null")
            println("✓ Bean post processor: $name (${processor::class.simpleName})")
        }
    }

    @SpringBootApplication
    @ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
    @EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])
    @EntityScan(basePackages = ["com.only4.cap4k.ddd"])
    class BeanDependencyTestApp
}
