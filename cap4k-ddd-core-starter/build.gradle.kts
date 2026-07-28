plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
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
