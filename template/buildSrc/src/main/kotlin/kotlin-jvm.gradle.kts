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

group = "${groupId}"
version = "${version}"

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(10))
    jvmArgs(
        "-Xmx2g",
        "-Xms512m",
        "-XX:MaxMetaspaceSize=512m",
    )
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
