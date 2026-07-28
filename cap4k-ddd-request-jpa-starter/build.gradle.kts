plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    kapt(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.configuration.processor)

    api(project(":cap4k-ddd-jpa-starter"))
    api(project(":ddd-application-request-jpa"))

    implementation(libs.slf4j)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
