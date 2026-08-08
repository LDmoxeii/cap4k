plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    kapt(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.configuration.processor)

    api(project(":cap4k-ddd-jpa-starter"))
    api(project(":ddd-domain-event-jpa"))

    implementation(libs.slf4j)
    implementation(libs.spring.messaging)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(project(":cap4k-ddd-integration-event-http-starter"))
    testImplementation(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
