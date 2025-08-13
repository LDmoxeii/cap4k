package com.only4.cap4k.ddd

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertTrue

/**
 * 基础自动配置测试
 * 验证最基本的配置能够正常工作
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootTest(classes = [BasicAutoConfigurationTest.BasicTestApplication::class])
@TestPropertySource(
    properties = [
        "cap4k.application.name=test-app",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework.beans=WARN",
        "logging.level.com.only4.cap4k.ddd=WARN"
    ]
)
@DisplayName("基础自动配置测试")
class BasicAutoConfigurationTest {

    @Test
    @DisplayName("验证基础配置正常工作")
    @Disabled
    fun testBasicConfigurationWorks() {
        // 如果测试能运行到这里，说明基础配置没有问题
        assertTrue(true, "Basic configuration works")
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication
    class BasicTestApplication
}
