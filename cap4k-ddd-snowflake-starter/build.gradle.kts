plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    kapt(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.configuration.processor)

    api(project(":cap4k-ddd-core-starter"))
    api(project(":ddd-distributed-snowflake"))
    api(libs.spring.jdbc)

    implementation(libs.spring.boot.starter)
    implementation(libs.slf4j)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
