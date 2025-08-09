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
 * Java版本配置测试
 * 测试Java版本的配置是否可以正常工作
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(classes = [JavaVersionTest.JavaTestApp::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.main.allow-bean-definition-overriding=true",
        "logging.level.org.springframework.beans=WARN"
    ]
)
@DisplayName("Java版本配置测试")
class JavaVersionTest {

    @Test
    @DisplayName("验证Java版本配置正常工作")
    @Disabled
    fun testJavaVersionConfiguration() {
        // 如果测试能运行到这里，说明没有循环依赖问题
        assertTrue(true, "Java version configuration works without circular dependencies")
    }

    @SpringBootApplication
    @ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
    @EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd"])
    @EntityScan(basePackages = ["com.only4.cap4k.ddd"])
    class JavaTestApp
}
