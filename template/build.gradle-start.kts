
plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.spring.boot)
}

dependencies {
    api(libs.spring.boot.starter)
    api(libs.spring.actuator)
    api(libs.spring.configuration.processor)
    api(libs.cap4k.console)
    api(project(":${artifactId}-adapter"))
    api(project(":${artifactId}-application"))
    api(project(":${artifactId}-domain"))
}
