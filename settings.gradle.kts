// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            credentials {
                username = providers.gradleProperty("aliyun.maven.username").orNull ?: "defaultUsername"
                password = providers.gradleProperty("aliyun.maven.password").orNull ?: "defaultPassword"
            }
            url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/cap4k")
        }
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include("ddd-core")
include("ddd-distributed-saga-jpa")
include("ddd-application-request-jpa")
include("ddd-distributed-locker-jdbc", "ddd-distributed-snowflake")
include("ddd-domain-event-jpa", "ddd-domain-repo-jpa", "ddd-domain-repo-jpa-querydsl")
include(
    "ddd-integration-event-http",
    "ddd-integration-event-http-jpa",
    "ddd-integration-event-rabbitmq",
    "ddd-integration-event-rocketmq"
)
include("cap4k-ddd-console", "cap4k-ddd-starter")
include("cap4k-plugin-code-analysis-core")
include("cap4k-plugin-code-analysis-compiler")
include("cap4k-plugin-code-analysis-flow-export")
include("cap4k-plugin-codegen-ksp")
include("cap4k-plugin-codegen")

rootProject.name = "cap4k"
