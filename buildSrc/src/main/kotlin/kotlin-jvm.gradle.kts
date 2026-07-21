// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
package buildsrc.convention

import org.gradle.api.GradleException
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.time.Duration

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
}

group = "com.only4"
version = "0.6.1-SNAPSHOT"

java {
    withSourcesJar()
}

afterEvaluate {
    if (!pluginManager.hasPlugin("java-gradle-plugin")) {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()
                }
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "AliYunMaven"
            url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/cap4k")
            credentials {
                username = providers.gradleProperty("aliyun.maven.username").orNull
                    ?: System.getenv("ALIYUN_MAVEN_USERNAME")
                password = providers.gradleProperty("aliyun.maven.password").orNull
                    ?: System.getenv("ALIYUN_MAVEN_PASSWORD")
            }
        }
    }
}

val missingAliyunCredentialsMessage =
    "Aliyun Maven publish requires credentials. " +
        "Set aliyun.maven.username/aliyun.maven.password Gradle properties " +
        "or ALIYUN_MAVEN_USERNAME/ALIYUN_MAVEN_PASSWORD environment variables."

val aliyunPublishRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName == "publish" ||
        taskName.endsWith(":publish") ||
        taskName.contains("ToAliYunMavenRepository")
}

if (aliyunPublishRequested) {
    val username = providers.gradleProperty("aliyun.maven.username")
        .orElse(providers.environmentVariable("ALIYUN_MAVEN_USERNAME"))
        .orNull
    val password = providers.gradleProperty("aliyun.maven.password")
        .orElse(providers.environmentVariable("ALIYUN_MAVEN_PASSWORD"))
        .orNull

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        throw GradleException(missingAliyunCredentialsMessage)
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    enabled = true
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
