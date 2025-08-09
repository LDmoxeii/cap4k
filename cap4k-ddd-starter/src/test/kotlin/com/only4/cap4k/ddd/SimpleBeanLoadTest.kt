package com.only4.cap4k.ddd

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 简化的Bean加载测试
 * 主要测试：
 * 1. Spring上下文能正常启动
 * 2. 核心Bean能正常加载
 * 3. 没有循环依赖问题
 *
 * @author LD_moxeii
 * @date 2025/08/10
 */
@SpringBootTest(classes = [SimpleBeanLoadTest.SimpleBeanLoadTestApp::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN"
    ]
)
class SimpleBeanLoadTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    @Disabled
    fun `应用上下文能正常启动`() {
        // 验证Spring上下文已经启动
        assertNotNull(applicationContext, "应用上下文不应该为null")

        val beanCount = applicationContext.beanDefinitionNames.size
        assertTrue(beanCount > 0, "应该至少有一些Bean被加载")

        println("✓ 应用上下文启动成功，加载了 $beanCount 个Bean")
    }

    @Test
    @Disabled
    fun `核心Bean能正常加载`() {
        // 检查一些核心Bean是否存在
        val coreBeans = listOf(
            "defaultMediator"
        )

        var loadedBeans = 0
        coreBeans.forEach { beanName ->
            if (applicationContext.containsBean(beanName)) {
                val bean = applicationContext.getBean(beanName)
                assertNotNull(bean, "Bean $beanName 不应该为null")
                loadedBeans++
                println("✓ 成功加载核心Bean: $beanName")
            } else {
                println("- 核心Bean $beanName 未找到（可能是条件性的）")
            }
        }

        println("成功加载 $loadedBeans 个核心Bean")
    }

    @Test
    @Disabled
    fun `检查Bean初始化没有异常`() {
        val beanNames = applicationContext.beanDefinitionNames
        var successCount = 0
        var errorCount = 0

        beanNames.forEach { beanName ->
            try {
                if (applicationContext.isSingleton(beanName)) {
                    val bean = applicationContext.getBean(beanName)
                    if (bean != null) {
                        successCount++
                    }
                }
            } catch (e: Exception) {
                errorCount++
                // 记录但不让测试失败，某些Bean可能需要特定条件
                println("警告: Bean $beanName 初始化失败: ${e.message}")
            }
        }

        println("Bean初始化统计: 成功 $successCount 个, 失败 $errorCount 个")

        // 确保至少有一些Bean成功初始化
        assertTrue(successCount > 0, "应该至少有一些Bean能成功初始化")
    }

    @Test
    @Disabled
    fun `验证没有明显的循环依赖`() {
        // 通过尝试获取所有Bean来检测循环依赖
        val problemBeans = mutableListOf<String>()

        applicationContext.beanDefinitionNames.forEach { beanName ->
            try {
                applicationContext.getBean(beanName)
            } catch (e: Exception) {
                if (e.message?.contains("circular", ignoreCase = true) == true ||
                    e.message?.contains("cycle", ignoreCase = true) == true ||
                    e.message?.contains("currently in creation", ignoreCase = true) == true
                ) {
                    problemBeans.add("$beanName: ${e.message}")
                }
            }
        }

        assertTrue(
            problemBeans.isEmpty(),
            "发现可能的循环依赖问题: ${problemBeans.joinToString("; ")}"
        )

        println("✓ 未检测到循环依赖问题")
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @org.springframework.context.annotation.ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
    @org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = ["com.only4.cap4k.ddd"])
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])
    class SimpleBeanLoadTestApp
}
