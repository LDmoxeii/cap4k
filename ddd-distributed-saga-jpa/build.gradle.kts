plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}
dependencies {
    implementation(project(":ddd-core"))

    implementation(libs.fastjson)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    compileOnly(libs.jpa)
    compileOnly(libs.springMassaging)

    // Test dependencies
    testImplementation(libs.jpa)
    testImplementation(libs.springMassaging)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.mockk:mockk:1.13.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
