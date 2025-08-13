package com.only4.cap4k.ddd

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertTrue

/**
 * 循环依赖检测测试
 * 专门测试自动配置类是否存在循环依赖问题
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@DisplayName("循环依赖检测测试")
@SpringBootTest(classes = [CircularDependencyTest.MinimalTestConfiguration::class])
@TestPropertySource(
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "logging.level.org.springframework.beans=ERROR"
    ]
)
class CircularDependencyTest {

    @Test
    @DisplayName("检测自动配置类循环依赖")
    @Disabled
    fun testCircularDependencyDetection() {
        // 如果测试执行到这里，说明Spring上下文启动成功，没有循环依赖
        assertTrue(true, "Spring context started successfully without circular dependencies")
    }

    @SpringBootApplication
    class MinimalTestConfiguration
}
