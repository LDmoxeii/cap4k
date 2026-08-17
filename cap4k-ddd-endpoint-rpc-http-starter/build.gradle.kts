plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}
dependencies {
    kapt(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.configuration.processor)
    api(project(":cap4k-ddd-core-starter"))
    api(project(":ddd-endpoint-rpc-http"))
    api(libs.spring.web.mvc)
    api(libs.spring.tomcat)
    implementation(libs.spring.boot.starter)
    testImplementation(libs.spring.boot.starter.test) { exclude(group = "org.junit.vintage", module = "junit-vintage-engine") }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("cap4k.test.runtime.classpath", sourceSets.test.get().runtimeClasspath.asPath)
}
