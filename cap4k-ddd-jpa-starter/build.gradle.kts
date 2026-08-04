plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    kapt(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.configuration.processor)

    api(project(":cap4k-ddd-core-starter"))
    api(project(":ddd-domain-repo-jpa"))
    api(libs.jpa)

    implementation(kotlin("reflect"))
    implementation(libs.slf4j)

    compileOnly(libs.spring.web.mvc)
    compileOnly(libs.spring.tomcat)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.spring.web.mvc)
    testImplementation(libs.spring.tomcat)
    testImplementation(libs.h2)
    testImplementation(libs.jackson.module.kotlin)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
