plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    kapt(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.configuration.processor)

    api(project(":cap4k-ddd-core-starter"))
    api(project(":ddd-integration-event-http"))
    api(libs.spring.tomcat)
    api(libs.spring.web.mvc)

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.messaging)
    implementation(libs.slf4j)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
