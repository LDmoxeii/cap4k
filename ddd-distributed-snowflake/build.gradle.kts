plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    api(platform(libs.springBootDependencies))
    compileOnly(libs.springJdbc)
    compileOnly(libs.hibernateCore)

    compileOnly(libs.slf4j)

    // Add mockk test framework
    testImplementation(platform(libs.springBootDependencies))
    testImplementation(libs.springJdbc)
    testImplementation(libs.hibernateCore)
    testImplementation(libs.slf4j)

    testImplementation(libs.mockk)
    testImplementation(libs.mockkAgentJvm)

    // Add JUnit and Kotlin Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
