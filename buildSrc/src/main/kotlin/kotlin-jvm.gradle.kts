// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.time.Duration

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
}

group = "com.only4"
version = "0.3.9-SNAPSHOT"

// 添加源码 jar 任务
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "AliYunMaven"
            url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/cap4k")
            credentials {
                username = providers.gradleProperty("aliyun.maven.username").orNull ?: "defaultUsername"
                password = providers.gradleProperty("aliyun.maven.password").orNull ?: "defaultPassword"
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    enabled = false
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
