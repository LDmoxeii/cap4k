plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":cap4k-ddd-core-starter"))
    api(project(":ddd-integration-event-rocketmq"))
    api(libs.rocketmq)

    implementation(libs.slf4j)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
