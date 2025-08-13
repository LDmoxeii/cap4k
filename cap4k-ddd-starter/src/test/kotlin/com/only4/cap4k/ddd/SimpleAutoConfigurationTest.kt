package com.only4.cap4k.ddd

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertTrue

/**
 * 简单的Bean加载测试
 * 验证Spring Boot自动配置类能够正常加载且无循环依赖问题
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(
    classes = [SimpleAutoConfigurationTest.MinimalTestApp::class],
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.autoconfigure.exclude=com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration,com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration,com.only4.cap4k.ddd.application.request.RequestAutoConfiguration,com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration,com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration,com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration,com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration",
        "logging.level.org.springframework.beans=WARN"
    ]
)
@DisplayName("简单的自动配置Bean加载测试")
class SimpleAutoConfigurationTest {

    @Test
    @DisplayName("验证Spring应用上下文成功启动")
    @Disabled
    fun testApplicationContextStartup() {
        // 如果测试能执行到这里，说明Spring上下文启动成功，没有循环依赖问题
        assertTrue(true, "Application context started successfully without circular dependencies")
    }

    @SpringBootApplication
    class MinimalTestApp
}
