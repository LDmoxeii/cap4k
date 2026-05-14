// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
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
include(
    "cap4k-plugin-pipeline-api",
    "cap4k-plugin-pipeline-core",
    "cap4k-plugin-pipeline-bootstrap",
    "cap4k-plugin-pipeline-renderer-api",
    "cap4k-plugin-pipeline-renderer-pebble",
    "cap4k-plugin-pipeline-source-design-json",
    "cap4k-plugin-pipeline-source-db",
    "cap4k-plugin-pipeline-source-enum-manifest",
    "cap4k-plugin-pipeline-source-ksp-metadata",
    "cap4k-plugin-pipeline-source-ir-analysis",
    "cap4k-plugin-pipeline-generator-design",
    "cap4k-plugin-pipeline-generator-aggregate",
    "cap4k-plugin-pipeline-generator-flow",
    "cap4k-plugin-pipeline-generator-drawing-board",
    "cap4k-plugin-pipeline-gradle"
)

rootProject.name = "cap4k"
