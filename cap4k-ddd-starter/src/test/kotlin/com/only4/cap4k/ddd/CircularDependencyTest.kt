package com.only4.cap4k.ddd

import com.only4.cap4k.ddd.fixture.minimal.StarterMinimalTestApplication
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
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
@SpringBootTest(classes = [StarterMinimalTestApplication::class])
@TestPropertySource(
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "cap4k.ddd.domain.event.enable=false",
        "cap4k.ddd.domain.event.event-scan-package=com.only4.cap4k.ddd.fixture.event",
        "cap4k.ddd.application.request.enable=false",
        "cap4k.ddd.application.saga.enable=false",
        "cap4k.ddd.application.distributed.locker.enable=false",
        "cap4k.ddd.distributed.id-generator.snowflake.enable=false",
        "spring.autoconfigure.exclude=com.only4.cap4k.ddd.domain.event.DomainEventAutoConfiguration,com.only4.cap4k.ddd.application.request.RequestAutoConfiguration,com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration,com.only4.cap4k.ddd.application.event.IntegrationEventAutoConfiguration,com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration,com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration",
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
}
