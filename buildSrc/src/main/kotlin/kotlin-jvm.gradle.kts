// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.time.Duration

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
    // Apply the Kotlin Spring plugin for better Spring framework integration.
    kotlin("plugin.spring")
    // Apply the Kotlin JPA plugin for JPA entity class generation.
    kotlin("plugin.jpa")
}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // 增加测试超时时间（10分钟）
    timeout.set(Duration.ofMinutes(10))

    // 配置JVM内存和参数
    jvmArgs(
        "-Xmx2g",           // 最大堆内存2GB
        "-Xms512m",         // 初始堆内存512MB
        "-XX:MaxMetaspaceSize=512m"  // 元空间最大512MB
    )

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
