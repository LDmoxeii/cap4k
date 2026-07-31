plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    kapt(platform(libs.spring.boot.dependencies))
    kapt(libs.spring.configuration.processor)

    api(project(":ddd-core"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.tx)
    implementation("com.github.f4b6a3:uuid-creator:6.1.1")

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
