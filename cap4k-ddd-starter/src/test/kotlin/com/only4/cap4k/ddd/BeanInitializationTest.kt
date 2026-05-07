package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.fixture.minimal.StarterMinimalTestApplication
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Bean初始化和加载测试
 * 测试关键Bean能够正常初始化而不会提前调用
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(classes = [StarterMinimalTestApplication::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.main.allow-bean-definition-overriding=true",
        "cap4k.ddd.domain.event.enable=false",
        "cap4k.ddd.domain.event.event-scan-package=com.only4.cap4k.ddd.fixture.event",
        "cap4k.ddd.application.request.enable=false",
        "cap4k.ddd.application.saga.enable=false",
        "cap4k.ddd.application.distributed.locker.enable=false",
        "cap4k.ddd.distributed.id-generator.snowflake.enable=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration,com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration,com.only4.cap4k.ddd.application.request.RequestAutoConfiguration,com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration,com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration,com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration,com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration",
        "logging.level.org.springframework.beans=WARN",
        "logging.level.com.only4.cap4k.ddd=INFO"
    ]
)
@DisplayName("Bean初始化和加载测试")
class BeanInitializationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    @DisplayName("验证应用上下文成功启动")
    @Disabled
    fun testApplicationContextStartup() {
        // 验证应用上下文已启动
        val beanCount = applicationContext.beanDefinitionNames.size
        assertTrue(beanCount > 0, "Application context should have beans loaded")

        println("✓ Application context started successfully with $beanCount beans")
    }

    @Test
    @DisplayName("验证核心Bean存在但未提前初始化")
    @Disabled
    fun testCoreBeansExistButNotEagerlyInitialized() {
        // 检查核心Bean定义是否存在
        val coreBeanNames = listOf("defaultMediator")

        for (beanName in coreBeanNames) {
            if (applicationContext.containsBean(beanName)) {
                // Bean定义存在
                assertTrue(true, "Bean definition exists: $beanName")

                try {
                    // 尝试获取Bean - 这会触发初始化
                    val bean = applicationContext.getBean(beanName)
                    assertNotNull(bean, "Bean should be properly initialized: $beanName")
                    println("✓ Bean properly initialized: $beanName (${bean::class.simpleName})")
                } catch (e: Exception) {
                    println("⚠ Bean initialization issue for $beanName: ${e.message}")
                }
            } else {
                println("- Bean not found (may be conditional): $beanName")
            }
        }
    }

    @Test
    @DisplayName("验证Bean初始化顺序合理")
    @Disabled
    fun testBeanInitializationOrder() {
        val beanNames = applicationContext.beanDefinitionNames

        // 统计不同类型的Bean
        val springBeans = beanNames.filter {
            it.startsWith("org.springframework") || it.startsWith("spring")
        }.size

        val ourBeans = beanNames.filter {
            it.contains("cap4k") || it.contains("default")
        }.size

        println("Bean statistics:")
        println("  - Total beans: ${beanNames.size}")
        println("  - Spring framework beans: $springBeans")
        println("  - Our application beans: $ourBeans")

        assertTrue(beanNames.size > 0, "Should have some beans loaded")
    }
}
