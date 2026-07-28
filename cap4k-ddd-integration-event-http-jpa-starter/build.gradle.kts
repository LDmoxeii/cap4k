plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":cap4k-ddd-integration-event-http-starter"))
    api(project(":cap4k-ddd-jpa-starter"))
    api(project(":ddd-integration-event-http-jpa"))
    api(libs.jpa)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
